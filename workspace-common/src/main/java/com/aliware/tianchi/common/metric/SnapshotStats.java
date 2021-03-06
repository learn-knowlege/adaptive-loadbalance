package com.aliware.tianchi.common.metric;

import com.aliware.tianchi.common.util.RuntimeInfo;

import java.io.Serializable;

import static com.aliware.tianchi.common.util.ObjectUtil.*;

/**
 * todo
 *
 * @author yangxf
 */
public abstract class SnapshotStats implements Serializable {
    private static final long serialVersionUID = 6032175431996366552L;

    private static final String SEPARATOR = "_";
    private static final String GROUP_SEPARATOR = "@";

    private volatile int weight;

    private volatile long epoch;

    public SnapshotStats() {
        this(0, 0);
    }

    public SnapshotStats(int weight, long epoch) {
        this.weight = weight;
        this.epoch = epoch;
    }

    public static SnapshotStats fromString(String text) {
        return fromString(null, text);
    }

    public static SnapshotStats fromString(String address, String text) {
        checkNotEmpty(text, "text");

        String[] groups = text.split(GROUP_SEPARATOR);
        if (groups.length != 3) {
            throwIllegalArg();
        }

        String serviceId = groups[0];
        String[] insts = groups[1].split(SEPARATOR);
        if (insts.length != 12) {
            throwIllegalArg();
        }
        String finalAddress = defaultIfEmpty(address, insts[0]);
        long startTimeMs = Long.parseLong(insts[1]);
        long intervalTimeMs = Long.parseLong(insts[2]);
        int threads = Integer.parseInt(insts[3]);
        int activeCount = Integer.parseInt(insts[4]);
        long successes = Long.parseLong(insts[5]);
        long failures = Long.parseLong(insts[6]);
        long rejections = Long.parseLong(insts[7]);
        double avgResponseMs = Double.parseDouble(insts[8]);
        long throughput = Long.parseLong(insts[9]);
        int weight = Integer.parseInt(insts[10]);
        long epoch = Long.parseLong(insts[11]);
        ServerStats serverStats = new ServerStats(finalAddress);
        RuntimeInfo runInfo = isEmpty(groups[2]) || groups[2].equals("null") ?
                null : RuntimeInfo.fromString(groups[2]);
        serverStats.setRuntimeInfo(runInfo);

        return new SnapshotStats(weight, epoch) {
            private static final long serialVersionUID = 6197862269143364929L;

            @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public long startTimeMs() {
                return startTimeMs;
            }

            @Override
            public long intervalTimeMs() {
                return intervalTimeMs;
            }

            @Override
            public int getDomainThreads() {
                return threads;
            }

            @Override
            public int getActiveCount() {
                return activeCount;
            }

            @Override
            public String getAddress() {
                return finalAddress;
            }

            @Override
            public double getAvgRTMs() {
                return avgResponseMs;
            }

            @Override
            public ServerStats getServerStats() {
                return serverStats;
            }

            @Override
            public long getThroughput() {
                return throughput;
            }

            @Override
            public long getNumberOfSuccesses() {
                return successes;
            }

            @Override
            public long getNumberOfFailures() {
                return failures;
            }

            @Override
            public long getNumberOfRejections() {
                return rejections;
            }
        };
    }

    @Override
    public final String toString() {
        return getServiceId() + GROUP_SEPARATOR
               + getAddress() + SEPARATOR
               + startTimeMs() + SEPARATOR
               + intervalTimeMs() + SEPARATOR
               + getDomainThreads() + SEPARATOR
               + getActiveCount() + SEPARATOR
               + getNumberOfSuccesses() + SEPARATOR
               + getNumberOfFailures() + SEPARATOR
               + getNumberOfRejections() + SEPARATOR
               + getAvgRTMs() + SEPARATOR
               + getThroughput() + SEPARATOR
               + getWeight() + SEPARATOR
               + getEpoch()
               + GROUP_SEPARATOR + getServerStats().getRuntimeInfo();
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public String getAddress() {
        throw new UnsupportedOperationException();
    }

    public String getServiceId() {
        throw new UnsupportedOperationException();
    }

    public long startTimeMs() {
        throw new UnsupportedOperationException();
    }

    public long intervalTimeMs() {
        throw new UnsupportedOperationException();
    }

    public int getDomainThreads() {
        throw new UnsupportedOperationException();
    }

    public int getActiveCount() {
        throw new UnsupportedOperationException();
    }

    public ServerStats getServerStats() {
        throw new UnsupportedOperationException();
    }

    public double getAvgRTMs() {
        throw new UnsupportedOperationException();
    }

    public long getThroughput() {
        throw new UnsupportedOperationException();
    }

    public long getNumberOfSuccesses() {
        throw new UnsupportedOperationException();
    }

    public long getNumberOfFailures() {
        throw new UnsupportedOperationException();
    }

    public long getNumberOfRejections() {
        throw new UnsupportedOperationException();
    }

    private static void throwIllegalArg() {
        throw new IllegalArgumentException("text format error, see SnapshotStats.toString()");
    }
}
