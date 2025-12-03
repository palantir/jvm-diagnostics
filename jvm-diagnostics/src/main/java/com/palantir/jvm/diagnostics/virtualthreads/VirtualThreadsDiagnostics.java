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

package com.palantir.jvm.diagnostics.virtualthreads;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a runtime delegate wrapper around various JMX-based diagnostics for virtual threads.
 * <p>
 * Currently, this implementation includes wrappers for the following:
 * <ul>
 *     <li><a href="https://docs.oracle.com/en/java/javase/24/docs/api/jdk.management/jdk/management/VirtualThreadSchedulerMXBean.html">jdk.management.VirtualThreadSchedulerMXBean</a> (available on JDK24+)</li>
 * </ul>
 */
@SuppressWarnings("AbbreviationAsWordInName")
public class VirtualThreadsDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(VirtualThreadsDiagnostics.class);

    private static final Optional<VirtualThreadSchedulerMXBeanSupport> VIRTUAL_THREAD_SCHEDULER_MX_BEAN_SUPPORT =
            maybeInitializeVirtualThreadMXBeanSupport();

    /**
     * Create a VirtualThreadSchedulerMXBean, as if by calling
     * {@code ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class)}.
     *
     * @return An {@link Optional} containing an instance of a reflective wrapper around VirtualThreadSchedulerMXBean
     * if it is available on this JVM; otherwise, {@link Optional#empty()}.
     */
    public static Optional<VirtualThreadSchedulerMXBeanSupport> getVirtualThreadSchedulerMXBean() {
        return VIRTUAL_THREAD_SCHEDULER_MX_BEAN_SUPPORT;
    }

    private static Optional<VirtualThreadSchedulerMXBeanSupport> maybeInitializeVirtualThreadMXBeanSupport() {
        int featureVersion = Runtime.version().feature();
        if (featureVersion < 24) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "VirtualThreadSchedulerMXBean is not available prior to JDK 24; the current version is {}",
                        featureVersion);
            }
            return Optional.empty();
        }
        try {
            return Optional.of(new ReflectiveVirtualThreadSchedulerMXBeanSupport());
        } catch (Throwable t) {
            log.warn("Virtual thread diagnostics support is not available", t);
            return Optional.empty();
        }
    }

    private VirtualThreadsDiagnostics() {}

    public interface VirtualThreadSchedulerMXBeanSupport {
        int getMountedVirtualThreadCount();

        int getParallelism();

        int getPoolSize();

        long getQueuedVirtualThreadCount();

        void setParalleism(int size);
    }

    private static final class ReflectiveVirtualThreadSchedulerMXBeanSupport
            implements VirtualThreadSchedulerMXBeanSupport {

        private final MethodHandle mxBeanGetMountedVirtualThreadCount;
        private final MethodHandle mxBeanGetParallelism;
        private final MethodHandle mxBeanGetPoolSize;
        private final MethodHandle mxBeanGetQueuedVirtualThreadCount;
        private final MethodHandle mxBeanSetParallelism;
        private final Object inst;

        ReflectiveVirtualThreadSchedulerMXBeanSupport() throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> mxBeanClass = lookup.findClass("jdk.management.VirtualThreadSchedulerMXBean");
            Class<?> managementFactoryClass = ManagementFactory.class;
            MethodHandle managementFactoryGetPlatformMXBean = lookup.findStatic(
                    managementFactoryClass,
                    "getPlatformMXBean",
                    MethodType.methodType(PlatformManagedObject.class, Class.class));
            try {
                inst = managementFactoryGetPlatformMXBean.invoke(mxBeanClass);
            } catch (Throwable t) {
                throw new RuntimeException("failed to create VirtualThreadSchedulerMXBean", t);
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

        @Override
        public int getMountedVirtualThreadCount() {
            try {
                return (int) mxBeanGetMountedVirtualThreadCount.invoke(inst);
            } catch (Throwable t) {
                throw new RuntimeException(
                        "failed to invoke VirtualThreadSchedulerMXBean#getMountedVirtualThreadCount", t);
            }
        }

        @Override
        public int getParallelism() {
            try {
                return (int) mxBeanGetParallelism.invoke(inst);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke VirtualThreadSchedulerMXBean#getParallelism", t);
            }
        }

        @Override
        public int getPoolSize() {
            try {
                return (int) mxBeanGetPoolSize.invoke(inst);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke VirtualThreadSchedulerMXBean#getPoolSize", t);
            }
        }

        @Override
        public long getQueuedVirtualThreadCount() {
            try {
                return (long) mxBeanGetQueuedVirtualThreadCount.invoke(inst);
            } catch (Throwable t) {
                throw new RuntimeException(
                        "failed to invoke VirtualThreadSchedulerMXBean#getQueuedVirtualThreadCount", t);
            }
        }

        @Override
        public void setParalleism(int size) {
            try {
                mxBeanSetParallelism.invoke(inst, size);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke VirtualThreadSchedulerMXBean#setParallelism", t);
            }
        }
    }
}
