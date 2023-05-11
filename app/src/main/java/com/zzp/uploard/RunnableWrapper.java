package com.zzp.uploard;

import java.util.concurrent.ScheduledFuture;

public abstract class RunnableWrapper implements Runnable {
    protected ScheduledFuture<?> future;

    public void setFuture(ScheduledFuture<?> future) {
        this.future = future;
    }

    public boolean cancel() {
        if (future != null && !future.isCancelled()) {
            boolean result = future.cancel(true);
            future = null;
            return result;
        }
        future = null;
        return false;
    }
}
