/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

public interface DnsCacheTtlAccessor {

    /** Returns the number of seconds successful DNS results are cached before making another DNS request. */
    int getPositiveSeconds();

    /** Returns the number of seconds unsuccessful DNS results are cached before making another DNS request. */
    int getNegativeSeconds();

    /**
     * The amount of time a previously successful DNS result will be used beyond {@link #getPositiveSeconds()} if
     * subsequent DNS requests for the same hostname fail.
     * Introduced in Java 21 by <a href=https://bugs.openjdk.org/browse/JDK-8306653>JDK-8306653</a>.
     */
    int getStaleSeconds();
}
