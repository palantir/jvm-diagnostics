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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.palantir.jvm.diagnostics.virtualthreads.VirtualThreadsDiagnostics.VirtualThreadSchedulerMXBeanSupport;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

// these tests deliberately do not check for absolute values on things like parallelism, pool size, queue size, or
// number of mounted threads. we must be careful, as these metrics are global to the JVM, so any other code that may
// be running concurrently with these tests which potentially use virtual threads will skew the results; instead, we
// mostly just verify that numbers are greater than 0 when we expect they should be. that has the risk of potentially
// producing false-negatives.
//
// an alternative to provide more control would be to fork a new JVM for _each_ test case; that way we can (mostly)
// guarantee that no other virtual threads will be running, but that adds a bit of complexity.
class VirtualThreadsDiagnosticsTest {
    @Test
    void testGetMountedVirtualThreadCount() throws Exception {
        assumeThat(isJdk24OrHigher()).isTrue();

        Optional<VirtualThreadSchedulerMXBeanSupport> mxBean =
                VirtualThreadsDiagnostics.getVirtualThreadSchedulerMXBean();
        assertThat(mxBean).isPresent();

        VirtualThreadFactory factory = new VirtualThreadFactory();
        BusyWaiter waiter = new BusyWaiter(factory);

        try {
            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(mxBean.get().getMountedVirtualThreadCount())
                            .isGreaterThan(0));
        } finally {
            waiter.join();
        }
    }

    @Test
    void testGetParallelism() {
        assumeThat(isJdk24OrHigher()).isTrue();

        Optional<VirtualThreadSchedulerMXBeanSupport> mxBean =
                VirtualThreadsDiagnostics.getVirtualThreadSchedulerMXBean();
        assertThat(mxBean).isPresent();
        assertThat(mxBean.get().getParallelism()).isGreaterThan(0);
    }

    @Test
    void testGetPoolSize() {
        assumeThat(isJdk24OrHigher()).isTrue();

        Optional<VirtualThreadSchedulerMXBeanSupport> mxBean =
                VirtualThreadsDiagnostics.getVirtualThreadSchedulerMXBean();
        assertThat(mxBean).isPresent();
        assertThat(mxBean.get().getPoolSize()).isGreaterThan(0);
    }

    @Test
    void testGetQueuedVirtualThreadCount() throws Exception {
        assumeThat(isJdk24OrHigher()).isTrue();

        Optional<VirtualThreadSchedulerMXBeanSupport> mxBean =
                VirtualThreadsDiagnostics.getVirtualThreadSchedulerMXBean();
        assertThat(mxBean).isPresent();

        int nThreads = mxBean.get().getParallelism();
        VirtualThreadFactory factory = new VirtualThreadFactory();
        List<BusyWaiter> waiters = new ArrayList<>();

        try {
            // create enough busy waiters to force at least one to be queued
            for (int i = 0; i < nThreads + 1; i++) {
                waiters.add(new BusyWaiter(factory));
            }
            assertThat(mxBean.get().getQueuedVirtualThreadCount()).isGreaterThan(0);
        } finally {
            for (BusyWaiter waiter : waiters) {
                waiter.join();
            }
        }
    }

    @Test
    void testSetParallelism() throws Exception {
        // we need to be careful with this test, as calling setParallelism seems to be mostly a best-effort
        assumeThat(isJdk24OrHigher()).isTrue();

        Optional<VirtualThreadSchedulerMXBeanSupport> mxBean =
                VirtualThreadsDiagnostics.getVirtualThreadSchedulerMXBean();
        assertThat(mxBean).isPresent();

        int currentParallelism = mxBean.get().getParallelism();
        try {
            mxBean.get().setParallelism(1);
            VirtualThreadFactory factory = new VirtualThreadFactory();
            BusyWaiter waiter1 = new BusyWaiter(factory);
            BusyWaiter waiter2 = new BusyWaiter(factory);
            try {
                Awaitility.waitAtMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(mxBean.get().getQueuedVirtualThreadCount())
                                .isGreaterThan(0));
            } finally {
                waiter1.join();
                waiter2.join();
            }
        } finally {
            mxBean.get().setParallelism(currentParallelism);
        }
    }

    private static boolean isJdk24OrHigher() {
        return Runtime.version().feature() >= 24;
    }

    // create a virtual thread that busy-waits on a boolean so we guarantee it is mounted to a carrier thread
    private static final class BusyWaiter implements Runnable {
        private volatile boolean done = false;
        private final Thread thread;

        private BusyWaiter(VirtualThreadFactory factory) {
            this.thread = factory.start(this);
        }

        private void join() throws InterruptedException {
            done = true;
            thread.join();
        }

        @Override
        public void run() {
            int counter = 0;
            while (!done) {
                counter++;
            }
        }
    }

    // necessary until this library is built with langauge level 21
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
