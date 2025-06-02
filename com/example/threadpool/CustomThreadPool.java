package com.example.threadpool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;


public class CustomThreadPool implements CustomExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTimeMillis;
    private final int queueCapacity;
    private final int minSpareThreads;

    private final BlockingQueue<Runnable> taskQueue;
    private final Set<Worker> workers = Collections.synchronizedSet(new HashSet<>());
    private final LoggingThreadFactory threadFactory;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final AtomicBoolean isShutdownNow = new AtomicBoolean(false);
    private final AtomicInteger idleCount = new AtomicInteger(0);

    private final RejectedExecutionHandler rejectionHandler;

    public CustomThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int queueCapacity,
            int minSpareThreads,
            RejectedExecutionHandler rejectionHandler,
            String poolName) {

        if (corePoolSize < 0 || maxPoolSize <= 0 || maxPoolSize < corePoolSize ||
                keepAliveTime < 0 || queueCapacity <= 0 || minSpareThreads < 0) {
            throw new IllegalArgumentException("Invalid pool parameters");
        }
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTimeMillis = timeUnit.toMillis(keepAliveTime);
        this.queueCapacity = queueCapacity;
        this.minSpareThreads = minSpareThreads;
        this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.threadFactory = new LoggingThreadFactory(poolName);
        this.rejectionHandler = rejectionHandler;

        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown.get()) {
            throw new RejectedExecutionException("Pool is shutdown");
        }

        boolean offered = taskQueue.offer(command);
        if (offered) {
            System.out.println("[Pool] Task accepted into queue: " + command.toString());
            ensureSpareThreads();
        } else {
            if (workers.size() < maxPoolSize) {
                addWorker();
                boolean offeredAfter = taskQueue.offer(command);
                if (offeredAfter) {
                    System.out.println("[Pool] Task accepted into queue after adding worker: " + command.toString());
                    ensureSpareThreads();
                    return;
                }
            }
            System.out.println("[Rejected] Task " + command.toString() + " was rejected due to overload!");
            rejectionHandler.rejectedExecution(command, null);
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) throw new NullPointerException();
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    private void ensureSpareThreads() {
        int idle = idleCount.get();
        if (idle < minSpareThreads && workers.size() < maxPoolSize) {
            addWorker();
        }
    }

    private void addWorker() {
        Worker worker = new Worker();
        workers.add(worker);
        Thread thread = threadFactory.newThread(worker);
        worker.setThread(thread);
        thread.start();
    }

    @Override
    public void shutdown() {
        if (!isShutdown.getAndSet(true)) {
            System.out.println("[Pool] Shutdown initiated.");
            for (Worker w : workers) {
                w.interruptIfIdle();
            }
        }
    }

    @Override
    public void shutdownNow() {
        if (!isShutdownNow.getAndSet(true)) {
            System.out.println("[Pool] Immediate shutdown initiated.");
            isShutdown.set(true);
            taskQueue.clear();
            for (Worker w : workers) {
                w.interruptNow();
            }
        }
    }


    private class Worker implements Runnable {
        private Thread thread;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean idle = new AtomicBoolean(false);

        public void setThread(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void run() {
            String name = Thread.currentThread().getName();
            try {
                while (running.get()) {
                    idle.set(true);
                    idleCount.incrementAndGet();
                    Runnable task;
                    try {
                        task = taskQueue.poll(keepAliveTimeMillis, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        if (isShutdownNow.get()) {
                            break;
                        } else {
                            continue;
                        }
                    } finally {
                        idle.set(false);
                        idleCount.decrementAndGet();
                    }

                    if (task != null) {
                        if (isShutdown.get()) {
                            break;
                        }
                        System.out.println("[Worker] " + name + " executes " + task.toString());
                        try {
                            task.run();
                        } catch (RuntimeException ex) {
                            System.out.println("[Worker] Exception in task: " + ex.getMessage());
                        }
                    } else {
                        if (isShutdown.get()) {
                            break;
                        }
                        if (workers.size() > corePoolSize) {
                            System.out.println("[Worker] " + name + " idle timeout, stopping.");
                            break;
                        }
                    }
                }
            } finally {
                workers.remove(this);
                System.out.println("[Worker] " + name + " terminated.");
            }
        }

        public void interruptIfIdle() {
            if (idle.get() && thread != null) {
                thread.interrupt();
            }
        }

        public void interruptNow() {
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    public static class AbortPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            throw new RejectedExecutionException("Task rejected: " + r.toString());
        }
    }

    public static class CallerRunsPolicyHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            System.out.println("[Rejected] Running task in caller thread: " + r.toString());
            r.run();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CustomThreadPool pool = new CustomThreadPool(
                2,
                4,
                5, TimeUnit.SECONDS,
                5,
                1,
                new CallerRunsPolicyHandler(),
                "MyPool"
        );

        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("[Task] Start task " + taskId + " on " + Thread.currentThread().getName());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("[Task] Finish task " + taskId + " on " + Thread.currentThread().getName());
            });
        }

        Thread.sleep(10000);
        pool.shutdown();
        System.out.println("[Main] Shutdown called.");
    }
}
