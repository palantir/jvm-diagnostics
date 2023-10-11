# jvm-diagnostics

jvm-diagnostics provides helpful utilities and wrappers for getting diagnostics or profiling data from the running JVM.

## Total Safepoint Time

The `JvmDiagnostics` class provides access to the JVM total safepoint time value without using reflection hacks that fail
in newer java releases.

### Usage

```java
Optional<SafepointTimeAccessor> maybeSafepointAccessor = JvmDiagnostics.totalSafepointTime();
if (maybeSafepointAccessor.isPresent()) {
    SafepointTimeAccessor safepointAccessor = maybeSafepointAccessor.get();
    long safepointMillis = maybeSafepointAccessor.safepointTimeMilliseconds();
    return safepointMillis;
}
return -1L;
```

## JFR Streaming Wrappers

The `EventStreams` and `RecordingStreams` classes provide runtime wrappers around the JFR Streaming APIs 
introduced in Java 14. These wrappers allow you to leverage JFR Streaming when deploying on runtimes that support
it, while maintaining language or runtime compatibility with older JVMs in your codebase.

The implementation uses reflection to delegate to the streaming API classes when the runtime supports it. This
is detected by factory methods in `EventStreams` and `RecordingStreams`, which will return an `Optional` containing
an object shaped like `jdk.jfr.consumer.EventStream` or `jdk.jfr.consumer.RecordingStream`, respectively, or will
return an `Optional.empty()` when the runtime does not support JFR Streaming.

### Usage

`EventStreams` provides a wrapper around `jdk.jfr.consumer.EventStream`. To hook into the JFR repository of the current 
JVM (assuming there is one):

```java
Optional<EventStreamSupport> maybeSupport = EventStreams.openRepository();
if (maybeSupport.isPresent()) {
    EventStreamSupport eventStream = maybeSupport.get();
    eventStream.onEvent("jdk.GarbageCollection", (event) -> {
        System.out.println("JVM Garbage Collection Event");
    });
    eventStream.startAsync();
}
```

`RecordingStreams` provides a similar wrapper around `jdk.jfr.consumer.RecordingStream`, which can be used to create a
JFR recording for the current JVM. Example:

```java
Optional<RecordingStreamSupport> maybeSupport = RecordingStreams.newRecordingStream();
if (maybeSupport.isPresent()) {
    RecordingStreamSupport recordingStream = recordingStreamSupport.get();
    recordingStream.enable("jdk.GarbageCollection");
    recordingStream.onEvent("jdk.GarbageCollection", (event) -> {
        System.out.println("JVM Garbage Collection Event");
    });
    recordingStream.startAsync();
}
```

See the JDK documentation on [EventStream](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html) and [RecordingStream](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingStream.html) for more information.