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

import java.util.OptionalDouble;

/**
 * CPU pressure metrics from Linux Pressure Stall Information (PSI).
 * <p>
 * These metrics track the percentage of time that at least one task was stalled
 * waiting for CPU resources. CPU pressure only has "some" metrics (no "full" metrics)
 * because the CPU is always running something.
 */
public interface CpuPressure {

    /**
     * Percentage of time at least one task was stalled on CPU over a 10-second window.
     *
     * @return percentage (0-100), or empty if unavailable
     */
    OptionalDouble someAvg10();

    /**
     * Percentage of time at least one task was stalled on CPU over a 60-second window.
     *
     * @return percentage (0-100), or empty if unavailable
     */
    OptionalDouble someAvg60();

    /**
     * Percentage of time at least one task was stalled on CPU over a 300-second window.
     *
     * @return percentage (0-100), or empty if unavailable
     */
    OptionalDouble someAvg300();

    /**
     * Cumulative microseconds at least one task was stalled on CPU.
     *
     * @return total microseconds, or empty if unavailable
     */
    OptionalDouble someTotal();
}
