/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import java.util.Optional;

/**
 * This utility class provides accessors to individual diagnostic getters. Every method should
 * return an optional of an interface with a single getter method in order to provide the most
 * flexibility if the runtime modifies or removes implementations over time.
 */
public final class JvmDiagnostics {

    /**
     * Returns an {@link SafepointTimeAccessor} which provides safepoint information. This functionality
     * is not supported on all java runtimes, and an {@link Optional#empty()} is returned in cases
     * safepoint data is unavailable.
     *
     * The resulting instance should be reused rather than calling this factory each time a
     * value is needed.
     */
    public static Optional<SafepointTimeAccessor> totalSafepointTime() {
        try {
            // Classes used by HotspotSafepointTimeAccessor are not guaranteed to exist at runtime on all JREs
            // so we must fail gracefully.
            return Optional.of(new HotspotSafepointTimeAccessor());
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private JvmDiagnostics() {}

    private static final class HotspotSafepointTimeAccessor implements SafepointTimeAccessor {

        private final sun.management.HotspotRuntimeMBean hotspotRuntimeManagementBean =
                sun.management.ManagementFactoryHelper.getHotspotRuntimeMBean();

        @Override
        public long safepointTimeMilliseconds() {
            return hotspotRuntimeManagementBean.getTotalSafepointTime();
        }
    }
}
