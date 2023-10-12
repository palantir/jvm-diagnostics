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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.EventSettings;
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a runtime delegate wrapper around jdk.jfr.consumer.RecordingStream while allowing consumers to
 * still target older JDKs.
 *
 * RecordingStreams provides a utility that allows consumers to take advantage of the JFR Streaming API
 * on newer Java runtimes, while still targeting language levels below JDK14 (when the streaming API was
 * introduced).
 *
 * RecordingStreams provides utility methods for creating a JFR recording stream when the runtime allows it.
 * The returned object will be shaped similarly to `jdk.jfr.consumer.RecordingStream`. When the runtime does not
 * support this, an empty Optional is returned.
 *
 * See also <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html">
 *     jdk.jfr.consumer.RecordingStream
 *     </a>
 */
public class RecordingStreams {
    private static final Logger log = LoggerFactory.getLogger(RecordingStreams.class);

    private RecordingStreams() {}

    /**
     * Creates an event stream for the current JVM (Java Virtual Machine).
     *
     * See <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html#%3Cinit%3E()">
     *     https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html#%3Cinit%3E()
     *     </a>
     *
     * @return an {@link Optional} containing an {@link RecordingStreamSupport} which delegates to an underlying
     * `jdk.jfr.consumer.RecordingStream` if the runtime supports it, or {@link Optional#empty()} otherwise.
     */
    public static Optional<RecordingStreamSupport> newRecordingStream() {
        return isSupported().map(_v -> {
            try {
                return new ReflectiveRecordingStreamSupport(null);
            } catch (Throwable t) {
                log.warn("JFR recording stream support is not available", t);
                return null;
            }
        });
    }

    /**
     * Creates a recording stream using settings from a configuration.
     *
     * See <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html#%3Cinit%3E(jdk.jfr.Configuration)">
     *     https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html#%3Cinit%3E(jdk.jfr.Configuration)
     *     </a>
     *
     * @param configuration configuration that contains the settings to use, not null
     * @return an {@link Optional} containing an {@link RecordingStreamSupport} which delegates to an underlying
     * `jdk.jfr.consumer.RecordingStream` if the runtime supports it, or {@link Optional#empty()} otherwise.
     */
    public static Optional<RecordingStreamSupport> newRecordingStream(Configuration configuration) {
        return isSupported().map(_v -> {
            try {
                return new ReflectiveRecordingStreamSupport(configuration);
            } catch (Throwable t) {
                log.warn("JFR recording stream support is not available", t);
                return null;
            }
        });
    }

    public interface RecordingStreamSupport extends AutoCloseable {
        void awaitTermination();

        void awaitTermination(Duration timeout);

        EventSettings disable(Class<? extends Event> eventClass);

        EventSettings disable(String name);

        void dump(Path destination);

        EventSettings enable(Class<? extends Event> eventClass);

        EventSettings enable(String name);

        void onClose(Runnable action);

        void onError(Consumer<Throwable> action);

        void onEvent(String eventName, Consumer<RecordedEvent> action);

        void onEvent(Consumer<RecordedEvent> action);

        void onFlush(Runnable action);

        boolean remove(Object action);

        void setEndTime(Instant endTime);

        void setMaxAge(Duration maxAge);

        void setMaxSize(long maxSize);

        void setOrdered(boolean ordered);

        void setReuse(boolean reuse);

        void setSettings(Map<String, String> settings);

        void setStartTime(Instant startTime);

        void start();

        void startAsync();

        @Override
        void close();
    }

    private static Optional<Boolean> isSupported() {
        int featureVersion = Runtime.version().feature();
        if (featureVersion < 14) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "JFR streaming API is not available prior to jdk14; the current version is {}", featureVersion);
            }
            return Optional.empty();
        }
        return Optional.of(true);
    }

    private static final class ReflectiveRecordingStreamSupport implements RecordingStreamSupport {
        private final Object recordingStream;
        private final MethodHandle recordingStreamAwaitTermination;
        private final MethodHandle recordingStreamAwaitTerminationWithTimeout;
        private final MethodHandle recordingStreamDisableClass;
        private final MethodHandle recordingStreamDisableName;
        private final MethodHandle recordingStreamDump;
        private final MethodHandle recordingStreamEnableClass;
        private final MethodHandle recordingStreamEnableName;
        private final MethodHandle recordingStreamOnClose;
        private final MethodHandle recordingStreamOnError;
        private final MethodHandle recordingStreamOnOneEvent;
        private final MethodHandle recordingStreamOnAllEvents;
        private final MethodHandle recordingStreamOnFlush;
        private final MethodHandle recordingStreamRemove;
        private final MethodHandle recordingStreamSetEndTime;
        private final MethodHandle recordingStreamSetMaxAge;
        private final MethodHandle recordingStreamSetMaxSize;
        private final MethodHandle recordingStreamSetOrdered;
        private final MethodHandle recordingStreamSetReuse;
        private final MethodHandle recordingStreamSetSettings;
        private final MethodHandle recordingStreamSetStartTime;
        private final MethodHandle recordingStreamStart;
        private final MethodHandle recordingStreamStartAsync;
        private final MethodHandle recordingStreamClose;

        ReflectiveRecordingStreamSupport(Configuration configuration) throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> clazz = lookup.findClass("jdk.jfr.consumer.RecordingStream");

            try {
                if (configuration != null) {
                    recordingStream = lookup.findConstructor(
                                    clazz, MethodType.methodType(void.class, Configuration.class))
                            .invoke(configuration);
                } else {
                    recordingStream = lookup.findConstructor(clazz, MethodType.methodType(void.class))
                            .invokeWithArguments();
                }
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke RecordingStream constructor", t);
            }

            recordingStreamAwaitTermination =
                    lookup.findVirtual(clazz, "awaitTermination", MethodType.methodType(void.class));
            recordingStreamAwaitTerminationWithTimeout =
                    lookup.findVirtual(clazz, "awaitTermination", MethodType.methodType(void.class, Duration.class));
            recordingStreamDisableClass =
                    lookup.findVirtual(clazz, "disable", MethodType.methodType(EventSettings.class, Class.class));
            recordingStreamDisableName =
                    lookup.findVirtual(clazz, "disable", MethodType.methodType(EventSettings.class, String.class));
            recordingStreamDump = lookup.findVirtual(clazz, "dump", MethodType.methodType(void.class, Path.class));
            recordingStreamEnableClass =
                    lookup.findVirtual(clazz, "enable", MethodType.methodType(EventSettings.class, Class.class));
            recordingStreamEnableName =
                    lookup.findVirtual(clazz, "enable", MethodType.methodType(EventSettings.class, String.class));
            recordingStreamOnClose =
                    lookup.findVirtual(clazz, "onClose", MethodType.methodType(void.class, Runnable.class));
            recordingStreamOnError =
                    lookup.findVirtual(clazz, "onError", MethodType.methodType(void.class, Consumer.class));
            recordingStreamOnOneEvent = lookup.findVirtual(
                    clazz, "onEvent", MethodType.methodType(void.class, String.class, Consumer.class));
            recordingStreamOnAllEvents =
                    lookup.findVirtual(clazz, "onEvent", MethodType.methodType(void.class, Consumer.class));
            recordingStreamOnFlush =
                    lookup.findVirtual(clazz, "onFlush", MethodType.methodType(void.class, Runnable.class));
            recordingStreamRemove =
                    lookup.findVirtual(clazz, "remove", MethodType.methodType(boolean.class, Object.class));
            recordingStreamSetEndTime =
                    lookup.findVirtual(clazz, "setEndTime", MethodType.methodType(void.class, Instant.class));
            recordingStreamSetMaxAge =
                    lookup.findVirtual(clazz, "setMaxAge", MethodType.methodType(void.class, Duration.class));
            recordingStreamSetMaxSize =
                    lookup.findVirtual(clazz, "setMaxSize", MethodType.methodType(void.class, long.class));
            recordingStreamSetOrdered =
                    lookup.findVirtual(clazz, "setOrdered", MethodType.methodType(void.class, boolean.class));
            recordingStreamSetReuse =
                    lookup.findVirtual(clazz, "setReuse", MethodType.methodType(void.class, boolean.class));
            recordingStreamSetSettings =
                    lookup.findVirtual(clazz, "setSettings", MethodType.methodType(void.class, Map.class));
            recordingStreamSetStartTime =
                    lookup.findVirtual(clazz, "setStartTime", MethodType.methodType(void.class, Instant.class));
            recordingStreamStart = lookup.findVirtual(clazz, "start", MethodType.methodType(void.class));
            recordingStreamStartAsync = lookup.findVirtual(clazz, "startAsync", MethodType.methodType(void.class));
            recordingStreamClose = lookup.findVirtual(clazz, "close", MethodType.methodType(void.class));
        }

        @Override
        public void awaitTermination() {
            try {
                recordingStreamAwaitTermination.invoke(recordingStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#awaitTermination'", t);
            }
        }

        @Override
        public void awaitTermination(Duration timeout) {
            try {
                recordingStreamAwaitTerminationWithTimeout.invoke(recordingStream, timeout);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#awaitTermination'", t);
            }
        }

        @Override
        public EventSettings disable(Class<? extends Event> eventClass) {
            try {
                return (EventSettings) recordingStreamDisableClass.invoke(recordingStream, eventClass);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#disable'", t);
            }
        }

        @Override
        public EventSettings disable(String name) {
            try {
                return (EventSettings) recordingStreamDisableName.invoke(recordingStream, name);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#disable'", t);
            }
        }

        @Override
        public void dump(Path destination) {
            try {
                recordingStreamDump.invoke(recordingStream, destination);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#dump'", t);
            }
        }

        @Override
        public EventSettings enable(Class<? extends Event> eventClass) {
            try {
                return (EventSettings) recordingStreamEnableClass.invoke(recordingStream, eventClass);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#enable'", t);
            }
        }

        @Override
        public EventSettings enable(String name) {
            try {
                return (EventSettings) recordingStreamEnableName.invoke(recordingStream, name);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#enable'", t);
            }
        }

        @Override
        public void onClose(Runnable action) {
            try {
                recordingStreamOnClose.invoke(recordingStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#onClose'", t);
            }
        }

        @Override
        public void onError(Consumer<Throwable> action) {
            try {
                recordingStreamOnError.invoke(recordingStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#onError'", t);
            }
        }

        @Override
        public void onEvent(Consumer<RecordedEvent> action) {
            try {
                recordingStreamOnAllEvents.invoke(recordingStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#onEvent'", t);
            }
        }

        @Override
        public void onEvent(String eventName, Consumer<RecordedEvent> consumer) {
            try {
                recordingStreamOnOneEvent.invoke(recordingStream, eventName, consumer);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#onEvent'", t);
            }
        }

        @Override
        public void onFlush(Runnable action) {
            try {
                recordingStreamOnFlush.invoke(recordingStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#onFlush'", t);
            }
        }

        @Override
        public boolean remove(Object action) {
            try {
                return (boolean) recordingStreamRemove.invoke(recordingStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#remove'", t);
            }
        }

        @Override
        public void setEndTime(Instant endTime) {
            try {
                recordingStreamSetEndTime.invoke(recordingStream, endTime);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setEndTime'", t);
            }
        }

        @Override
        public void setMaxAge(Duration maxAge) {
            try {
                recordingStreamSetMaxAge.invoke(recordingStream, maxAge);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setMaxAge'", t);
            }
        }

        @Override
        public void setMaxSize(long maxSize) {
            try {
                recordingStreamSetMaxSize.invoke(recordingStream, maxSize);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setMaxSize'", t);
            }
        }

        @Override
        public void setOrdered(boolean ordered) {
            try {
                recordingStreamSetOrdered.invoke(recordingStream, ordered);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setOrdered'", t);
            }
        }

        @Override
        public void setReuse(boolean reuse) {
            try {
                recordingStreamSetReuse.invoke(recordingStream, reuse);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setReuse'", t);
            }
        }

        @Override
        public void setSettings(Map<String, String> settings) {
            try {
                recordingStreamSetSettings.invoke(recordingStream, settings);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setSettings'", t);
            }
        }

        @Override
        public void setStartTime(Instant startTime) {
            try {
                recordingStreamSetStartTime.invoke(recordingStream, startTime);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#setStartTime'", t);
            }
        }

        @Override
        public void startAsync() {
            try {
                recordingStreamStartAsync.invoke(recordingStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#startAsync'", t);
            }
        }

        @Override
        public void start() {
            try {
                recordingStreamStart.invoke(recordingStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#start'", t);
            }
        }

        @Override
        public void close() {
            try {
                recordingStreamClose.invoke(recordingStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'RecordingStream#close'", t);
            }
        }
    }
}
