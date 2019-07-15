package com.aliware.tianchi.lb;

import com.aliware.tianchi.common.conf.Configuration;
import com.aliware.tianchi.common.metric.SnapshotStats;
import com.aliware.tianchi.common.util.DubboUtil;
import com.aliware.tianchi.common.util.SmallPriorityQueue;
import com.aliware.tianchi.lb.metric.LBStatistics;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.LoadBalance;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.aliware.tianchi.common.util.ObjectUtil.*;

/**
 * @author yangxf
 */
public class AdaptiveLoadBalance implements LoadBalance {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveLoadBalance.class);

    private static final int HEAP_THRESHOLD = 8;

    private final long start = System.nanoTime();

    private final Configuration conf;

    private final Comparator<SnapshotStats> comparator;
    private final Comparator<SnapshotStats> idleComparator;
    
    private final ThreadLocal<Queue<SnapshotStats>> localQueue;
    private final ThreadLocal<Queue<SnapshotStats>> localIdleQueue;

    public AdaptiveLoadBalance(Configuration conf) {
        checkNotNull(conf, "conf");
        this.conf = conf;
        comparator = Comparator.comparingDouble(SnapshotStats::getAvgRTMs);
        idleComparator = Comparator.comparingLong(SnapshotStats::tokens);
        localQueue = ThreadLocal.withInitial(() -> new SmallPriorityQueue<>(8, comparator));
        localIdleQueue = ThreadLocal.withInitial(() -> new SmallPriorityQueue<>(8, idleComparator));
    }

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        int size = invokers.size();

        LBStatistics lbStatistics = LBStatistics.INSTANCE;
        Map<String, Invoker<T>> mapping = new HashMap<>();

        // Queue<SnapshotStats> queue = size > HEAP_THRESHOLD ?
        //         new PriorityQueue<>(comparator) :
        //         new SmallPriorityQueue<>(HEAP_THRESHOLD, comparator);

        Queue<SnapshotStats> queue = localQueue.get();

        String serviceId = DubboUtil.getServiceId(invokers.get(0), invocation);

        Map<String, SnapshotStats> instanceStatsMap = lbStatistics.getInstanceStatsMap(serviceId);
        if (isNull(instanceStatsMap)) {
            return invokers.get(ThreadLocalRandom.current().nextInt(size));
        }

        List<SnapshotStats> statsList = new ArrayList<>(size);
        for (Invoker<T> invoker : invokers) {
            String address = DubboUtil.getIpAddress(invoker);
            SnapshotStats stats = instanceStatsMap.get(address);
            if (isNull(stats)) {
                return invokers.get(ThreadLocalRandom.current().nextInt(size));
            }
            mapping.put(address, invoker);
            // todo:
            queue.offer(stats);
            statsList.add(stats);
        }

        Queue<SnapshotStats> idleQueue = null;
        for (int mask = 0x00000001; ; ) {
            SnapshotStats stats = queue.poll();
            if (isNull(stats)) {
                break;
            }

            if (stats.acquireToken()) {

                if ((ThreadLocalRandom.current().nextInt() & mask) == 0) {
                    stats.releaseToken();

                    if (isNull(idleQueue)) {
                        // idleQueue = size > HEAP_THRESHOLD ?
                        //         new PriorityQueue<>(idleComparator) :
                        //         new SmallPriorityQueue<>(HEAP_THRESHOLD, idleComparator);
                        idleQueue = localIdleQueue.get();
                    }

                    idleQueue.offer(stats);
                    mask = (mask << 1) | mask;
                    continue;
                }

                String address = stats.getAddress();
                invocation.getAttachments().put("CURRENT_STATS_EPOCH", stats.getEpoch() + "");
                queue.clear();
                return mapping.get(address);
            }
        }

        while (nonNull(idleQueue)) {
            SnapshotStats stats = idleQueue.poll();
            if (isNull(stats)) {
                break;
            }
            if (stats.acquireToken()) {
                invocation.getAttachments().put("CURRENT_STATS_EPOCH", stats.getEpoch() + "");
                idleQueue.clear();
                return mapping.get(stats.getAddress());
            }
        }
        
        // weighted random ?
        
        double total = 0d;
        double[] weights = new double[size];
        for (int i = 0; i < size; i++) {
            SnapshotStats stats = statsList.get(i);
            double weight = stats.getDomainThreads() - stats.getWeight();
            total += weight;
            weights[i] = total;
        }

        if (total == 0) {
            return invokers.get(ThreadLocalRandom.current().nextInt(size));
        }

        double r = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < size; i++) {
            if (r < weights[i]) {
                return invokers.get(i);
            }
        }

        return invokers.get(ThreadLocalRandom.current().nextInt(size));
        // logger.info("all providers are overloaded");
        // throw new RpcException(RpcException.BIZ_EXCEPTION, "all providers are overloaded");
    }
}
