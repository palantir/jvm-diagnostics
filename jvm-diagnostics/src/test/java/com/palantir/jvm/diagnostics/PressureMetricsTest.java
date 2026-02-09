/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PressureMetricsTest {

    private static final Path PROC_PRESSURE_CPU = Paths.get("/proc/pressure/cpu");
    private static final Path PROC_PRESSURE_MEMORY = Paths.get("/proc/pressure/memory");
    private static final Path PROC_PRESSURE_IO = Paths.get("/proc/pressure/io");

    @Nested
    class PositiveTests {

        @Test
        void pressureMetricsAccessorIsAvailableOnLinuxWithPsi() {
            assumeThat(Files.isReadable(PROC_PRESSURE_CPU)
                            || Files.isReadable(PROC_PRESSURE_MEMORY)
                            || Files.isReadable(PROC_PRESSURE_IO))
                    .describedAs("PSI files should be readable on Linux with PSI support")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();

            assertThat(accessor)
                    .describedAs("Accessor should be present on Linux with PSI")
                    .isPresent();
        }

        @Test
        void cpuPressureMetricsAreAccessible() {
            assumeThat(Files.isReadable(PROC_PRESSURE_CPU))
                    .describedAs("CPU pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            Optional<CpuPressure> cpuPressure = accessor.get().getCpuPressure();

            assertThat(cpuPressure)
                    .describedAs("CPU pressure should be available")
                    .isPresent();

            CpuPressure cpu = cpuPressure.get();
            assertThat(cpu.someAvg10())
                    .describedAs("CPU someAvg10 should be present")
                    .isPresent();
            assertThat(cpu.someAvg60())
                    .describedAs("CPU someAvg60 should be present")
                    .isPresent();
            assertThat(cpu.someAvg300())
                    .describedAs("CPU someAvg300 should be present")
                    .isPresent();
            assertThat(cpu.someTotal())
                    .describedAs("CPU someTotal should be present")
                    .isPresent();
        }

        @Test
        void cpuPressureValuesAreReasonable() {
            assumeThat(Files.isReadable(PROC_PRESSURE_CPU))
                    .describedAs("CPU pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            Optional<CpuPressure> cpuPressure = accessor.get().getCpuPressure();
            assumeThat(cpuPressure).isPresent();

            CpuPressure cpu = cpuPressure.get();

            // Percentages should be 0-100
            cpu.someAvg10().ifPresent(value -> assertThat(value)
                    .describedAs("CPU someAvg10 should be 0-100")
                    .isBetween(0.0, 100.0));
            cpu.someAvg60().ifPresent(value -> assertThat(value)
                    .describedAs("CPU someAvg60 should be 0-100")
                    .isBetween(0.0, 100.0));
            cpu.someAvg300().ifPresent(value -> assertThat(value)
                    .describedAs("CPU someAvg300 should be 0-100")
                    .isBetween(0.0, 100.0));

            // Total microseconds should be non-negative
            cpu.someTotal().ifPresent(value -> assertThat(value)
                    .describedAs("CPU someTotal should be non-negative")
                    .isGreaterThanOrEqualTo(0.0));
        }

        @Test
        void memoryPressureMetricsAreAccessible() {
            assumeThat(Files.isReadable(PROC_PRESSURE_MEMORY))
                    .describedAs("Memory pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            Optional<MemoryPressure> memoryPressure = accessor.get().getMemoryPressure();

            assertThat(memoryPressure)
                    .describedAs("Memory pressure should be available")
                    .isPresent();

            MemoryPressure memory = memoryPressure.get();
            assertThat(memory.someAvg10())
                    .describedAs("Memory someAvg10 should be present")
                    .isPresent();
            assertThat(memory.someAvg60())
                    .describedAs("Memory someAvg60 should be present")
                    .isPresent();
            assertThat(memory.someAvg300())
                    .describedAs("Memory someAvg300 should be present")
                    .isPresent();
            assertThat(memory.someTotal())
                    .describedAs("Memory someTotal should be present")
                    .isPresent();
            assertThat(memory.fullAvg10())
                    .describedAs("Memory fullAvg10 should be present")
                    .isPresent();
            assertThat(memory.fullAvg60())
                    .describedAs("Memory fullAvg60 should be present")
                    .isPresent();
            assertThat(memory.fullAvg300())
                    .describedAs("Memory fullAvg300 should be present")
                    .isPresent();
            assertThat(memory.fullTotal())
                    .describedAs("Memory fullTotal should be present")
                    .isPresent();
        }

        @Test
        void memoryPressureValuesAreReasonable() {
            assumeThat(Files.isReadable(PROC_PRESSURE_MEMORY))
                    .describedAs("Memory pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            Optional<MemoryPressure> memoryPressure = accessor.get().getMemoryPressure();
            assumeThat(memoryPressure).isPresent();

            MemoryPressure memory = memoryPressure.get();

            // Percentages should be 0-100
            memory.someAvg10().ifPresent(value -> assertThat(value)
                    .describedAs("Memory someAvg10 should be 0-100")
                    .isBetween(0.0, 100.0));
            memory.someAvg60().ifPresent(value -> assertThat(value)
                    .describedAs("Memory someAvg60 should be 0-100")
                    .isBetween(0.0, 100.0));
            memory.someAvg300().ifPresent(value -> assertThat(value)
                    .describedAs("Memory someAvg300 should be 0-100")
                    .isBetween(0.0, 100.0));
            memory.fullAvg10().ifPresent(value -> assertThat(value)
                    .describedAs("Memory fullAvg10 should be 0-100")
                    .isBetween(0.0, 100.0));
            memory.fullAvg60().ifPresent(value -> assertThat(value)
                    .describedAs("Memory fullAvg60 should be 0-100")
                    .isBetween(0.0, 100.0));
            memory.fullAvg300().ifPresent(value -> assertThat(value)
                    .describedAs("Memory fullAvg300 should be 0-100")
                    .isBetween(0.0, 100.0));

            // Totals should be non-negative
            memory.someTotal().ifPresent(value -> assertThat(value)
                    .describedAs("Memory someTotal should be non-negative")
                    .isGreaterThanOrEqualTo(0.0));
            memory.fullTotal().ifPresent(value -> assertThat(value)
                    .describedAs("Memory fullTotal should be non-negative")
                    .isGreaterThanOrEqualTo(0.0));
        }

        @Test
        void ioPressureMetricsAreAccessible() {
            assumeThat(Files.isReadable(PROC_PRESSURE_IO))
                    .describedAs("I/O pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            Optional<IoPressure> ioPressure = accessor.get().getIoPressure();

            assertThat(ioPressure)
                    .describedAs("I/O pressure should be available")
                    .isPresent();

            IoPressure io = ioPressure.get();
            assertThat(io.someAvg10())
                    .describedAs("I/O someAvg10 should be present")
                    .isPresent();
            assertThat(io.someAvg60())
                    .describedAs("I/O someAvg60 should be present")
                    .isPresent();
            assertThat(io.someAvg300())
                    .describedAs("I/O someAvg300 should be present")
                    .isPresent();
            assertThat(io.someTotal())
                    .describedAs("I/O someTotal should be present")
                    .isPresent();
            assertThat(io.fullAvg10())
                    .describedAs("I/O fullAvg10 should be present")
                    .isPresent();
            assertThat(io.fullAvg60())
                    .describedAs("I/O fullAvg60 should be present")
                    .isPresent();
            assertThat(io.fullAvg300())
                    .describedAs("I/O fullAvg300 should be present")
                    .isPresent();
            assertThat(io.fullTotal())
                    .describedAs("I/O fullTotal should be present")
                    .isPresent();
        }

        @Test
        void ioPressureValuesAreReasonable() {
            assumeThat(Files.isReadable(PROC_PRESSURE_IO))
                    .describedAs("I/O pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            Optional<IoPressure> ioPressure = accessor.get().getIoPressure();
            assumeThat(ioPressure).isPresent();

            IoPressure io = ioPressure.get();

            // Percentages should be 0-100
            io.someAvg10().ifPresent(value -> assertThat(value)
                    .describedAs("I/O someAvg10 should be 0-100")
                    .isBetween(0.0, 100.0));
            io.someAvg60().ifPresent(value -> assertThat(value)
                    .describedAs("I/O someAvg60 should be 0-100")
                    .isBetween(0.0, 100.0));
            io.someAvg300().ifPresent(value -> assertThat(value)
                    .describedAs("I/O someAvg300 should be 0-100")
                    .isBetween(0.0, 100.0));
            io.fullAvg10().ifPresent(value -> assertThat(value)
                    .describedAs("I/O fullAvg10 should be 0-100")
                    .isBetween(0.0, 100.0));
            io.fullAvg60().ifPresent(value -> assertThat(value)
                    .describedAs("I/O fullAvg60 should be 0-100")
                    .isBetween(0.0, 100.0));
            io.fullAvg300().ifPresent(value -> assertThat(value)
                    .describedAs("I/O fullAvg300 should be 0-100")
                    .isBetween(0.0, 100.0));

            // Totals should be non-negative
            io.someTotal().ifPresent(value -> assertThat(value)
                    .describedAs("I/O someTotal should be non-negative")
                    .isGreaterThanOrEqualTo(0.0));
            io.fullTotal().ifPresent(value -> assertThat(value)
                    .describedAs("I/O fullTotal should be non-negative")
                    .isGreaterThanOrEqualTo(0.0));
        }

        @Test
        void accessorCanBeReused() {
            assumeThat(Files.isReadable(PROC_PRESSURE_CPU))
                    .describedAs("CPU pressure file should be readable")
                    .isTrue();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();
            assumeThat(accessor).isPresent();

            // Read metrics multiple times
            Optional<CpuPressure> cpu1 = accessor.get().getCpuPressure();
            Optional<CpuPressure> cpu2 = accessor.get().getCpuPressure();

            assertThat(cpu1).isPresent();
            assertThat(cpu2).isPresent();

            // Verify we can read values from both instances
            OptionalDouble value1 = cpu1.get().someAvg10();
            OptionalDouble value2 = cpu2.get().someAvg10();

            assertThat(value1).isPresent();
            assertThat(value2).isPresent();
        }
    }

    @Nested
    class NegativeTests {

        @Test
        void accessorReturnsEmptyOnNonLinuxPlatform() {
            boolean anyPsiFileExists = Files.isReadable(PROC_PRESSURE_CPU)
                    || Files.isReadable(PROC_PRESSURE_MEMORY)
                    || Files.isReadable(PROC_PRESSURE_IO);

            assumeThat(anyPsiFileExists)
                    .describedAs("This test should only run when PSI files are not available")
                    .isFalse();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();

            assertThat(accessor)
                    .describedAs("Accessor should be empty when PSI is not available")
                    .isEmpty();
        }

        @Test
        void cpuPressureReturnsEmptyWhenFileNotReadable() {
            assumeThat(Files.isReadable(PROC_PRESSURE_CPU))
                    .describedAs("This test should only run when CPU pressure file is not readable")
                    .isFalse();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();

            // If accessor is present (other PSI files exist), CPU pressure should be empty
            accessor.ifPresent(a -> {
                Optional<CpuPressure> cpuPressure = a.getCpuPressure();
                assertThat(cpuPressure)
                        .describedAs("CPU pressure should be empty when file is not readable")
                        .isEmpty();
            });
        }

        @Test
        void memoryPressureReturnsEmptyWhenFileNotReadable() {
            assumeThat(Files.isReadable(PROC_PRESSURE_MEMORY))
                    .describedAs("This test should only run when memory pressure file is not readable")
                    .isFalse();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();

            // If accessor is present (other PSI files exist), memory pressure should be empty
            accessor.ifPresent(a -> {
                Optional<MemoryPressure> memoryPressure = a.getMemoryPressure();
                assertThat(memoryPressure)
                        .describedAs("Memory pressure should be empty when file is not readable")
                        .isEmpty();
            });
        }

        @Test
        void ioPressureReturnsEmptyWhenFileNotReadable() {
            assumeThat(Files.isReadable(PROC_PRESSURE_IO))
                    .describedAs("This test should only run when I/O pressure file is not readable")
                    .isFalse();

            Optional<PressureMetricsAccessor> accessor = JvmDiagnostics.pressureMetrics();

            // If accessor is present (other PSI files exist), I/O pressure should be empty
            accessor.ifPresent(a -> {
                Optional<IoPressure> ioPressure = a.getIoPressure();
                assertThat(ioPressure)
                        .describedAs("I/O pressure should be empty when file is not readable")
                        .isEmpty();
            });
        }
    }
}
