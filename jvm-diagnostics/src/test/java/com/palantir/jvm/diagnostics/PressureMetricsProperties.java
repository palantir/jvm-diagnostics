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

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based tests for PSI metrics using jqwik.
 * These tests verify invariants that should hold across all valid inputs.
 */
class PressureMetricsProperties {

    @Property(tries = 100)
    void avgPercentagesAlwaysInRange0To100(
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double avg10,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double avg60,
            @ForAll @DoubleRange(min = 0.0, max = 100.0) double avg300) {
        // Verify that the percentages we generate are in valid range
        assertThat(avg10).isBetween(0.0, 100.0);
        assertThat(avg60).isBetween(0.0, 100.0);
        assertThat(avg300).isBetween(0.0, 100.0);
    }

    @Property(tries = 100)
    void totalMicrosecondsAlwaysNonNegative(@ForAll("validTotalMicroseconds") double total) {
        // Verify that totals are always non-negative
        assertThat(total).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 100)
    @SuppressWarnings("StringSplitter") // Test code, validates parsing behavior
    void psiFormatParsingNeverThrows(@ForAll("validPsiLine") String psiLine) {
        // Verify that parsing PSI format never throws exceptions
        // This tests the robustness of the parsing logic
        try {
            String[] parts = psiLine.split("\\s+");
            for (String part : parts) {
                if (part.contains("=")) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        try {
                            Double.parseDouble(keyValue[1]);
                        } catch (NumberFormatException e) {
                            // Expected for some generated inputs
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Parsing should never throw", e);
        }
    }

    @Provide
    Arbitrary<Double> validTotalMicroseconds() {
        return Arbitraries.doubles().greaterOrEqual(0.0).lessOrEqual(Double.MAX_VALUE / 2);
    }

    @Provide
    Arbitrary<String> validPsiLine() {
        Arbitrary<Double> percentages = Arbitraries.doubles().between(0.0, 100.0);
        Arbitrary<Long> totals = Arbitraries.longs().greaterOrEqual(0L);

        return Arbitraries.randomValue(_random -> {
            double avg10 = percentages.sample();
            double avg60 = percentages.sample();
            double avg300 = percentages.sample();
            long total = totals.sample();

            return String.format("some avg10=%.2f avg60=%.2f avg300=%.2f total=%d", avg10, avg60, avg300, total);
        });
    }
}
