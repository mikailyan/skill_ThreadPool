package com.example.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


public class LoggingThreadFactory implements ThreadFactory {
    private final AtomicInteger threadCount = new AtomicInteger(0);
    private final String poolName;

    public LoggingThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = poolName + "-worker-" + threadCount.incrementAndGet();
        System.out.println("[ThreadFactory] Creating new thread: " + threadName);
        Thread t = new Thread(r, threadName);
        return t;
    }
}
