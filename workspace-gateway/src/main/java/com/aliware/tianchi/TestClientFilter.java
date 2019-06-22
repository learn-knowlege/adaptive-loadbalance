package com.aliware.tianchi;

import com.aliware.tianchi.common.metric.SnapshotStats;
import com.aliware.tianchi.lb.metric.LBStatistics;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

import static com.aliware.tianchi.common.util.ObjectUtil.nonEmpty;

/**
 * @author daofeng.xjf
 * <p>
 * 客户端过滤器
 * 可选接口
 * 用户可以在客户端拦截请求和响应,捕获 rpc 调用时产生、服务端返回的已知异常。
 */
@Activate(group = Constants.CONSUMER)
public class TestClientFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        LBStatistics.INSTANCE.queue(invoker);
        return invoker.invoke(invocation);
    }

    @Override
    public Result onResponse(Result result, Invoker<?> invoker, Invocation invocation) {
        LBStatistics.INSTANCE.dequeue(invoker);

        String statsText = result.getAttachment("STATS");
        if (nonEmpty(statsText)) {
            SnapshotStats snapshotStats = SnapshotStats.fromString(statsText);
            LBStatistics.INSTANCE.updateInstanceStats(invoker, invocation, snapshotStats);

        }
        return result;
    }
}
    