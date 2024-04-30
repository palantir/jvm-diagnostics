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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JvmDiagnosticsTest {

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
