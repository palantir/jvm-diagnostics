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
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This utility class provides accessors to individual diagnostic getters. Every method should
 * return an optional of an interface with a single getter method in order to provide the most
 * flexibility if the runtime modifies or removes implementations over time.
 */
public final class JvmDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(JvmDiagnostics.class);

    /**
     * Returns an {@link SafepointTimeAccessor} which provides safepoint information. This functionality
     * is not supported on all java runtimes, and an {@link Optional#empty()} is returned in cases
     * safepoint data is unavailable.
     *
     * The resulting instance should be reused rather than calling this factory each time a
     * value is needed.
     *
     * Currently this supports up to java 16 assuming {@code --illegal-access=deny} is not used, and java 17+
     * only when the {@code --illegal-access=permit} parameter is provided. Once a safe, suitable replacement is
     * found, we will likely use a multi-release jar to leverage the new functionality.
     */
    public static Optional<SafepointTimeAccessor> totalSafepointTime() {
        try {
            // Classes used by HotspotSafepointTimeAccessor are not guaranteed to exist at runtime on all JREs
            // so we must fail gracefully.
            return Optional.of(new HotspotSafepointTimeAccessor());
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotSafepointTimeAccessor", t);
            return Optional.empty();
        }
    }

    /**
     * Returns an {@link ThreadAllocatedBytesAccessor} which allows access to an estimate of the total number of
     * allocated bytes. This functionality is not supported on all java runtimes, and an {@link Optional#empty()} is
     * returned in cases thread allocation data is unavailable.
     *
     * The resulting instance should be reused rather than calling this factory each time a
     * value is needed.
     */
    public static Optional<ThreadAllocatedBytesAccessor> threadAllocatedBytes() {
        try {
            HotspotThreadAllocatedBytesAccessor accessor = new HotspotThreadAllocatedBytesAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotThreadAllocatedBytesAccessor", t);
            return Optional.empty();
        }
    }

    /**
     * Returns an {@link HotspotThreadUserTimeAccessor}. This functionality is not supported on all java runtimes,
     * and an {@link Optional#empty()} is returned in cases thread allocation data is unavailable.
     *
     * The resulting instance should be reused rather than calling this factory each time a
     * value is needed.
     */
    public static Optional<ThreadCpuTimeAccessor> threadCpuTime() {
        try {
            HotspotThreadCpuTimeAccessor accessor = new HotspotThreadCpuTimeAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotThreadCpuTimeAccessor", t);
            return Optional.empty();
        }
    }

    /**
     * Returns an {@link HotspotThreadUserTimeAccessor}. This functionality is not supported on all java runtimes,
     * and an {@link Optional#empty()} is returned in cases thread allocation data is unavailable.
     *
     * The resulting instance should be reused rather than calling this factory each time a
     * value is needed.
     */
    public static Optional<ThreadUserTimeAccessor> threadUserTime() {
        try {
            HotspotThreadUserTimeAccessor accessor = new HotspotThreadUserTimeAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotThreadUserTimeAccessor", t);
            return Optional.empty();
        }
    }

    /**
     * Returns an {@link HotspotCpuSharesAccessor}. This functionality is not supported on all java runtimes,
     * and an {@link Optional#empty()} is returned in cases cpu share information is not supported.
     *
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8281181">JDK-8281181</a>
     * @see <a href="https://danluu.com/cgroup-throttling/">danluu.com/cgroup-throttling</a>
     */
    public static Optional<CpuSharesAccessor> cpuShares() {
        try {
            HotspotCpuSharesAccessor accessor = new HotspotCpuSharesAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotCpuSharesAccessor", t);
            return Optional.empty();
        }
    }

    /**
     * Returns a {@link HotspotDnsCacheTtlAccessor}. This functionality is not supported on all java runtimes,
     * and an {@link Optional#empty()} is returned in cases cpu share information is not supported.
     */
    public static Optional<DnsCacheTtlAccessor> dnsCacheTtl() {
        try {
            HotspotDnsCacheTtlAccessor accessor = new HotspotDnsCacheTtlAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotDnsCacheTtlAccessor", t);
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

    private static final class HotspotThreadAllocatedBytesAccessor implements ThreadAllocatedBytesAccessor {

        private final com.sun.management.ThreadMXBean hotspotThreadImpl = loadThreadManagementBean();

        boolean isEnabled() {
            return hotspotThreadImpl != null
                    && hotspotThreadImpl.isThreadAllocatedMemorySupported()
                    && hotspotThreadImpl.isThreadAllocatedMemoryEnabled();
        }

        @Override
        public long getAllocatedBytes(long threadId) {
            return hotspotThreadImpl.getThreadAllocatedBytes(threadId);
        }

        private static com.sun.management.ThreadMXBean loadThreadManagementBean() {
            java.lang.management.ThreadMXBean threadBean = java.lang.management.ManagementFactory.getThreadMXBean();
            return threadBean instanceof com.sun.management.ThreadMXBean
                    ? (com.sun.management.ThreadMXBean) threadBean
                    : null;
        }
    }

    private static final class HotspotThreadUserTimeAccessor implements ThreadUserTimeAccessor {

        private final java.lang.management.ThreadMXBean threadManagementBean =
                java.lang.management.ManagementFactory.getThreadMXBean();

        boolean isEnabled() {
            return threadManagementBean != null
                    && threadManagementBean.isThreadCpuTimeSupported()
                    && threadManagementBean.isThreadCpuTimeEnabled();
        }

        @Override
        public long getUserTimeNanoseconds(long threadId) {
            return threadManagementBean.getThreadUserTime(threadId);
        }
    }

    private static final class HotspotThreadCpuTimeAccessor implements ThreadCpuTimeAccessor {

        private final java.lang.management.ThreadMXBean threadManagementBean =
                java.lang.management.ManagementFactory.getThreadMXBean();

        boolean isEnabled() {
            return threadManagementBean != null
                    && threadManagementBean.isThreadCpuTimeSupported()
                    && threadManagementBean.isThreadCpuTimeEnabled();
        }

        @Override
        public long getCpuTimeNanoseconds(long threadId) {
            return threadManagementBean.getThreadCpuTime(threadId);
        }
    }

    private static final class HotspotCpuSharesAccessor implements CpuSharesAccessor {

        private final jdk.internal.platform.Metrics metrics = jdk.internal.platform.Metrics.systemMetrics();

        boolean isEnabled() {
            return metrics.getCpuShares() != -2;
        }

        @Override
        public OptionalLong getCpuShares() {
            long result = metrics.getCpuShares();
            if (result == -1) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(result);
        }
    }

    private static final class HotspotDnsCacheTtlAccessor implements DnsCacheTtlAccessor {

        boolean isEnabled() {
            try {
                // Ensure invocations succeed. If sufficient exports aren't present, this will throw.
                getPositiveSeconds();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public int getPositiveSeconds() {
            return sun.net.InetAddressCachePolicy.get();
        }

        @Override
        public int getNegativeSeconds() {
            return sun.net.InetAddressCachePolicy.getNegative();
        }

        @Override
        public int getStaleSeconds() {
            if (Runtime.version().feature() >= 21) {
                // Introduced in Java 21 by https://bugs.openjdk.org/browse/JDK-8306653
                try {
                    return (Integer) sun.net.InetAddressCachePolicy.class
                            .getMethod("getStale")
                            .invoke(null);
                } catch (ReflectiveOperationException roe) {
                    log.debug("Failed to load stale InetAddressCachePolicy", roe);
                    return 0;
                }
            }
            return 0;
        }
    }
}
