/*
 * Copyright 2015 Kejun Xia
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

package com.shipdream.lib.android.mvc;

/**
 * Indicates the object having managed state that will be saved and restored by {@link StateKeeper}
 * @param <T> The type defining the state of this object
 */
public interface StateManaged <T> {
    /**
     * @return The class type of the state. It's only used to instantiate the state object by the
     * framework.
     */
    Class<T> getStateType();

    /**
     * Binds the state to this object
     * @param state The state to bind
     */
    void bindState(T state);

    /**
     * @return The state of the object implementing {@link StateManaged}
     */
    T getState();

    /**
     * Restores the state of the object implementing {@link StateManaged}
     * @param restoredState The restored state that will be applied to the object implementing {@link StateManaged}
     */
    void restoreState(T restoredState);
}
