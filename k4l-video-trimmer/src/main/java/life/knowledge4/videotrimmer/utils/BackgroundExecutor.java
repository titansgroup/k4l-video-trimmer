/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package life.knowledge4.videotrimmer.utils;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackgroundExecutor {

    private static final String TAG = "BackgroundExecutor";

    public static final Executor DEFAULT_EXECUTOR = Executors.newScheduledThreadPool(2 * Runtime.getRuntime().availableProcessors());
    private static Executor executor = DEFAULT_EXECUTOR;
    private static final List<Task> TASKS = new ArrayList<>();
    private static final ThreadLocal<String> CURRENT_SERIAL = new ThreadLocal<>();

    private BackgroundExecutor() {
    }

    /**
     * Execute a runnable after the given delay.
     *
     * @param runnable the task to execute
     * @param delay    the time from now to delay execution, in milliseconds
     *                 <p>
     *                 if <code>delay</code> is strictly positive and the current
     *                 executor does not support scheduling (if
     *                 Executor has been called with such an
     *                 executor)
     * @return Future associated to the running task
     * @throws IllegalArgumentException if the current executor set by Executor
     *                                  does not support scheduling
     */
    private static Future<?> directExecute(Runnable runnable, long delay) {
        Future<?> future = null;
        if (delay > 0) {
            /* no serial, but a delay: schedule the task */
            if (!(executor instanceof ScheduledExecutorService)) {
                throw new IllegalArgumentException("The executor set does not support scheduling");
            }
            ScheduledExecutorService scheduledExecutorService = (ScheduledExecutorService) executor;
            future = scheduledExecutorService.schedule(runnable, delay, TimeUnit.MILLISECONDS);
        } else {
            if (executor instanceof ExecutorService) {
                ExecutorService executorService = (ExecutorService) executor;
                future = executorService.submit(runnable);
            } else {
                /* non-cancellable task */
                executor.execute(runnable);
            }
        }
        return future;
    }

    /**
     * Execute a task after (at least) its delay <strong>and</strong> after all
     * tasks added with the same non-null <code>serial</code> (if any) have
     * completed execution.
     *
     * @param task the task to execute
     * @throws IllegalArgumentException if <code>task.delay</code> is strictly positive and the
     *                                  current executor does not support scheduling (if
     *                                  Executor has been called with such an
     *                                  executor)
     */
    public static synchronized void execute(Task task) {
        Future<?> future = null;
        if (task.serial == null || !hasSerialRunning(task.serial)) {
            task.executionAsked = true;
            future = directExecute(task, task.remainingDelay);
        }
        if ((task.id != null || task.serial != null) && !task.managed.get()) {
            /* keep task */
            task.future = future;
            TASKS.add(task);
        }
    }

    /**
     * Indicates whether a task with the specified <code>serial</code> has been
     * submitted to the executor.
     *
     * @param serial the serial queue
     * @return <code>true</code> if such a task has been submitted,
     * <code>false</code> otherwise
     */
    private static boolean hasSerialRunning(String serial) {
        for (Task task : TASKS) {
            if (task.executionAsked && serial.equals(task.serial)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieve and remove the first task having the specified
     * <code>serial</code> (if any).
     *
     * @param serial the serial queue
     * @return task if found, <code>null</code> otherwise
     */
    private static Task take(String serial) {
        int len = TASKS.size();
        for (int i = 0; i < len; i++) {
            if (serial.equals(TASKS.get(i).serial)) {
                return TASKS.remove(i);
            }
        }
        return null;
    }

    /**
     * Cancel all tasks having the specified <code>id</code>.
     *
     * @param id                    the cancellation identifier
     * @param mayInterruptIfRunning <code>true</code> if the thread executing this task should be
     *                              interrupted; otherwise, in-progress tasks are allowed to
     *                              complete
     */
    public static synchronized void cancelAll(String id, boolean mayInterruptIfRunning) {
        for (int i = TASKS.size() - 1; i >= 0; i--) {
            Task task = TASKS.get(i);
            if (id.equals(task.id)) {
                if (task.future != null) {
                    task.future.cancel(mayInterruptIfRunning);
                    if (!task.managed.getAndSet(true)) {
						/*
						 * the task has been submitted to the executor, but its
						 * execution has not started yet, so that its run()
						 * method will never call postExecute()
						 */
                        task.postExecute();
                    }
                } else if (task.executionAsked) {
                    Log.w(TAG, "A task with id " + task.id + " cannot be cancelled (the executor set does not support it)");
                } else {
					/* this task has not been submitted to the executor */
                    TASKS.remove(i);
                }
            }
        }
    }

    public static abstract class Task implements Runnable {

        private String id;
        private long remainingDelay;
        private long targetTimeMillis; /* since epoch */
        private String serial;
        private boolean executionAsked;
        private Future<?> future;

        /*
         * A task can be cancelled after it has been submitted to the executor
         * but before its run() method is called. In that case, run() will never
         * be called, hence neither will postExecute(): the tasks with the same
         * serial identifier (if any) will never be submitted.
         *
         * Therefore, cancelAll() *must* call postExecute() if run() is not
         * started.
         *
         * This flag guarantees that either cancelAll() or run() manages this
         * task post execution, but not both.
         */
        private AtomicBoolean managed = new AtomicBoolean();

        public Task(String id, long delay, String serial) {
            if (!"".equals(id)) {
                this.id = id;
            }
            if (delay > 0) {
                remainingDelay = delay;
                targetTimeMillis = System.currentTimeMillis() + delay;
            }
            if (!"".equals(serial)) {
                this.serial = serial;
            }
        }

        @Override
        public void run() {
            if (managed.getAndSet(true)) {
                /* cancelled and postExecute() already called */
                return;
            }

            try {
                CURRENT_SERIAL.set(serial);
                execute();
            } finally {
                /* handle next tasks */
                postExecute();
            }
        }

        public abstract void execute();

        private void postExecute() {
            if (id == null && serial == null) {
				/* nothing to do */
                return;
            }
            CURRENT_SERIAL.set(null);
            synchronized (BackgroundExecutor.class) {
				/* execution complete */
                TASKS.remove(this);

                if (serial != null) {
                    Task next = take(serial);
                    if (next != null) {
                        if (next.remainingDelay != 0) {
							/* the delay may not have elapsed yet */
                            next.remainingDelay = Math.max(0L, targetTimeMillis - System.currentTimeMillis());
                        }
						/* a task having the same serial was queued, execute it */
                        BackgroundExecutor.execute(next);
                    }
                }
            }
        }
    }
}

