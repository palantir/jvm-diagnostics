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

/**
 * Property-based tests for PSI metrics using jqwik.
 * These tests verify invariants that should hold across all valid inputs.
 */
class PressureMetricsPropertiesTest {

    @Property(tries = 10000)
    @SuppressWarnings("StringSplitter") // Test code, validates parsing behavior
    void psiFormatParsingNeverThrows(@ForAll("validPsiLine") String psiLine) {
        // Verify that parsing PSI format never throws exceptions
        // This tests the robustness of the parsing logic
        for (String part : psiLine.split("\\s+")) {
            if (part.contains("=")) {
                JvmDiagnostics.parsePressureComponents(part, (key, value) -> {
                    assertThat(key).isNotNull().isNotEmpty().containsAnyOf("avg10", "avg60", "avg300", "total");
                    assertThat(value).isNotNull().isNotEmpty().satisfies(v -> assertThat(Double.parseDouble(v))
                            .isNotNaN());
                    assertThat(JvmDiagnostics.parseDouble(value)).isNotNull().isNotEmpty();
                });
            }
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
