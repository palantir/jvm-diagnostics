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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * This utility class provides accessors to individual diagnostic getters. Every method should
 * return an optional of an interface with a single getter method in order to provide the most
 * flexibility if the runtime modifies or removes implementations over time.
 */
public final class JvmDiagnostics {

    private static final SafeLogger log = SafeLoggerFactory.get(JvmDiagnostics.class);

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
     * Returns an {@link ThreadUserTimeAccessor}. This functionality is not supported on all java runtimes,
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
     * Returns an {@link ThreadUserTimeAccessor}. This functionality is not supported on all java runtimes,
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
     * Returns an {@link CpuSharesAccessor}. This functionality is not supported on all java runtimes,
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
     * Returns a {@link DnsCacheTtlAccessor}. This functionality is not supported on all java runtimes,
     * and an {@link Optional#empty()} is returned in cases TTL information is not accessible.
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

    /**
     * Returns a {@link VirtualThreadSchedulerAccessor}. This functionality is not supported on all java runtimes,
     * and an {@link Optional#empty()} is returned in cases where virtual thread scheduler metrics are not accessible.
     *
     * The resulting instance should be reused rather than calling this factory each time a
     * value is needed.
     *
     * This is only supported on Java 24 and above.
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/24/docs/api/jdk.management/jdk/management/VirtualThreadSchedulerMXBean.html">jdk.management.VirtualThreadSchedulerMXBean</a>
     */
    public static Optional<VirtualThreadSchedulerAccessor> virtualThreadScheduler() {
        try {
            HotspotVirtualThreadSchedulerAccessor accessor = new HotspotVirtualThreadSchedulerAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a HotspotVirtualThreadSchedulerAccessor", t);
            return Optional.empty();
        }
    }

    /**
     * Returns a {@link PressureMetricsAccessor} which provides access to Linux Pressure Stall Information (PSI)
     * metrics. This functionality is only available on Linux systems with PSI support (kernel 4.20+).
     *
     * The accessor automatically detects container environments and prefers container-specific metrics when
     * available, falling back to system-wide metrics otherwise.
     *
     * The resulting instance should be reused rather than calling this factory each time a value is needed.
     *
     * @return a PSI metrics accessor, or empty if PSI is not available
     * @see <a href="https://docs.kernel.org/accounting/psi.html">Linux PSI Documentation</a>
     */
    public static Optional<PressureMetricsAccessor> pressureMetrics() {
        try {
            LinuxPressureMetricsAccessor accessor = new LinuxPressureMetricsAccessor();
            return accessor.isEnabled() ? Optional.of(accessor) : Optional.empty();
        } catch (Throwable t) {
            log.debug("Failed to create a LinuxPressureMetricsAccessor", t);
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

        @Override
        public long safepointSyncTimeMilliseconds() {
            return hotspotRuntimeManagementBean.getSafepointSyncTime();
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
            java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            return (threadBean instanceof com.sun.management.ThreadMXBean mxBean) ? mxBean : null;
        }
    }

    private static final class HotspotThreadUserTimeAccessor implements ThreadUserTimeAccessor {

        private final java.lang.management.ThreadMXBean threadManagementBean = ManagementFactory.getThreadMXBean();

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

        private final java.lang.management.ThreadMXBean threadManagementBean = ManagementFactory.getThreadMXBean();

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

        private final IntSupplier staleAccessor = createStaleAccessor();

        private static IntSupplier createStaleAccessor() {
            if (Runtime.version().feature() >= 21) {
                // Introduced in Java 21 by https://bugs.openjdk.org/browse/JDK-8306653
                try {
                    Method getStale = sun.net.InetAddressCachePolicy.class.getMethod("getStale");
                    return () -> {
                        try {
                            return (Integer) getStale.invoke(null);
                        } catch (ReflectiveOperationException roe) {
                            log.debug("Failed to load stale InetAddressCachePolicy", roe);
                            return 0;
                        }
                    };
                } catch (ReflectiveOperationException roe) {
                    log.debug("Failed to load stale InetAddressCachePolicy", roe);
                }
            }
            return () -> 0;
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
            return staleAccessor.getAsInt();
        }
    }

    // this implementation must use reflection because jdk.management.VirtualThreadSchedulerMXBean is
    // only available on jdk24+
    private static final class HotspotVirtualThreadSchedulerAccessor implements VirtualThreadSchedulerAccessor {
        private final MethodHandle mxBeanGetMountedVirtualThreadCount;
        private final MethodHandle mxBeanGetParallelism;
        private final MethodHandle mxBeanGetPoolSize;
        private final MethodHandle mxBeanGetQueuedVirtualThreadCount;
        private final MethodHandle mxBeanSetParallelism;
        private final Object inst;

        HotspotVirtualThreadSchedulerAccessor() throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> mxBeanClass = lookup.findClass("jdk.management.VirtualThreadSchedulerMXBean");
            Class<?> managementFactoryClass = ManagementFactory.class;
            MethodHandle managementFactoryGetPlatformMxBean = lookup.findStatic(
                    managementFactoryClass,
                    "getPlatformMXBean",
                    MethodType.methodType(PlatformManagedObject.class, Class.class));
            try {
                inst = managementFactoryGetPlatformMxBean.invoke(mxBeanClass);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to create VirtualThreadSchedulerMXBean", t);
            }

            mxBeanGetMountedVirtualThreadCount =
                    lookup.findVirtual(mxBeanClass, "getMountedVirtualThreadCount", MethodType.methodType(int.class));
            mxBeanGetParallelism = lookup.findVirtual(mxBeanClass, "getParallelism", MethodType.methodType(int.class));
            mxBeanGetPoolSize = lookup.findVirtual(mxBeanClass, "getPoolSize", MethodType.methodType(int.class));
            mxBeanGetQueuedVirtualThreadCount =
                    lookup.findVirtual(mxBeanClass, "getQueuedVirtualThreadCount", MethodType.methodType(long.class));
            mxBeanSetParallelism =
                    lookup.findVirtual(mxBeanClass, "setParallelism", MethodType.methodType(void.class, int.class));
        }

        boolean isEnabled() {
            try {
                getParallelism();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public int getMountedVirtualThreadCount() {
            try {
                return (int) mxBeanGetMountedVirtualThreadCount.invoke(inst);
            } catch (Throwable t) {
                throw new SafeRuntimeException(
                        "failed to invoke VirtualThreadSchedulerMXBean#getMountedVirtualThreadCount", t);
            }
        }

        @Override
        public int getParallelism() {
            try {
                return (int) mxBeanGetParallelism.invoke(inst);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke VirtualThreadSchedulerMXBean#getParallelism", t);
            }
        }

        @Override
        public int getPoolSize() {
            try {
                return (int) mxBeanGetPoolSize.invoke(inst);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke VirtualThreadSchedulerMXBean#getPoolSize", t);
            }
        }

        @Override
        public long getQueuedVirtualThreadCount() {
            try {
                return (long) mxBeanGetQueuedVirtualThreadCount.invoke(inst);
            } catch (Throwable t) {
                throw new SafeRuntimeException(
                        "failed to invoke VirtualThreadSchedulerMXBean#getQueuedVirtualThreadCount", t);
            }
        }

        @Override
        public void setParallelism(int size) {
            try {
                mxBeanSetParallelism.invoke(inst, size);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke VirtualThreadSchedulerMXBean#setParallelism", t);
            }
        }
    }

    private static final class LinuxPressureMetricsAccessor implements PressureMetricsAccessor {
        private static final Pattern CGROUP_V2_PATTERN = Pattern.compile("0::(.+)");
        private static final Path PROC_SELF_CGROUP = Paths.get("/proc/self/cgroup");
        private static final Path PROC_PRESSURE_CPU = Paths.get("/proc/pressure/cpu");
        private static final Path PROC_PRESSURE_MEMORY = Paths.get("/proc/pressure/memory");
        private static final Path PROC_PRESSURE_IO = Paths.get("/proc/pressure/io");

        private final Path cpuPressurePath;
        private final Path memoryPressurePath;
        private final Path ioPressurePath;

        LinuxPressureMetricsAccessor() {
            String cgroupPath = detectCgroupPath();
            if (cgroupPath != null) {
                Path cgroupBase = Paths.get("/sys/fs/cgroup", cgroupPath);
                this.cpuPressurePath = tryPath(cgroupBase.resolve("cpu.pressure"), PROC_PRESSURE_CPU);
                this.memoryPressurePath = tryPath(cgroupBase.resolve("memory.pressure"), PROC_PRESSURE_MEMORY);
                this.ioPressurePath = tryPath(cgroupBase.resolve("io.pressure"), PROC_PRESSURE_IO);
                log.debug("Using container-specific PSI paths", SafeArg.of("cgroupPath", cgroupPath));
            } else {
                this.cpuPressurePath = PROC_PRESSURE_CPU;
                this.memoryPressurePath = PROC_PRESSURE_MEMORY;
                this.ioPressurePath = PROC_PRESSURE_IO;
                log.debug("Using system-wide PSI paths");
            }
        }

        boolean isEnabled() {
            return Files.isReadable(cpuPressurePath)
                    || Files.isReadable(memoryPressurePath)
                    || Files.isReadable(ioPressurePath);
        }

        @Override
        public Optional<CpuPressure> getCpuPressure() {
            return Files.isReadable(cpuPressurePath)
                    ? Optional.of(new PsiCpuPressure(cpuPressurePath))
                    : Optional.empty();
        }

        @Override
        public Optional<MemoryPressure> getMemoryPressure() {
            return Files.isReadable(memoryPressurePath)
                    ? Optional.of(new PsiMemoryPressure(memoryPressurePath))
                    : Optional.empty();
        }

        @Override
        public Optional<IoPressure> getIoPressure() {
            return Files.isReadable(ioPressurePath) ? Optional.of(new PsiIoPressure(ioPressurePath)) : Optional.empty();
        }

        @Nullable
        private static String detectCgroupPath() {
            if (!Files.isReadable(PROC_SELF_CGROUP)) {
                return null;
            }

            try (BufferedReader reader = Files.newBufferedReader(PROC_SELF_CGROUP)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = CGROUP_V2_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                }
            } catch (IOException e) {
                log.debug("Failed to read cgroup info", e);
            }
            return null;
        }

        private static Path tryPath(Path containerPath, Path systemPath) {
            return Files.isReadable(containerPath) ? containerPath : systemPath;
        }
    }

    private static final class PsiCpuPressure implements CpuPressure {
        private final Path path;

        PsiCpuPressure(Path path) {
            this.path = path;
        }

        @Override
        public OptionalDouble someAvg10() {
            return readMetric("some", "avg10");
        }

        @Override
        public OptionalDouble someAvg60() {
            return readMetric("some", "avg60");
        }

        @Override
        public OptionalDouble someAvg300() {
            return readMetric("some", "avg300");
        }

        @Override
        public OptionalDouble someTotal() {
            return readMetric("some", "total");
        }

        private OptionalDouble readMetric(String linePrefix, String metricName) {
            Map<String, String> metrics = parsePsiFile(path, linePrefix);
            return parseDouble(metrics.get(metricName));
        }
    }

    private static final class PsiMemoryPressure implements MemoryPressure {
        private final Path path;

        PsiMemoryPressure(Path path) {
            this.path = path;
        }

        @Override
        public OptionalDouble someAvg10() {
            return readMetric("some", "avg10");
        }

        @Override
        public OptionalDouble someAvg60() {
            return readMetric("some", "avg60");
        }

        @Override
        public OptionalDouble someAvg300() {
            return readMetric("some", "avg300");
        }

        @Override
        public OptionalDouble someTotal() {
            return readMetric("some", "total");
        }

        @Override
        public OptionalDouble fullAvg10() {
            return readMetric("full", "avg10");
        }

        @Override
        public OptionalDouble fullAvg60() {
            return readMetric("full", "avg60");
        }

        @Override
        public OptionalDouble fullAvg300() {
            return readMetric("full", "avg300");
        }

        @Override
        public OptionalDouble fullTotal() {
            return readMetric("full", "total");
        }

        private OptionalDouble readMetric(String linePrefix, String metricName) {
            Map<String, String> metrics = parsePsiFile(path, linePrefix);
            return parseDouble(metrics.get(metricName));
        }
    }

    private static final class PsiIoPressure implements IoPressure {
        private final Path path;

        PsiIoPressure(Path path) {
            this.path = path;
        }

        @Override
        public OptionalDouble someAvg10() {
            return readMetric("some", "avg10");
        }

        @Override
        public OptionalDouble someAvg60() {
            return readMetric("some", "avg60");
        }

        @Override
        public OptionalDouble someAvg300() {
            return readMetric("some", "avg300");
        }

        @Override
        public OptionalDouble someTotal() {
            return readMetric("some", "total");
        }

        @Override
        public OptionalDouble fullAvg10() {
            return readMetric("full", "avg10");
        }

        @Override
        public OptionalDouble fullAvg60() {
            return readMetric("full", "avg60");
        }

        @Override
        public OptionalDouble fullAvg300() {
            return readMetric("full", "avg300");
        }

        @Override
        public OptionalDouble fullTotal() {
            return readMetric("full", "total");
        }

        private OptionalDouble readMetric(String linePrefix, String metricName) {
            Map<String, String> metrics = parsePsiFile(path, linePrefix);
            return parseDouble(metrics.get(metricName));
        }
    }

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern EQUALS_PATTERN = Pattern.compile("=");

    @SuppressWarnings("StringSplitter") // Pattern.split is appropriate here; Guava is not a dependency
    private static Map<String, String> parsePsiFile(Path path, String linePrefix) {
        Map<String, String> metrics = new HashMap<>();
        BiConsumer<String, String> metricConsumer = metrics::put;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(linePrefix)) {
                    String[] parts = WHITESPACE_PATTERN.split(line);
                    for (int i = 1; i < parts.length; i++) {
                        parsePressureComponents(parts[i], metricConsumer);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read PSI file", SafeArg.of("path", path), e);
        }
        return metrics;
    }

    static void parsePressureComponents(String input, BiConsumer<String, String> metricConsumer) {
        String[] keyValue = EQUALS_PATTERN.split(input, 2);
        if (keyValue.length == 2) {
            metricConsumer.accept(keyValue[0], keyValue[1]);
        }
    }

    static OptionalDouble parseDouble(String value) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
