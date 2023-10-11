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
import java.util.Optional;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a runtime delegate wrapper around jdk.jfr.consumer.EventStream while allowing consumers to
 * still target older JDKs.
 *
 * EventStreams provides a utility that allows consumers to take advantage of the JFR Streaming API
 * on newer Java runtimes, while still targeting language levels below JDK14 (when the streaming API was
 * introduced).
 *
 * EventStreams provides utility methods for creating a JFR event stream when the runtime allows it.
 * The returned object will be shaped similarly to `jdk.jfr.consumer.EventStream`. When the runtime does not
 * support this, an empty Optional is returned.
 *
 * Note that this wrapper is intended to compile on JDK11 or above, and so doesn't wrap the full
 * set of methods on EventStream; in particular, `onMetadata` is not supported, as it relies on classes
 * only available in Java 16 or above.
 *
 * See also <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html">
 *     jdk.jfr.consumer.EventStream
 *     </a>
 */
public class EventStreams {
    private static final Logger log = LoggerFactory.getLogger(EventStreams.class);

    private EventStreams() {}

    /**
     * Creates a stream from the repository of the current Java Virtual Machine (JVM).
     *
     * See <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html#openRepository()">
     *     https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html#openRepository()
     *     </a>
     *
     * @return an {@link Optional} containing an {@link EventStreamSupport} which delegates to an underlying
     * `jdk.jfr.consumer.EventStream` if the runtime supports it, or {@link Optional#empty()} otherwise.
     */
    public static Optional<EventStreamSupport> openRepository() {
        return isSupported().map(_v -> {
            try {
                return ReflectiveEventStreamSupport.openRepository();
            } catch (Throwable t) {
                log.warn("JFR event stream support is not available", t);
                return null;
            }
        });
    }

    /**
     * Creates an event stream from a disk repository.
     *
     * See <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html#openRepository(java.nio.file.Path)">
     *     https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html#openRepository(java.nio.file.Path)
     *     </a>
     *
     * @param directory location of the disk repository, not null
     * @return an {@link Optional} containing an {@link EventStreamSupport} which delegates to an underlying
     * `jdk.jfr.consumer.EventStream` if the runtime supports it, or {@link Optional#empty()} otherwise.
     */
    public static Optional<EventStreamSupport> openRepository(Path directory) {
        return isSupported().map(_v -> {
            try {
                return ReflectiveEventStreamSupport.openRepository(directory);
            } catch (Throwable t) {
                log.warn("JFR event stream support is not available", t);
                return null;
            }
        });
    }

    /**
     * Creates an event stream from a file.
     *
     * See <a href="https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html#openFile(java.nio.file.Path)">
     *     https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html#openFile(java.nio.file.Path)
     *     </a>
     *
     * @param file location of the file, not null
     * @return an {@link Optional} containing an {@link EventStreamSupport} which delegates to an underlying
     * `jdk.jfr.consumer.EventStream` if the runtime supports it, or {@link Optional#empty()} otherwise.
     */
    public static Optional<EventStreamSupport> openFile(Path file) {
        return isSupported().map(_v -> {
            try {
                return ReflectiveEventStreamSupport.openFile(file);
            } catch (Throwable t) {
                log.warn("JFR event stream support is not available", t);
                return null;
            }
        });
    }

    public interface EventStreamSupport extends AutoCloseable {
        void awaitTermination();

        void awaitTermination(Duration timeout);

        void onClose(Runnable action);

        void onError(Consumer<Throwable> action);

        void onEvent(String eventName, Consumer<RecordedEvent> consumer);

        void onFlush(Runnable action);

        boolean remove(Object action);

        void setEndTime(Instant endTime);

        void setOrdered(boolean ordered);

        void setReuse(boolean reuse);

        void setStartTime(Instant startTime);

        void start();

        void startAsync();
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

    private static final class ReflectiveEventStreamSupport implements EventStreamSupport {
        private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        private final Object eventStream;
        private final MethodHandle eventStreamAwaitTermination;
        private final MethodHandle eventStreamAwaitTerminationWithTimeout;
        private final MethodHandle eventStreamOnClose;
        private final MethodHandle eventStreamOnError;
        private final MethodHandle eventStreamOnEvent;
        private final MethodHandle eventStreamOnFlush;
        private final MethodHandle eventStreamRemove;
        private final MethodHandle eventStreamSetEndTime;
        private final MethodHandle eventStreamSetOrdered;
        private final MethodHandle eventStreamSetReuse;
        private final MethodHandle eventStreamSetStartTime;
        private final MethodHandle eventStreamStart;
        private final MethodHandle eventStreamStartAsync;
        private final MethodHandle eventStreamClose;

        static ReflectiveEventStreamSupport openFile(Path file) throws ReflectiveOperationException {
            Class<?> clazz = lookup.findClass("jdk.jfr.consumer.EventStream");
            return new ReflectiveEventStreamSupport(clazz, openFileInternal(clazz, file));
        }

        static ReflectiveEventStreamSupport openRepository() throws ReflectiveOperationException {
            Class<?> clazz = lookup.findClass("jdk.jfr.consumer.EventStream");
            return new ReflectiveEventStreamSupport(clazz, openRepositoryInternal(clazz));
        }

        static ReflectiveEventStreamSupport openRepository(Path repository) throws ReflectiveOperationException {
            Class<?> clazz = lookup.findClass("jdk.jfr.consumer.EventStream");
            return new ReflectiveEventStreamSupport(clazz, openRepositoryInternal(clazz, repository));
        }

        private static Object openFileInternal(Class<?> clazz, Path file) throws ReflectiveOperationException {
            MethodHandle openFile = lookup.findStatic(clazz, "openFile", MethodType.methodType(clazz, Path.class));
            try {
                return openFile.invoke(file);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#openFile'", t);
            }
        }

        private static Object openRepositoryInternal(Class<?> clazz) throws ReflectiveOperationException {
            MethodHandle openRepository = lookup.findStatic(clazz, "openRepository", MethodType.methodType(clazz));
            try {
                return openRepository.invokeWithArguments();
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#openRepository'", t);
            }
        }

        private static Object openRepositoryInternal(Class<?> clazz, Path repository)
                throws ReflectiveOperationException {
            MethodHandle openRepository =
                    lookup.findStatic(clazz, "openRepository", MethodType.methodType(clazz, Path.class));
            try {
                return openRepository.invoke(repository);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#openRepository'", t);
            }
        }

        private ReflectiveEventStreamSupport(Class<?> clazz, Object eventStream) throws ReflectiveOperationException {
            this.eventStream = eventStream;
            eventStreamAwaitTermination =
                    lookup.findVirtual(clazz, "awaitTermination", MethodType.methodType(void.class));
            eventStreamAwaitTerminationWithTimeout =
                    lookup.findVirtual(clazz, "awaitTermination", MethodType.methodType(void.class, Duration.class));
            eventStreamOnClose =
                    lookup.findVirtual(clazz, "onClose", MethodType.methodType(void.class, Runnable.class));
            eventStreamOnError =
                    lookup.findVirtual(clazz, "onError", MethodType.methodType(void.class, Consumer.class));
            eventStreamOnEvent = lookup.findVirtual(
                    clazz, "onEvent", MethodType.methodType(void.class, String.class, Consumer.class));
            eventStreamOnFlush =
                    lookup.findVirtual(clazz, "onFlush", MethodType.methodType(void.class, Runnable.class));
            eventStreamRemove = lookup.findVirtual(clazz, "remove", MethodType.methodType(boolean.class, Object.class));
            eventStreamSetEndTime =
                    lookup.findVirtual(clazz, "setEndTime", MethodType.methodType(void.class, Instant.class));
            eventStreamSetOrdered =
                    lookup.findVirtual(clazz, "setOrdered", MethodType.methodType(void.class, boolean.class));
            eventStreamSetReuse =
                    lookup.findVirtual(clazz, "setReuse", MethodType.methodType(void.class, boolean.class));
            eventStreamSetStartTime =
                    lookup.findVirtual(clazz, "setStartTime", MethodType.methodType(void.class, Instant.class));
            eventStreamStart = lookup.findVirtual(clazz, "start", MethodType.methodType(void.class));
            eventStreamStartAsync = lookup.findVirtual(clazz, "startAsync", MethodType.methodType(void.class));
            eventStreamClose = lookup.findVirtual(clazz, "close", MethodType.methodType(void.class));
        }

        @Override
        public void awaitTermination() {
            try {
                eventStreamAwaitTermination.invoke(eventStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#awaitTermination'", t);
            }
        }

        @Override
        public void awaitTermination(Duration timeout) {
            try {
                eventStreamAwaitTerminationWithTimeout.invoke(eventStream, timeout);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#awaitTermination'", t);
            }
        }

        @Override
        public void onClose(Runnable action) {
            try {
                eventStreamOnClose.invoke(eventStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#onClose'", t);
            }
        }

        @Override
        public void onError(Consumer<Throwable> action) {
            try {
                eventStreamOnError.invoke(eventStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#onError'", t);
            }
        }

        @Override
        public void onEvent(String eventName, Consumer<RecordedEvent> consumer) {
            try {
                eventStreamOnEvent.invoke(eventStream, eventName, consumer);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#onEvent'", t);
            }
        }

        @Override
        public void onFlush(Runnable action) {
            try {
                eventStreamOnFlush.invoke(eventStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#onFlush'", t);
            }
        }

        @Override
        public boolean remove(Object action) {
            try {
                return (boolean) eventStreamRemove.invoke(eventStream, action);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#remove'", t);
            }
        }

        @Override
        public void setEndTime(Instant endTime) {
            try {
                eventStreamSetEndTime.invoke(eventStream, endTime);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#setEndTime'", t);
            }
        }

        @Override
        public void setOrdered(boolean ordered) {
            try {
                eventStreamSetOrdered.invoke(eventStream, ordered);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#setOrdered'", t);
            }
        }

        @Override
        public void setReuse(boolean reuse) {
            try {
                eventStreamSetReuse.invoke(eventStream, reuse);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#setReuse'", t);
            }
        }

        @Override
        public void setStartTime(Instant startTime) {
            try {
                eventStreamSetStartTime.invoke(eventStream, startTime);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#setStartTime'", t);
            }
        }

        @Override
        public void start() {
            try {
                eventStreamStart.invoke(eventStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#start'", t);
            }
        }

        @Override
        public void startAsync() {
            try {
                eventStreamStartAsync.invoke(eventStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#startAsync'", t);
            }
        }

        @Override
        public void close() {
            try {
                eventStreamClose.invoke(eventStream);
            } catch (Throwable t) {
                throw new RuntimeException("failed to invoke 'EventStream#close'", t);
            }
        }
    }
}
