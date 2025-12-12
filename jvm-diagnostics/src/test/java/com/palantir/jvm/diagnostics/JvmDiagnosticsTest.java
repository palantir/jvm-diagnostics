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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class JvmDiagnosticsTest {

    @Nested
    class BasicDiagnosticsTest {
        @Test
        void testSafepointTime() {
            assertThat(JvmDiagnostics.totalSafepointTime().get().safepointTimeMilliseconds())
                    .isGreaterThanOrEqualTo(0L);
        }

        @Test
        void testThreadAllocation() {
            assertThat(JvmDiagnostics.threadAllocatedBytes()
                            .get()
                            .getAllocatedBytes(Thread.currentThread().getId()))
                    .isGreaterThan(0L);
        }

        @Test
        void testThreadCpuTime() {
            assertThat(JvmDiagnostics.threadCpuTime()
                            .get()
                            .getCpuTimeNanoseconds(Thread.currentThread().getId()))
                    .isGreaterThan(0L);
        }

        @Test
        void testThreadUserTime() {
            assertThat(JvmDiagnostics.threadUserTime()
                            .get()
                            .getUserTimeNanoseconds(Thread.currentThread().getId()))
                    .isGreaterThan(0L);
        }

        @Test
        void testDnsCacheTtl() {
            assertThat(JvmDiagnostics.dnsCacheTtl().get().getPositiveSeconds()).isEqualTo(30);
            assertThat(JvmDiagnostics.dnsCacheTtl().get().getNegativeSeconds()).isEqualTo(10);
            assertThat(JvmDiagnostics.dnsCacheTtl().get().getStaleSeconds()).isEqualTo(0);
        }
    }

    @Nested
    class VirtualThreadSchedulerDiagnosticsTest {
        private static VirtualThreadSchedulerAccessor accessor;

        @BeforeAll
        static void beforeAll() {
            assumeThat(isJdk24OrHigher()).isTrue();
            Optional<VirtualThreadSchedulerAccessor> maybeAccessor = JvmDiagnostics.virtualThreadScheduler();
            assumeThat(maybeAccessor).isPresent();
            accessor = maybeAccessor.get();
        }

        @Test
        void testGetMountedVirtualThreadCount() throws Exception {
            VirtualThreadFactory factory = new VirtualThreadFactory();
            BusyWaiter waiter = new BusyWaiter(factory);

            try {
                Awaitility.waitAtMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(accessor.getMountedVirtualThreadCount())
                                .isGreaterThan(0));
            } finally {
                waiter.join();
            }
        }

        @Test
        void testGetParallelism() {
            assertThat(accessor.getParallelism()).isGreaterThan(0);
        }

        @Test
        void testGetPoolSize() {
            assertThat(accessor.getPoolSize()).isGreaterThan(0);
        }

        @Test
        void testGetQueuedVirtualThreadCount() throws Exception {
            int nThreads = accessor.getParallelism();
            VirtualThreadFactory factory = new VirtualThreadFactory();
            List<BusyWaiter> waiters = new ArrayList<>();

            try {
                // create enough busy waiters to force at least one to be queued
                for (int i = 0; i < nThreads + 1; i++) {
                    waiters.add(new BusyWaiter(factory));
                }
                assertThat(accessor.getQueuedVirtualThreadCount()).isGreaterThan(0);
            } finally {
                for (BusyWaiter waiter : waiters) {
                    waiter.join();
                }
            }
        }

        @Test
        void testSetParallelism() throws Exception {
            // we need to be careful with this test, as calling setParallelism seems to be mostly a best-effort
            int currentParallelism = accessor.getParallelism();
            try {
                accessor.setParallelism(1);
                VirtualThreadFactory factory = new VirtualThreadFactory();
                BusyWaiter waiter1 = new BusyWaiter(factory);
                BusyWaiter waiter2 = new BusyWaiter(factory);
                try {
                    Awaitility.waitAtMost(Duration.ofSeconds(1))
                            .untilAsserted(() -> assertThat(accessor.getQueuedVirtualThreadCount())
                                    .isGreaterThan(0));
                } finally {
                    waiter1.join();
                    waiter2.join();
                }
            } finally {
                accessor.setParallelism(currentParallelism);
            }
        }

        private static boolean isJdk24OrHigher() {
            return Runtime.version().feature() >= 24;
        }

        // create a virtual thread that busy-waits on a boolean so we guarantee it is mounted to a carrier thread
        private static final class BusyWaiter implements Runnable {
            private volatile boolean done = false;
            private final Thread thread;

            private final AtomicLong counter = new AtomicLong(0);

            private BusyWaiter(VirtualThreadFactory factory) {
                this.thread = factory.start(this);
            }

            private void join() throws InterruptedException {
                done = true;
                thread.join();
            }

            @Override
            public void run() {
                while (!done) {
                    counter.incrementAndGet();
                }
            }
        }

        // necessary until this library is built with language level 21
        private static final class VirtualThreadFactory {
            private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
            private final MethodHandle threadOfVirtual;
            private final MethodHandle startVirtualThread;

            private VirtualThreadFactory() throws ReflectiveOperationException {
                Class<?> ofVirtual = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
                threadOfVirtual = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(ofVirtual));
                startVirtualThread =
                        lookup.findVirtual(ofVirtual, "start", MethodType.methodType(Thread.class, Runnable.class));
            }

            private Thread start(Runnable runnable) {
                try {
                    Object builder = threadOfVirtual.invoke();
                    return (Thread) startVirtualThread.invoke(builder, runnable);
                } catch (Throwable throwable) {
                    throw new RuntimeException("failed to start virtual thread", throwable);
                }
            }
        }
    }
}
