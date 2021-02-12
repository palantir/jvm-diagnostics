jvm-diagnostics
===============
Provides access to the JVM total safepoint time value without using reflection hacks that fail
in newer java releases.

Usage
-----
```java
Optional<SafepointTimeAccessor> maybeSafepointAccessor = JvmDiagnostics.totalSafepointTime();
if (maybeSafepointAccessor.isPresent()) {
    SafepointTimeAccessor safepointAccessor = maybeSafepointAccessor.get();
    long safepointMillis = maybeSafepointAccessor.safepointTimeMilliseconds();
    return safepointMillis;
}
return -1L;
```

Gradle Tasks
------------
`./gradlew tasks` - to get the list of gradle tasks


Start Developing
----------------
Run one of the following commands:

* `./gradlew idea` for IntelliJ
* `./gradlew eclipse` for Eclipse
