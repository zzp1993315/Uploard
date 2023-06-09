package com.zzp.uploard;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * 后台线程池
 * 类/接口注释
 *
 * @author panrq
 * @createDate Dec 29, 2014
 */
// an asynchronous task executor(thread pool)
public class BackgroundTaskExecutor {
    private static ExecutorService sThreadPoolExecutor = null;
    private static ScheduledThreadPoolExecutor sScheduledThreadPoolExecutor = null;

    public static void executeTask(Runnable task) {
        ensureThreadPoolExecutor();
        sThreadPoolExecutor.execute(task);
    }

//    public static <T> Future<T> submitTask(Callable<T> task) {
//        ensureThreadPoolExecutor();
//        return sThreadPoolExecutor.submit(task);
//    }

    public static ScheduledFuture<?> scheduleTask(long delay, Runnable task) {
        ensureScheduledThreadPoolExecutor();
        ScheduledFuture<?> future = sScheduledThreadPoolExecutor.schedule(task, delay, TimeUnit.MILLISECONDS);
        if (task instanceof RunnableWrapper) {
            ((RunnableWrapper)task).setFuture(future);
        }
        return future;
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delayMillis, long periodMillis) {
        ensureScheduledThreadPoolExecutor();
        ScheduledFuture<?> future = sScheduledThreadPoolExecutor.scheduleAtFixedRate(task, delayMillis, periodMillis, TimeUnit.MILLISECONDS);
        if (task instanceof RunnableWrapper) {
            ((RunnableWrapper)task).setFuture(future);
        }
        return future;
    }

    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delayMillis, long periodMillis) {
        ensureScheduledThreadPoolExecutor();
        ScheduledFuture<?> future = sScheduledThreadPoolExecutor.scheduleWithFixedDelay(task, delayMillis, periodMillis, TimeUnit.MILLISECONDS);
        if (task instanceof RunnableWrapper) {
            ((RunnableWrapper)task).setFuture(future);
        }
        return future;
    }

    /**
     * 如果要在未执行时取消，参考使用RunnableWrapper
     * @param task
     */
    public static void removeTask(Runnable task) {
        if (sScheduledThreadPoolExecutor != null) {
            sScheduledThreadPoolExecutor.remove(task);
        }
        if (task instanceof RunnableWrapper) {
            ((RunnableWrapper)task).cancel();
        }
    }

    private synchronized static void ensureThreadPoolExecutor() {
        if (sThreadPoolExecutor == null) {
            sThreadPoolExecutor = Executors.newFixedThreadPool(10);
//            sThreadPoolExecutor = new ThreadPoolExecutor(5, 5,
//                    60L, TimeUnit.SECONDS,
//                    new LinkedBlockingQueue<Runnable>(),
//                    Executors.defaultThreadFactory());
        }
    }

    private synchronized static void ensureScheduledThreadPoolExecutor() {
        if (sScheduledThreadPoolExecutor == null) {
            sScheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(5);
        }
    }

    public static void shutdown() {
        if (sThreadPoolExecutor != null) {
            sThreadPoolExecutor.shutdown();
            sThreadPoolExecutor = null;
        }

        if (sScheduledThreadPoolExecutor != null) {
            sScheduledThreadPoolExecutor.shutdown();
            sScheduledThreadPoolExecutor = null;
        }
    }
}
