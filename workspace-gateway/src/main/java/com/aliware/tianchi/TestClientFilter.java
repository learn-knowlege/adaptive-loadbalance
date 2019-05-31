package com.aliware.tianchi;

import com.aliware.tianchi.lb.metric.InstanceStats;
import com.aliware.tianchi.lb.metric.LBStatistics;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.remoting.exchange.ResponseCallback;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter;
import org.apache.dubbo.rpc.support.RpcUtils;

/**
 * @author daofeng.xjf
 * <p>
 * 客户端过滤器
 * 可选接口
 * 用户可以在客户端拦截请求和响应,捕获 rpc 调用时产生、服务端返回的已知异常。
 */
@Activate(group = Constants.CONSUMER)
public class TestClientFilter implements Filter {

    private LBStatistics lbStats = LBStatistics.STATS;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        boolean isAsync = RpcUtils.isAsync(invoker.getUrl(), invocation);
        if (isAsync) {
            return invokeAsync(invoker, invocation);
        } else {
            return invokeSync(invoker, invocation);
        }
    }

    private Result invokeAsync(Invoker<?> invoker, Invocation invocation) throws RpcException {
        InstanceStats stats = lbStats.getInstanceStats(invoker);
        long startMs = System.currentTimeMillis();
        Result result = invoker.invoke(invocation);
        FutureAdapter<Object> f = (FutureAdapter<Object>) RpcContext.getContext().getFuture();
        if (f != null) {
            f.whenComplete((x, y) -> {
                stats.success(System.currentTimeMillis() - startMs);
            }).exceptionally(t -> {
                stats.failure(System.currentTimeMillis() - startMs);
                // todo
                return null;
            });
        }
        return result;
    }

    private Result invokeSync(Invoker<?> invoker, Invocation invocation) throws RpcException {
        InstanceStats stats = lbStats.getInstanceStats(invoker);
        long startMs = System.currentTimeMillis();
        try {
            Result result = invoker.invoke(invocation);
            stats.success(System.currentTimeMillis() - startMs);
            return result;
        } catch (Exception e) {
            stats.failure(System.currentTimeMillis() - startMs);
            throw e;
        }
    }
}
    