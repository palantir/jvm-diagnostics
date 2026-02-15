# CLAUDE.md - Java Project Guidance

## Common Commands

### Build and Test

- `./gradlew build` - Build the entire project
- `./gradlew check` - Run all checks (includes tests, javadoc, checkUnusedDependencies)
- `./gradlew :jvm-diagnostics:test` - Run tests for the jvm-diagnostics module only
- `./gradlew :jvm-diagnostics:check` - Run all checks for the jvm-diagnostics module only
- `./gradlew test --tests "com.palantir.jvm.diagnostics.JvmDiagnosticsTest.BasicDiagnosticsTest.testSafepointTime"` - Run a single test method

### Code Formatting

- `./gradlew format` - Format code according to Palantir Java Format standards
- `./gradlew formatDiff` - Format only chunks that appear in git diff
- `./gradlew spotlessCheck` - Check formatting without making changes
- `./gradlew spotlessApply` - Apply formatting fixes

### Dependency Management

- `./gradlew --write-locks` - Regenerate versions.lock after changing versions.props
- `./gradlew verifyLocks` - Verify that versions.lock is up to date
- `./gradlew checkUnusedDependencies` - Check for unused dependencies
- `./gradlew checkImplicitDependenciesMain` - Ensure all dependencies are explicitly declared

Prefer `./gradlew --quiet` for reduced output verbosity.

## Code Architecture

This is a Gradle-based Java library project (`com.palantir.gt`) with a single module:

- **`jvm-diagnostics/`** - Core module containing all source code under `com.palantir.gt` package

### Build Configuration

- **Java compiler**: 25, **Library target**: 17, **Runtime**: 25, **Daemon target**: 21
- Compilation uses `-Werror` (warnings are errors)
- Uses [gradle-consistent-versions](https://github.com/palantir/gradle-consistent-versions): versions defined in `versions.props`, locked in `versions.lock`

### Key Patterns

- **Immutables**: The `Foo` interface uses `@Value.Immutable` to generate `ImmutableFoo` with builders, hashCode, and toString. The annotation processor runs at compile time.
- **Safe Logging**: Uses Palantir's `SafeLogger` with `SafeArg`/`UnsafeArg` for log safety. The `strict-log-safety` plugin enforces correct usage at compile time.
- **Error Prone + NullAway**: Static analysis runs during compilation to catch common bugs and null safety issues.

### Testing

- JUnit Jupiter + AssertJ
- Tests live in `jvm-diagnostics/src/test/java/`

## Development Guidelines

### Git and Pull Requests

- PR descriptions must follow `.github/PULL_REQUEST_TEMPLATE.md` with sections: Before this PR, After this PR, Testing, Possible downsides?, Are Docs needed?
- NEVER write to external systems (PUT/DELETE/POST) without explicit confirmation


> Derived from [Palantir Baseline Best Practices](https://github.com/palantir/gradle-baseline/tree/develop/docs) and seasoned engineering judgment. This document is the canonical reference for AI agents and human engineers working in this codebase.

---

## 0. Foundational Mindset

**Chesterton's Fence.** Do not change code, patterns, or configurations until you understand *why* they exist. If you cannot articulate the original rationale, investigate before modifying.

**Bias toward boring.** Prefer well-understood, widely-adopted solutions over clever ones. Cleverness is a liability in production; clarity is an asset.

---

## 1. Class & Module Design

### Single Responsibility Principle (SRP)

Every class must have **one reason to change**. If you cannot describe a class's purpose in a single sentence without the word "and", it is doing too much. Split it.

- A class that parses input **and** validates it **and** persists it has three responsibilities. Factor into `InputParser`, `InputValidator`, and `InputRepository`.
- SRP applies at every level: methods do one thing, classes own one concept, packages represent one bounded context, modules encapsulate one deployable capability.
- When in doubt, prefer **more, smaller classes** over fewer, larger ones. Small classes are easier to name, test, review, and replace.

### Cohesion & Coupling

- **High cohesion:** every field and method in a class should relate to its single responsibility. If a subset of fields is only used by a subset of methods, that's a new class waiting to be extracted.
- **Low coupling:** depend on abstractions, not concretions. Accept interfaces in constructors and method parameters. Return concrete types only when the caller genuinely needs them.
- Prefer composition over inheritance. Inheritance creates tight coupling between parent and child; composition allows substitution and independent evolution.

### Interface Segregation

- Do not force clients to depend on methods they don't use. Prefer several small, focused interfaces over one large one.
- A `ReadableRepository` and a `WritableRepository` are better than a `Repository` with 20 methods, half of which throw `UnsupportedOperationException` in read-only implementations.

### Dependency Inversion

- High-level policy classes must not depend on low-level infrastructure classes. Both should depend on interfaces defined at the policy level.
- Constructor injection is the default. Avoid field injection (`@Inject` on fields) - it hides dependencies and defeats immutability.

### Package & Module Organization

- Organize packages by **feature/domain**, not by layer. `com.acme.orders` containing `Order`, `OrderService`, `OrderRepository` is better than `com.acme.model`, `com.acme.service`, `com.acme.repository` each containing fragments of every feature.
- A package's public API should be minimal. Use package-private visibility aggressively - only make classes and methods `public` when they are genuinely part of the module's contract.

---

## 2. Immutability & State

- **Make every field `final` whenever possible.** Make every class immutable where possible.
- When returning collection-typed fields, return an `ImmutableList`, `ImmutableSet`, `ImmutableMap`, or an unmodifiable view. Never leak mutable internal state.
- Fields must be `private` unless they are `static final` and immutable, or annotated `@VisibleForTesting` / `@Rule`.
- **Never use mutable static fields.** Static state couples code to external factors, defeats testability, and makes correctness proofs impossible.
- Avoid `static` methods unless they are trivial, fast, and dependency-free.
- Exception classes are always immutable.

```java
// GOOD: Immutable value object - single responsibility: represent a coordinate
public final class Coordinate {
    private final double lat;
    private final double lon;

    public Coordinate(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double lat() { return lat; }
    public double lon() { return lon; }
}
```

---

## 3. Object Construction & Initialization

- Objects must be fully constructed via their constructor. **No `initialize()` or `setup()` post-construction methods.** If complex initialization is required, use the Factory pattern with appropriate visibility scoping.
- Constructors may allocate new value objects (e.g., `new ArrayList<>()`), but must not perform I/O, start threads, or register callbacks.
- **Use the Builder pattern** when a class has many optional fields or more than ~4 constructor parameters. Builders should accept all required fields as constructor parameters, expose setters for optional fields that return `this`, and provide a `build()` method.
- Declare a class `final` if all of its constructors are `private`.
- Utility classes (all static members) must have a `private` zero-arg constructor.

---

## 4. Libraries & Dependencies

- **Use existing libraries.** Someone has probably written this before.
- Prefer **Guava** over Apache Commons. Prefer Apache Commons over reimplementing from scratch.
- Apache Commons Lang 2.x is deprecated; use version 3.x.
- Prefer JDK standard library constructors over Guava factory methods where equivalent: `new ArrayList<>()` over `Lists.newArrayList()`, `new HashMap<>()` over `Maps.newHashMap()`.
- Use `java.util.Optional` (not `com.google.common.base.Optional`) and `java.util.function.Supplier` (not Guava's).
- If a library *almost* does what you need, submit a patch upstream rather than forking or wrapping.
- Read and know the APIs of standard libraries (Guava, JDK collections, `java.util.stream`, `java.time`).

---

## 5. Concurrency

- **Learn and use `java.util.concurrent`.** Use `Executors`, `Future`, `CompletableFuture`, `ExecutorService`, `ConcurrentHashMap`, and the `java.util.concurrent.atomic` package.
- **Do not use** raw `Thread`, `ThreadGroup`, `synchronized`, `wait`, `notify`, or `notifyAll` unless you can formally prove correctness by referencing the JVM specification-and even then, don't.
- **Do not use Java parallel streams** (`Collection.parallelStream()`). They use the common `ForkJoinPool`, which creates unpredictable thread contention with the rest of the application.
- Write the simplest possible synchronization scheme. Concurrency bugs are transient and nearly impossible to reproduce. Simple code is the only reliable defense.
- Required reading: *Java Concurrency in Practice* by Brian Goetz.

---

## 6. Error Handling & Exceptions

- Use standard log levels consistently: **Fatal** (app cannot continue; page an admin), **Error** (problem occurred but app continues; must be actionable), **Warn** (something unexpected; monitor), **Debug/Trace** (development diagnostics).
- Do not nest `try/catch` blocks-they obfuscate control flow. Restructure into separate methods.
- Be consistent in how methods signal failure. Within the same class, do not mix returning `null`, throwing exceptions, returning empty collections, and returning `Optional.empty()` for the same category of failure.
- Return empty collections rather than `null` when there are no values.
- Use `Optional` for return types when absence is a normal, expected case-not for fields, parameters, or collections.

---

## 7. Logging

- **Use SLF4J exclusively.** Do not import `java.util.logging`, Log4j, Log4j2, or Logback directly.
- Log messages must be compile-time constants (enforced by `Slf4jConstantLogMessage` check). Parameterize with `{}` placeholders; never concatenate strings in log statements.
- Use safe-logging (`com.palantir.logsafe`) for any arguments that may contain sensitive data. Distinguish between `SafeArg` and `UnsafeArg`.

```java
// GOOD
log.info("Processed request", SafeArg.of("requestId", id), UnsafeArg.of("userId", userId));

// BAD - string concatenation, not safe-logging aware
log.info("Processed request " + id + " for user " + userId);
```

---

## 8. API & Method Design

- Each method should do **one thing** (SRP at the method level). If a method name requires "and" - split it.
- Do not overload methods more than once or twice. Excessive overloading confuses callers.
- When a method has many optional arguments, extract it into a function object with the Builder pattern and a `run()` method.
- Avoid ternary operators where they make code hard to follow. Prefer explicit `if/else` for non-trivial conditions.
- Override `Object.equals()` consistently-never define a covariant `equals(MyType other)` without also overriding `equals(Object)`.
- Never instantiate primitive wrapper types directly (`new Boolean(true)`). Use `Boolean.valueOf()` or autoboxing.
- **Design APIs that are hard to misuse.** Prefer types over stringly-typed parameters. An `EmailAddress` value object is better than a `String email` parameter.

---

## 9. Code Style & Formatting

- **Automate formatting.** Use `palantir-java-format` (or the project's configured formatter) via `./gradlew format`. Never debate style in code reviews; let tooling settle it.
- Line length: 120 characters for code, 80 characters for chained method calls.
- Use `com.palantir.baseline` Checkstyle rules as the style baseline. Style violations must not reach code review.
- Commit messages follow the imperative form: `"Fix bug"` not `"Fixed bug"` or `"Fixes bug"`. Capitalize the summary, keep it under 80 characters, wrap body at ~120.

---

## 10. Testing

### Philosophy

Testing is not a phase - it is a design tool. If code is hard to test, it is poorly designed. Difficulty writing a unit test is a signal to refactor: extract a dependency, split a class, simplify a method.

### Unit Tests

- Every public method should have corresponding unit tests. Tests run fast, are isolated, and are deterministic.
- Use **JUnit 5** (`org.junit.jupiter`) as the default test framework. Use **AssertJ** for fluent assertions.
- Structure tests as **Arrange / Act / Assert**. One logical assertion per test method.
- Test class naming: `<ClassUnderTest>Test.java`.

### Positive & Negative Test Cases

Every unit of behavior requires **both** happy-path and failure-path coverage. Skipping negative cases is the #1 source of production surprises.

**Positive tests** verify that the system behaves correctly given valid input and normal conditions:

```java
@Test
void parsesValidCoordinate() {
    Coordinate coord = CoordinateParser.parse("51.5074,-0.1278");
    assertThat(coord.lat()).isCloseTo(51.5074, within(1e-4));
    assertThat(coord.lon()).isCloseTo(-0.1278, within(1e-4));
}
```

**Negative tests** verify that the system fails correctly given invalid input, boundary conditions, and adversarial scenarios:

```java
@Test
void rejectsNullInput() {
    assertThatThrownBy(() -> CoordinateParser.parse(null))
        .isInstanceOf(NullPointerException.class);
}

@Test
void rejectsMalformedInput() {
    assertThatThrownBy(() -> CoordinateParser.parse("not-a-coord"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid coordinate format");
}

@Test
void rejectsOutOfRangeLatitude() {
    assertThatThrownBy(() -> CoordinateParser.parse("91.0,0.0"))
        .isInstanceOf(IllegalArgumentException.class);
}
```

**Negative test case checklist:**

| Category | Examples |
|---|---|
| Null / missing inputs | `null`, empty string, empty collection |
| Boundary values | `Integer.MAX_VALUE`, `0`, `-1`, off-by-one |
| Malformed data | Invalid format, wrong types, truncated input |
| Constraint violations | Out-of-range values, duplicate keys, oversized payloads |
| Concurrency hazards | Concurrent modification, timeout, interruption |
| Resource failures | Unavailable service, full disk, permission denied |
| Security edge cases | Injection strings, overlong inputs, unicode edge cases |

### Property-Based Testing with jqwik

Example-based tests verify specific cases you thought of. **Property-based tests** verify invariants across thousands of cases you didn't think of. Use [jqwik](https://jqwik.net/) for property-based testing wherever the domain has clear invariants.

**When to use property-based tests:**
- Serialization round-trips: `deserialize(serialize(x)) == x`
- Codec/parser symmetry: `decode(encode(x)) == x`
- Algebraic properties: commutativity, associativity, idempotency
- Invariant preservation: "after any sequence of operations, the data structure's invariants still hold"
- Domain constraint enforcement: "no matter what valid input, the output satisfies these constraints"
- Comparison with a reference implementation: "optimized path produces same results as naive path"

**Dependency:**
```groovy
testImplementation 'net.jqwik:jqwik:1.9.+'
```

**Example - round-trip serialization property:**

```java
import net.jqwik.api.*;

class CoordinateSerializationProperties {

    @Property
    void roundTripPreservesCoordinate(@ForAll("validCoordinates") Coordinate original) {
        String serialized = CoordinateSerializer.serialize(original);
        Coordinate deserialized = CoordinateSerializer.deserialize(serialized);
        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<Coordinate> validCoordinates() {
        Arbitrary<Double> lats = Arbitraries.doubles().between(-90.0, 90.0);
        Arbitrary<Double> lons = Arbitraries.doubles().between(-180.0, 180.0);
        return Combinators.combine(lats, lons).as(Coordinate::new);
    }
}
```

**Example - invariant preservation property:**

```java
class SortedSetProperties {

    @Property
    void elementsAlwaysSorted(@ForAll List<@IntRange(min = -1000, max = 1000) Integer> elements) {
        SortedSet<Integer> set = new TreeSet<>(elements);
        List<Integer> asList = new ArrayList<>(set);

        for (int i = 1; i < asList.size(); i++) {
            assertThat(asList.get(i)).isGreaterThan(asList.get(i - 1));
        }
    }

    @Property
    void sizeNeverExceedsInputSize(@ForAll List<Integer> elements) {
        SortedSet<Integer> set = new TreeSet<>(elements);
        assertThat(set.size()).isLessThanOrEqualTo(elements.size());
    }
}
```

**Example - idempotency property:**

```java
class NormalizerProperties {

    @Property
    void normalizationIsIdempotent(@ForAll @StringLength(max = 500) String raw) {
        String once = Normalizer.normalize(raw);
        String twice = Normalizer.normalize(once);
        assertThat(twice).isEqualTo(once);
    }
}
```

**jqwik guidelines:**
- Annotate property test methods with `@Property`, not `@Test`.
- Use `@Provide` methods to define domain-specific `Arbitrary` generators that respect real business constraints. Generic random data is less useful than constrained domain data.
- Set `@Property(tries = 1000)` for fast properties; increase for subtle invariants.
- When jqwik finds a failing case, it **shrinks** it to the minimal reproducer. Capture that minimal case as a separate regression `@Example` (jqwik's equivalent of `@Test`) so it remains in the suite permanently.
- Property tests complement but do not replace example-based tests. Use example tests for known edge cases and documented requirements; use property tests for invariants and exploratory coverage.

### Testing Pyramid - Recommended Ratios

| Layer | Scope | Speed | Volume |
|---|---|---|---|
| **Property tests** | Invariants across generated inputs | Fast | High (per property, thousands of cases) |
| **Unit tests** | Single class/method, positive + negative | Fast | High (bulk of test count) |
| **Integration tests** | Cross-component, external systems | Medium | Moderate |
| **End-to-end tests** | Full system behavior | Slow | Few, focused on critical paths |

### Integration Tests

- Place integration tests in `src/integrationTest/java` with a separate source set.
- Integration tests may touch external systems (databases, HTTP services, filesystems). They run after unit tests (`shouldRunAfter(tasks.named('test'))`).
- Ask: *does this code need integration tests?* Code that interacts with external systems or configuration often can't be adequately tested with unit tests alone.

### General Testing Principles

- Tests must not call each other or depend on execution order.
- Separate **refactoring changes** from **behavior changes**-never in the same commit.
- Run the full test suite locally and verify CI passes *before* requesting review.
- **Test behavior, not implementation.** Tests that assert on internal method calls, field values, or mock interaction counts are brittle. Test the observable output.
- When a bug is found, **write a failing test first**, then fix the code. The test is the proof the bug existed and won't return.

---

## 11. Code Reviews

### Author Responsibilities
- Changes should have a **narrow, well-defined, self-contained scope**.
- If a CR touches more than ~5 files, took longer than 1–2 days to write, or would take more than 20 minutes to review, **split it**.
- Submit only complete, self-reviewed (by diff), and self-tested CRs.
- Separate refactoring from behavior changes. Refactoring CRs must not alter behavior; behavior-changing CRs must not include formatting/refactoring.
- For complex features, use a **stacked CR model**: a primary feature branch with secondary sub-branches, each individually reviewed.

### Reviewer Responsibilities
- Focus on program logic, not style (automated tooling handles style).
- Verify: correctness, edge cases, error handling, test coverage (positive *and* negative), API design, documentation updates.
- Check: are there property-based tests for code with clear invariants (serialization, codecs, data structure operations)?
- Ask: *does this code need integration tests?*
- Check: was external documentation (README, CHANGELOG) updated?
- Verify: does each new/modified class have a single, clear responsibility?
- **Praise** concise, readable, efficient, and elegant code.

---

## 12. Build & Toolchain

- Apply `com.palantir.baseline` to the root project. Individual plugins auto-apply to subprojects.
- Use `com.palantir.consistent-versions` for dependency locking. Lock files must be committed.
- Use `com.palantir.baseline-java-versions` to configure compiler, library target, and distribution target versions.
- Enable Error Prone checks. Treat warnings as errors in CI: `options.compilerArgs += ['-Werror']`.
- Enforce classpath uniqueness via `baseline-class-uniqueness` to prevent duplicate class definitions.
- Use UTF-8 encoding for all compilation tasks.
- Default test heap: 2GB. Default compile heap: 2GB.
- Enable the `-parameters` compiler flag for reflection metadata.
- Use Gradle's `--parallel` mode and incremental compilation.

---

## 13. Dependency Hygiene

- Run `./gradlew why --dependency <dep>` to understand why a version was chosen.
- Run `./gradlew checkClassUniqueness` to detect conflicting JARs on the classpath.
- Prefer project modules over external modules (`baseline-prefer-project-modules`).
- Pin dependency versions in `versions.props`; verify with `versions.lock`.
- Never resolve dependencies at Gradle configuration time.

---

## 14. Performance-Critical Code

- Profile before optimizing. Use JMH for microbenchmarks (`src/jmh/java`).
- Avoid parallel streams (see §5). Use explicit `ExecutorService` for parallelism.
- Prefer primitive arrays and specialized collections (e.g., Eclipse Collections, `int[]`) over boxed collections on hot paths.
- Minimize allocation in tight loops. Reuse buffers where safe.
- Be aware of JIT compilation behavior: small, focused methods inline better.
- Avoid unnecessary autoboxing on critical paths.

---

## 15. Safety & Correctness Checklist

Use this as a pre-commit/pre-review mental checklist:

| Area | Question |
|---|---|
| **SRP** | Does each class have exactly one reason to change? |
| **Immutability** | Are all fields final? Are returned collections unmodifiable? |
| **Nullability** | Are `@Nullable` / `@NonNull` annotations present? Is `Optional` used correctly? |
| **Concurrency** | Is shared mutable state properly synchronized? Are there race conditions? |
| **Error handling** | Are exceptions handled consistently? Are error logs actionable? |
| **Logging** | Are log arguments safe-logged? Are messages constant strings? |
| **Positive tests** | Is the happy path covered for every public method? |
| **Negative tests** | Are null inputs, boundaries, malformed data, and failures tested? |
| **Property tests** | Do serialization, codecs, or invariant-rich code have jqwik properties? |
| **Dependencies** | Are new dependencies justified? Do they introduce classpath conflicts? |
| **API surface** | Is the new API minimal, consistent, and hard to misuse? |
| **Build** | Does `./gradlew check` pass? Are lock files updated? |

---

## 16. Anti-Patterns - Do Not

| Anti-Pattern | Do Instead |
|---|---|
| God class (multiple responsibilities) | Split into focused, single-responsibility classes |
| `Lists.newArrayList()` | `new ArrayList<>()` |
| `Maps.newHashMap()` | `new HashMap<>()` |
| `com.google.common.base.Optional` | `java.util.Optional` |
| Raw `Thread` / `synchronized` | `ExecutorService` / `java.util.concurrent` |
| `Collection.parallelStream()` | Explicit parallelism via `ExecutorService` |
| Mutable static fields | Dependency injection or immutable constants |
| `initialize()` post-construction | Factory pattern or Builder pattern |
| Nested `try/catch` | Extract to separate methods |
| String concatenation in log calls | SLF4J `{}` placeholders + SafeArg/UnsafeArg |
| `new Boolean(true)` | `Boolean.TRUE` / `Boolean.valueOf(true)` |
| Log4j / JUL / Logback direct | SLF4J |
| Apache Commons Lang 2.x | Apache Commons Lang 3.x |
| Style debates in code review | `./gradlew format` |
| Only happy-path tests | Positive *and* negative test cases for every behavior |
| Only example-based tests for invariants | Add jqwik `@Property` tests for round-trips and invariants |
| Package-by-layer (`model`, `service`, `repo`) | Package-by-feature/domain |
| Field injection (`@Inject` on fields) | Constructor injection |
| Fat interfaces | Small, focused interfaces (Interface Segregation) |

---

## References

- [Palantir Baseline Best Practices](https://github.com/palantir/gradle-baseline/tree/develop/docs/best-practices)
- [Palantir Java Style Guide](https://github.com/palantir/gradle-baseline/tree/develop/docs/java-style-guide)
- [Palantir Java Format](https://github.com/palantir/palantir-java-format)
- [jqwik User Guide](https://jqwik.net/docs/current/user-guide.html)
- *Effective Java, 3rd Edition* - Joshua Bloch
- *Java Concurrency in Practice* - Brian Goetz et al.
- *Clean Architecture* - Robert C. Martin (SRP, DIP, ISP)
- [Google Error Prone](https://errorprone.info/)

