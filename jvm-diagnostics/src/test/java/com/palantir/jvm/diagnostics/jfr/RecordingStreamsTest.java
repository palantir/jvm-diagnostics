/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.jvm.diagnostics.jfr;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.jvm.diagnostics.jfr.RecordingStreams.RecordingStreamSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class RecordingStreamsTest {

    @Test
    public void testNewRecordingStream() {
        assertThat(RecordingStreams.newRecordingStream()).isPresent();
    }

    @Test
    public void testOnEvent() {
        Optional<RecordingStreamSupport> support = RecordingStreams.newRecordingStream();
        assertThat(support).isPresent();

        support.get().setStartTime(Instant.EPOCH);
        support.get().enable("jdk.ActiveRecording");

        AtomicBoolean seen = new AtomicBoolean(false);
        support.get().onEvent("jdk.ActiveRecording", (_ev) -> seen.compareAndSet(false, true));

        support.get().startAsync();

        Awaitility.waitAtMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(seen.get()).isTrue();
        });
    }
}
