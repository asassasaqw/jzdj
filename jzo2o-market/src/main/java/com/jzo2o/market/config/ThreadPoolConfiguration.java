package com.jzo2o.market.config;

import com.jzo2o.redis.properties.RedisSyncProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 自定义同步秒杀队列所需要的线程池
 */
@Configuration
public class ThreadPoolConfiguration {

    @Bean("syncThreadPool")
    public ThreadPoolExecutor synchronizeThreadPool(RedisSyncProperties redisSyncProperties) {
        // 核心线程数
        int corePoolSize = 1;
        // 最大线程数
        int maxPoolSize = redisSyncProperties.getQueueNum();//10
        // 临时线程空闲时间
        long keepAliveTime = 120;
        // 临时线程空闲时间单位
        TimeUnit unit = TimeUnit.SECONDS;
        // 指定拒绝策略为 DiscardPolicy 当任务被拒绝时，会默默地丢弃被拒绝的任务，不会抛出异常也不会执行被拒绝的任务
        RejectedExecutionHandler rejectedHandler = new ThreadPoolExecutor.DiscardPolicy();
        // 任务阻塞队列，使用SynchronousQueue，在没有线程去消费时不会保存任务
        SynchronousQueue queue = new SynchronousQueue();
        // 创建线程池返回
        return new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, queue, rejectedHandler);
    }
}