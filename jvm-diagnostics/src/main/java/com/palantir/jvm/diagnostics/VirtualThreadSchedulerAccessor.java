/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.jvm.diagnostics;

public interface VirtualThreadSchedulerAccessor {
    /**
     * Returns an estimate of the number of virtual threads that are currently mounted by the scheduler;
     * -1 if not known.
     */
    int getMountedVirtualThreadCount();

    /**
     * Returns the scheduler's target parallelism.
     */
    int getParallelism();

    /**
     * Returns the current number of platform threads that the scheduler has started but have not terminated;
     * -1 if not known.
     */
    int getPoolSize();

    /**
     * Returns an estimate of the number of virtual threads that are queued to the scheduler to start or continue
     * execution; -1 if not known.
     */
    long getQueuedVirtualThreadCount();

    /**
     * Sets the scheduler's target parallelism.
     */
    void setParallelism(int size);
}
