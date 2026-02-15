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

import java.util.Optional;

/**
 * Provides access to Linux Pressure Stall Information (PSI) metrics.
 * <p>
 * PSI tracks the time that tasks spend waiting for CPU, memory, or I/O resources.
 * This accessor automatically detects if running in a container environment and
 * prefers container-specific metrics when available, falling back to system-wide
 * metrics otherwise.
 * <p>
 * On non-Linux systems or when PSI is not available, the accessor methods return
 * empty {@link Optional} values.
 *
 * @see <a href="https://docs.kernel.org/accounting/psi.html">Linux PSI Documentation</a>
 */
public interface PressureMetricsAccessor {

    /**
     * Returns CPU pressure metrics. Automatically detects container environment and
     * returns container-specific metrics when available, otherwise system-wide metrics.
     * <p>
     * CPU pressure indicates time spent waiting for CPU resources.
     *
     * @return CPU pressure metrics, or empty if unavailable
     */
    Optional<CpuPressure> getCpuPressure();

    /**
     * Returns memory pressure metrics. Automatically detects container environment and
     * returns container-specific metrics when available, otherwise system-wide metrics.
     * <p>
     * Memory pressure indicates time spent waiting for memory resources or reclaiming pages.
     *
     * @return memory pressure metrics, or empty if unavailable
     */
    Optional<MemoryPressure> getMemoryPressure();

    /**
     * Returns I/O pressure metrics. Automatically detects container environment and
     * returns container-specific metrics when available, otherwise system-wide metrics.
     * <p>
     * I/O pressure indicates time spent waiting for I/O operations to complete.
     *
     * @return I/O pressure metrics, or empty if unavailable
     */
    Optional<IoPressure> getIoPressure();
}
