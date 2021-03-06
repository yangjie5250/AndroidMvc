/*
 * Copyright 2016 Kejun Xia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipdream.lib.android.mvc.controller.internal;


import com.shipdream.lib.android.mvc.MvcBean;
import com.shipdream.lib.android.mvc.controller.BaseController;
import com.shipdream.lib.android.mvc.event.BaseEventC;
import com.shipdream.lib.android.mvc.event.BaseEventV;
import com.shipdream.lib.android.mvc.event.bus.EventBus;
import com.shipdream.lib.android.mvc.event.bus.annotation.EventBusC;
import com.shipdream.lib.android.mvc.event.bus.annotation.EventBusV;
import com.shipdream.lib.android.mvc.manager.internal.BaseManagerImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

/**
 * Base controller implementation implements {@link BaseController}. A controller is responsible to
 * manage the corresponding view. When multiple controllers have shared logic or data, break them
 * out into a manager extending {@link BaseManagerImpl}. For example, a common  scenario is multiple
 * controllers can share an AccountManager and monitor the account change events.
 */
public abstract class BaseControllerImpl<MODEL> extends MvcBean<MODEL> implements BaseController<MODEL> {
    interface AndroidPoster {
        void post(EventBus eventBusV, BaseEventV eventV);
    }

    static AndroidPoster androidPoster;
    protected Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    @EventBusV
    EventBus eventBus2V;

    @Inject
    @EventBusC
    EventBus eventBus2C;

    @Inject
    ExecutorService executorService;

    /**
     * Called when the controller is constructed. Note that it could be called either when the
     * controller is instantiated for the first time or restored by views.
     * <p/>
     * <p>The model of the controller will be instantiated by model's default no-argument
     * constructor here whe {@link #modelType()} doesn't return null.</p>
     */
    public void onConstruct() {
        super.onConstruct();
        eventBus2C.register(this);
    }

    /**
     * Called when the controller is disposed. This occurs when the controller is de-referenced and
     * not retained by any objects.
     */
    @Override
    public void onDisposed() {
        super.onDisposed();
        eventBus2C.unregister(this);
    }

    @Override
    public void bindModel(Object sender, MODEL model) {
        super.bindModel(model);
    }

    /**
     * Model represents the state of the view this controller is managing.
     *
     * @return Null if the controller doesn't need to get its model saved and restored automatically
     * when {@link #modelType()} returns null.
     */
    @Override
    public MODEL getModel() {
        return super.getModel();
    }

    /**
     * Post an event to controllers. The event will be posted on the same thread that the
     * caller is running on.
     *
     * @param event event to controllers
     */
    protected void postEvent2C(final BaseEventC event) {
        if (eventBus2C != null) {
            eventBus2C.post(event);
        } else {
            logger.warn("Trying to post event {} to EventBusC which is null", event.getClass().getName());
        }
    }

    /**
     * Post an event to views on
     * <ul>
     * <li>Android main thread -- when detected android OS. Note that, if the caller is on main thread, event will be
     * execute immediately on the main thread. Otherwise it will be post to the main thread message queue.</li>
     * <li>Same thread of caller -- if on usual JVM</li>
     * </ul>
     *
     * @param event event to views
     */
    protected void postEvent2V(final BaseEventV event) {
        if (androidPoster != null) {
            //Run on android OS
            androidPoster.post(eventBus2V, event);
        } else {
            if (eventBus2V != null) {
                eventBus2V.post(event);
            } else {
                logger.warn("Trying to post event {} to EventBusV which is null", event.getClass().getName());
            }
        }
    }

    /**
     * Run a task on threads supplied by injected {@link ExecutorService} without a callback. By
     * default it runs tasks on separate threads by {@link ExecutorService} injected from AndroidMvc
     * framework. A simple {@link ExecutorService} that runs tasks on the same thread in test cases
     * to make the test easier.
     * @param sender          Who wants run the task
     * @param task            The task
     * @return The monitor to track the state of the execution of the task. It also can cancel the
     * task.
     *
     * @since 2.2.0
     */
    protected Monitor runTask(Object sender, final Task task) {
        return runTask(sender, executorService, task, null);
    }

    /**
     * Run a task on threads supplied by injected {@link ExecutorService}. By default it runs tasks
     * on separate threads by {@link ExecutorService} injected from AndroidMvc framework. A simple
     * {@link ExecutorService} that runs tasks on the same thread in test cases to make the test
     * easier.
     * @param sender          Who wants run the task
     * @param task            The task
     * @param callback        The callback
     * @return The monitor to track the state of the execution of the task. It also can cancel the
     * task.
     *
     * @since 2.2.0
     */
    protected Monitor runTask(Object sender, final Task task, final Task.Callback callback) {
        return runTask(sender, executorService, task, callback);
    }

    /**
     * Run a task on the threads supplied by the given {@link ExecutorService}. The task could be
     * run either asynchronously or synchronously depending on the given executorService.
     *
     * @param sender          Who wants run the task
     * @param executorService The executor service managing how the task will be run
     * @param task            The task
     * @param callback        The callback
     * @return The monitor to track the state of the execution of the task. It also can cancel the
     * task.
     *
     * @since 2.2.0
     */
    protected Monitor runTask(Object sender, ExecutorService executorService,
                              final Task task, final Task.Callback callback) {
        final Monitor monitor = new Monitor(task, callback);

        monitor.setFuture(executorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (monitor.getState() == Monitor.State.CANCELED) {
                    return null;
                }

                monitor.setState(Monitor.State.STARTED);
                if (callback != null) {
                    callback.onStarted();
                }

                try {
                    task.execute(monitor);

                    if (monitor.getState() != Monitor.State.CANCELED) {
                        monitor.setState(Monitor.State.DONE);

                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                } catch (Exception e) {
                    boolean interruptedByCancel = false;
                    if (e instanceof InterruptedException) {
                        if (monitor.getState() == Monitor.State.INTERRUPTED) {
                            interruptedByCancel = true;
                        }
                    }
                    //If the exception is an interruption caused by cancelling, then ignore it
                    if (!interruptedByCancel) {
                        monitor.setState(Monitor.State.ERRED);
                        if (callback != null) {
                            callback.onException(e);
                        }
                    }
                }

                return null;
            }
        }));

        return monitor;
    }

    //region Deprecated methods

    /**
     * Run async task on the default ExecutorService injected as a field of this class. Exceptions
     * occur during running the task will be suppressed but logged at warning level. <b>Be careful,
     * only use this method when you are sure there will be no exceptions occur during the execution
     * of async task, or you want to ignore all exceptions intentionally.</b> Otherwise use
     * {@link #runAsyncTask(Object, AsyncTask, AsyncExceptionHandler)} to handle errors explicitly.
     *
     * @param sender    who initiated this task
     * @param asyncTask task to execute
     * @return returns the reference of {@link AsyncTask} that can be used to query its state and cancel it.
     *
     * @deprecated see {@link BaseControllerImpl#runTask(Object, ExecutorService, Task, Task.Callback)} and
     * {@link BaseControllerImpl#runTask(Object, Task)} and {@link BaseControllerImpl#runTask(Object, Task, Task.Callback)}
     */
    protected AsyncTask runAsyncTask(Object sender, final AsyncTask asyncTask) {
        return runAsyncTask(sender, executorService, asyncTask, null);
    }

    /**
     * Run async task on the default ExecutorService injected as a field of this class. Exceptions
     * occur during running the task will be handled by the given {@link AsyncExceptionHandler}.
     *
     * @param sender                who initiated this task
     * @param asyncTask             task to execute
     * @param asyncExceptionHandler error handler for the exception during running the task
     * @return the reference of {@link AsyncTask} that can be used to query its state and cancel it.
     *
     * @deprecated see {@link BaseControllerImpl#runTask(Object, ExecutorService, Task, Task.Callback)} and
     * {@link BaseControllerImpl#runTask(Object, Task)} and {@link BaseControllerImpl#runTask(Object, Task, Task.Callback)}
     */
    protected AsyncTask runAsyncTask(Object sender, final AsyncTask asyncTask,
                                     final AsyncExceptionHandler asyncExceptionHandler) {
        return runAsyncTask(sender, executorService, asyncTask, asyncExceptionHandler);
    }

    /**
     * Run async task on the given ExecutorService. Exceptions occur during running the task will be
     * suppressed but logged at warning level. <b>Be careful, only use this method when you are sure
     * there will be no exceptions occur during the execution of async task, or you want to ignore
     * all exceptions intentionally.</b>  Otherwise use
     * {@link #runAsyncTask(Object, java.util.concurrent.ExecutorService, AsyncTask, AsyncExceptionHandler)}
     * to handle errors explicitly.
     *
     * @param sender          who initiated this task
     * @param executorService the executor service provided to execute the async task
     * @param asyncTask       task to execute
     * @return the reference of {@link AsyncTask} that can be used to query its state and cancel it.
     *
     * @deprecated see {@link BaseControllerImpl#runTask(Object, ExecutorService, Task, Task.Callback)} and
     * {@link BaseControllerImpl#runTask(Object, Task)} and {@link BaseControllerImpl#runTask(Object, Task, Task.Callback)}
     */
    protected AsyncTask runAsyncTask(Object sender, ExecutorService executorService,
                                     final AsyncTask asyncTask) {
        return runAsyncTask(sender, executorService, asyncTask, null);
    }


    /**
     * Run async task on the given ExecutorService. Exceptions occur during running the task will be
     * handled by the given {@link AsyncExceptionHandler}.
     *
     * @param sender                who initiated this task
     * @param executorService       the executor service provided to execute the async task
     * @param asyncTask             task to execute
     * @param asyncExceptionHandler error handler for the exception during running the task. If null
     *                              is given all exceptions occur during the execution of the async
     *                              task will be suppressed with warning level log.
     * @return the reference of {@link AsyncTask} that can be used to query its state and cancel it.
     *
     * @deprecated see {@link BaseControllerImpl#runTask(Object, ExecutorService, Task, Task.Callback)} and
     * {@link BaseControllerImpl#runTask(Object, Task)} and {@link BaseControllerImpl#runTask(Object, Task, Task.Callback)}
     */
    protected AsyncTask runAsyncTask(Object sender, ExecutorService executorService,
                                     final AsyncTask asyncTask,
                                     final AsyncExceptionHandler asyncExceptionHandler) {
        asyncTask.state = AsyncTask.State.RUNNING;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    asyncTask.execute();
                    if (asyncTask.state != AsyncTask.State.CANCELED) {
                        asyncTask.state = AsyncTask.State.DONE;
                    }
                } catch (Exception e) {
                    asyncTask.state = AsyncTask.State.ERRED;
                    if (asyncExceptionHandler == null) {
                        logger.warn(e.getMessage(), e);
                    } else {
                        asyncExceptionHandler.handleException(e);
                    }
                }
            }
        });

        return asyncTask;
    }

    //endregion

}
