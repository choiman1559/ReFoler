package com.refoler.app.process.actions;

import android.os.Build;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class DeAsyncJob<T> {
    private final AsyncRunnable<T> runnable;
    private final AtomicReference<T> resultAtomic;
    private final Object lock = new Object();

    public interface AsyncRunnable<T> {
        void run(DeAsyncJob<T> job);
    }

    public DeAsyncJob(AsyncRunnable<T> runnable) {
        this.runnable = runnable;
        this.resultAtomic = new AtomicReference<>(null);
    }

    @NotNull
    public T runAndWait() {
        runnable.run(this);
        synchronized (lock) {
            while (resultAtomic.get() == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Thread.onSpinWait();
                    }
                }
            }
        }
        return resultAtomic.get();
    }

    public void setResult(@NotNull T resultObj) {
        synchronized (lock) {
            resultAtomic.set(resultObj);
            lock.notifyAll();
        }
    }
}
