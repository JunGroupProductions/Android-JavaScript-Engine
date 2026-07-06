# JSEngine Future Releases Plan

This document outlines the roadmap for future JSEngine releases with emphasis on **performance optimization** and **ANR prevention**.

> **Versioning note:** `0.0.1` is a sandbox validation release of the published Maven artifacts. Once validated, it is promoted unchanged to `1.0.0` (i.e., `0.0.1` ≙ `1.0.0`). The v1.x roadmap below builds on that baseline.
>
> **Pre-1.0.0 gate:** verify (and fix if needed) the ART `VerifyError` under baseline profiles — strict `speed-profile` verification rejects generic reflection bytecode around the `Memoize<Constructor>`/`Memoize<Method>` usage in `JSEngineContext`/`JavaObject`/`JavaMethodObject` (see `jsengine-jar-upgrade-prompt.md`). Check the AAR classes with `dexdump -verify` before promotion.
>
> **Engine policy:** the vendored QuickJS stays frozen at **2020-07-05** through all v1.x/v2.x releases. The revamp's priority is functional parity with the legacy vendored-jar setup; the engine update is isolated in **v3.0.0**.

## Priority Framework

**🔴 Critical Priority:**
- Performance improvements
- ANR prevention
- Memory leak fixes
- Crash fixes

**🟡 High Priority:**
- API improvements
- Developer experience
- Documentation
- Testing infrastructure

**🟢 Medium Priority:**
- New features
- Advanced capabilities
- Nice-to-haves

---

## v1.0.1 - Build & Performance Foundation

**Release Goal:** Improve build system and optimize native compilation

### #8 Gradle Version Catalog 🟡
**Priority:** High
**Effort:** 2-3 hours
**Description:**
- Migrate from hardcoded versions to `libs.versions.toml`
- Better dependency management across modules
- Easier to maintain and update dependencies

**Implementation:**
```toml
[versions]
agp = "8.8.0"
kotlin = "1.6.21"
ndk = "28.2.13676358"

[libraries]
android-gradle-plugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
```

### #9 Native Build Optimization 🔴
**Priority:** Critical (Performance)
**Effort:** 4-6 hours
**Description:**
- Optimize CMake build configuration for faster iteration
- Add build variants for specific ABIs during development
- Profile and optimize JNI method call overhead
- Consider native method caching strategies

**Performance Impact:**
- Faster developer builds (30-50% reduction)
- Smaller APK sizes when targeting specific ABIs
- Reduced cold start time with optimized JNI

**Implementation:**
```gradle
// Note: android.splits.abi applies to APP modules, not library AARs.
// The AAR always ships all ABIs; consumers get per-ABI delivery via App Bundles.
// For faster LOCAL builds, restrict ABIs in a dev-only flavor instead:
android {
    flavorDimensions "mode"
    productFlavors {
        dev {
            dimension "mode"
            ndk { abiFilters 'arm64-v8a' } // dev builds only — never release
        }
        full { dimension "mode" }
    }
}
```

**ANR Prevention Considerations:**
- Profile JNI call overhead
- Identify and optimize hot paths
- Ensure native calls don't block main thread

---

## v1.0.2 - Documentation & Examples

**Release Goal:** Comprehensive documentation and performance guidance

### #11 Sample App Improvements 🟡
**Priority:** High
**Effort:** 6-8 hours
**Description:**
- Create comprehensive example app in `jsengine/JSEngine/`
- Demonstrate common use cases with performance best practices
- Show proper lifecycle management to avoid ANRs
- Include QuickJS vs Duktape benchmarks

**Examples to Include:**
1. **Basic Usage** - Hello World, simple evaluations
2. **Async Patterns** - Background execution, avoiding main thread blocking
3. **Type Coercion** - Java ↔ JavaScript object mapping
4. **Memory Management** - Proper context cleanup, leak prevention
5. **Performance** - Batch operations, caching strategies
6. **Error Handling** - Exception management, debugging

**ANR Prevention Examples:**
```java
// ❌ BAD - Blocks main thread
String result = context.evaluate("longRunningScript()");

// ✅ GOOD - Execute on background thread
executor.execute(() -> {
    String result = context.evaluate("longRunningScript()");
    handler.post(() -> updateUI(result));
});
```

### #12 API Documentation 🟡
**Priority:** High
**Effort:** 4-6 hours
**Description:**
- Generate comprehensive Javadoc
- Document all public APIs with examples
- Include performance notes on each method
- Publish to GitHub Pages

**Focus Areas:**
- Which methods are safe to call on main thread
- Which methods require background execution
- Expected execution times for common operations
- Memory footprint of various operations

### #13 Performance Guide 🔴
**Priority:** Critical (Performance)
**Effort:** 6-8 hours
**Description:**
Create comprehensive performance documentation covering:

#### JNI Bridge Efficiency
- Minimize JNI boundary crossings
- Batch JavaScript operations when possible
- Proper use of `JSEngineContext.evaluate()` vs multiple calls
- Caching JavaScript function references

#### ANR Prevention Strategies
1. **Never block the main thread**
   ```java
   // Use background threads for all JS execution
   ExecutorService executor = Executors.newSingleThreadExecutor();
   ```

2. **Set execution timeouts**
   ```java
   // Planned for v1.2.0 (via JS_SetInterruptHandler, works on the current engine):
   // context.setMaxExecutionTime(5000); // 5 seconds
   ```

3. **Monitor heap growth**
   ```java
   long heapSize = context.getHeapSize();
   if (heapSize > threshold) {
       context.gc();
   }
   ```

4. **Use job queues for async operations**
   ```java
   // Process pending jobs without blocking
   while (context.hasPendingJobs()) {
       context.runJobs();
   }
   ```

#### QuickJS vs Duktape Performance
- **QuickJS:** Faster execution, modern ES features, larger memory footprint
- **Duktape:** Slower but more predictable, smaller footprint, ES5.1 only
- Benchmarks and decision matrix

#### Memory Management Best Practices
- Always call `context.close()` in try-finally
- Force GC when crossing memory thresholds
- Monitor `JavaScriptObject` finalization queue
- Avoid circular references between Java and JavaScript

#### Common Performance Pitfalls
1. Creating new contexts repeatedly (expensive)
2. Not reusing compiled functions
3. Excessive type coercion
4. Large object graphs crossing JNI boundary
5. Synchronous I/O in JavaScript callbacks

**Target Metrics:**
- Document expected execution times
- Memory overhead per context
- Startup time benchmarks
- ANR risk assessment for common patterns

### #14 Security Guide 🟡
**Priority:** High
**Effort:** 4-6 hours
**Description:**
- Safe JavaScript execution patterns
- Sandboxing recommendations
- Input validation strategies
- Preventing code injection
- Resource limits (memory, execution time)

**ANR Security Angle:**
- Prevent malicious scripts from blocking execution
- Implement timeout mechanisms
- Resource exhaustion attacks prevention

---

## v1.1.0 - Modern Android Support

**Release Goal:** First-class Kotlin support with performance optimizations

### #4 Kotlin DSL Support 🟡
**Priority:** High
**Effort:** 8-12 hours
**Description:**
Add Kotlin-friendly APIs with performance in mind

#### Coroutine Support (ANR Prevention Focus)
```kotlin
// Automatic background execution
suspend fun JSEngineContext.evaluateAsync(code: String): Any? =
    withContext(Dispatchers.Default) {
        evaluate(code)
    }

// Timeout support
suspend fun JSEngineContext.evaluateWithTimeout(
    code: String,
    timeoutMs: Long = 5000
): Result<Any?> = withTimeoutOrNull(timeoutMs) {
    evaluateAsync(code)
}?.let { Result.success(it) } ?: Result.failure(TimeoutException())
```

#### Reactive Patterns
Polling-based Flow wrappers (a `while` loop with `delay()` emitting property reads) are **deliberately out of scope** — they drain battery while pretending to be reactive. Reactive updates must be push-based (JavaScript callbacks invoking Kotlin), designed as part of the v2.0.0 API.

#### Extension Functions for Type Safety
```kotlin
inline fun <reified T> JSEngineContext.evaluate(code: String): T {
    return evaluate(code) as T
}

// Usage
val result: String = context.evaluate("'hello world'")
```

#### Performance-Aware Builders
```kotlin
class JSEngineContextBuilder {
    var maxHeapSize: Long = 16 * 1024 * 1024 // 16MB default
    var executionTimeout: Long = 5000 // 5s default
    var useQuickJS: Boolean = true

    fun build(): JSEngineContext {
        // Create optimized context
    }
}

val context = jsEngineContext {
    maxHeapSize = 32.MB
    executionTimeout = 10.seconds
    useQuickJS = true
}
```

**ANR Prevention Features:**
- All async operations use coroutines by default
- Built-in timeout support
- Automatic background dispatching
- Push-based callbacks instead of polling

---

## v1.2.0 - Advanced Performance & Monitoring

**Release Goal:** Production-grade performance monitoring and optimization

### Performance Monitoring API 🔴
**Priority:** Critical (Performance)
**Effort:** 10-15 hours
**Description:**
Comprehensive performance monitoring and ANR detection

#### Real-time Performance Stats
```java
public interface JSEngineStats {
    // Execution metrics
    long getLastExecutionTime();
    long getAverageExecutionTime();
    long getMaxExecutionTime();
    int getExecutionCount();

    // Memory metrics
    long getCurrentHeapSize();
    long getPeakHeapSize();
    long getAllocatedObjects();

    // ANR risk detection
    boolean isAtRiskOfANR(); // Based on execution patterns
    long getMainThreadBlockTime(); // Total time blocked

    // Performance degradation
    boolean isPerformanceDegrading(); // Trend analysis
}

JSEngineStats stats = context.getStats();
if (stats.isAtRiskOfANR()) {
    // Move to background thread or optimize
    Log.w(TAG, "ANR risk detected: " + stats.getMainThreadBlockTime() + "ms");
}
```

#### Execution Time Tracking
```java
context.setExecutionListener(new ExecutionListener() {
    @Override
    public void onExecutionStart(String code) {}

    @Override
    public void onExecutionEnd(String code, long durationMs) {
        if (durationMs > 16) { // Frame budget
            Log.w(TAG, "Slow execution: " + durationMs + "ms");
        }
    }

    @Override
    public void onExecutionTimeout(String code) {
        // ANR prevented
    }
});
```

#### Memory Profiling
```java
MemoryProfile profile = context.profileMemory();
System.out.println("Objects: " + profile.getObjectCount());
System.out.println("Strings: " + profile.getStringCount());
System.out.println("Functions: " + profile.getFunctionCount());
System.out.println("Heap fragmentation: " + profile.getFragmentation());
```

#### ANR Detection & Prevention
```java
context.setANRWatchdog(new ANRWatchdog() {
    @Override
    public void onSlowExecution(String code, long durationMs) {
        // Warn if execution taking too long
    }

    @Override
    public void onMainThreadBlock(long durationMs) {
        // Critical: main thread blocked
        Analytics.track("anr_risk", durationMs);
    }
});
```

### Execution Timeout & Interrupt Support 🔴
**Priority:** Critical (ANR Prevention)
**Effort:** 6-10 hours
**Description:**
Real script interruption via QuickJS `JS_SetInterruptHandler` — available in the **current 2020-07-05 engine**, so no engine update is required.

```java
context.setMaxExecutionTime(5000);   // interrupt runaway scripts after 5s
context.setInterruptHandler(() -> shouldCancel); // cooperative cancellation
```

**Why now:** coroutine/executor timeouts only stop the *caller* from waiting — the script keeps running and pins its thread. A native interrupt handler is the only mechanism that actually stops execution. This also unblocks real cancellation for v2.1.0 worker threads.

**Testing Focus:**
- Runaway script (infinite loop) is interrupted within the deadline
- Interrupted evaluation surfaces a catchable exception, context stays usable
- No interference with normal execution (overhead benchmark)

> **Note:** The QuickJS engine update previously planned for this release has moved to **v3.0.0** (see below) — engine parity is maintained through all v1.x/v2.x releases.

---

## v1.3.0 - Console & Debugging (Optional)

### Console API 🟡
**Priority:** High (Developer Experience)
**Effort:** 4-6 hours
**Description:**
```java
context.setConsole(new JSConsole() {
    @Override
    public void log(Object... args) {
        Log.d(TAG, Arrays.toString(args));
    }

    @Override
    public void error(Object... args) {
        Log.e(TAG, Arrays.toString(args));
    }

    @Override
    public void time(String label) {
        // Performance timing
    }

    @Override
    public void timeEnd(String label) {
        // Measure execution blocks
    }
});
```

**Performance Benefit:**
- `console.time()` / `console.timeEnd()` for profiling JavaScript code
- Identify slow JavaScript functions
- Better debugging of performance issues

---

## v2.x Series - Major Architecture Updates

The v2.0.0 release has been broken down into smaller, incremental releases to reduce risk and enable faster delivery. All v2.x releases run on the **frozen 2020-07-05 engine**; the engine update and everything that depends on it live in v3.x:

- **v2.0.0** - Kotlin API foundation (breaking changes, but establishes new architecture)
- **v2.1.0** - Worker threads (builds on coroutines from v2.0.0 + interrupt support from v1.2.0)
- **v2.2.0** - ES modules (with old-engine limitations documented; lifted at v3.0.0)
- **v3.0.0** - QuickJS engine update (isolated major — behavioral risk of a ~6-year engine jump)
- **v3.1.0** - Bytecode compilation (must follow the engine update: bytecode format is engine-version-specific, shipping it earlier would invalidate all consumer bytecode at v3.0.0)

**Benefits of this breakdown:**
1. **Reduced risk** - Smaller releases are easier to test and validate
2. **Faster delivery** - Can ship performance improvements (bytecode) sooner
3. **Clear dependencies** - Each release builds logically on the previous
4. **Flexibility** - Can adjust priorities based on user feedback
5. **Incremental migration** - Users can adopt new features gradually

**Total effort:** ~57-81 hours (same as original v2.0.0, but spread across 4 releases)

---

## v2.0.0 - Kotlin API Foundation

**Release Goal:** Modern Kotlin-first API with type safety and coroutines

**Breaking Changes:** Yes - Java APIs deprecated, Kotlin becomes primary

### API Modernization (Kotlin-first) 🟡
**Priority:** High
**Effort:** 20-30 hours
**Description:**
Rewrite core APIs in Kotlin with modern language features

**Key Changes:**
- Null safety throughout (no more `@Nullable`/`@NonNull`)
- Sealed classes for type-safe JavaScript values
- Coroutines as primary async mechanism
- Extension functions for ergonomic API
- Data classes for configuration

**New API:**
```kotlin
sealed class JSValue {
    data class String(val value: kotlin.String) : JSValue()
    data class Number(val value: Double) : JSValue()
    data class Boolean(val value: kotlin.Boolean) : JSValue()
    data class Object(val value: JSEngineObject) : JSValue()
    object Null : JSValue()
    object Undefined : JSValue()
}

// Coroutine-based async evaluation
suspend fun JSEngineContext.evaluate(code: String): JSValue

// Type-safe extensions
inline fun <reified T> JSEngineContext.evaluate(code: String): T

// Builder pattern for configuration
class JSEngineContextBuilder {
    var maxHeapSize: Long = 16 * 1024 * 1024
    var executionTimeout: Long = 5000
    var useQuickJS: Boolean = true
    fun build(): JSEngineContext
}
```

**Migration Path:**
- Java APIs marked `@Deprecated` with migration guide
- Compatibility layer maintains Java support for 2 major versions
- Kotlin interop ensures gradual migration

**Testing Requirements:**
- Full regression test suite
- Performance benchmarks vs v1.x
- Migration guide with examples
- Backward compatibility verification

---

## v2.1.0 - Worker Thread Support

**Release Goal:** Parallel JavaScript execution with ANR prevention

**Prerequisites:** v2.0.0 (Coroutines foundation), v1.2.0 (interrupt support — required for real script cancellation)

### Worker Thread Support 🔴
**Priority:** Critical (Performance + ANR Prevention)
**Effort:** 15-20 hours
**Description:**
Dedicated worker threads for parallel JavaScript execution with proper thread safety

**ANR Prevention:**
- Dedicated worker threads for long-running scripts
- Message passing between contexts
- Parallel execution on multi-core devices
- Main thread never blocked
- Automatic timeout and cancellation

**API:**
```kotlin
// Worker with dedicated thread pool
class JSWorker(
    private val code: String,
    private val context: JSEngineContext? = null
) {
    suspend fun execute(): JSValue = withContext(Dispatchers.Default) {
        // Execute in background thread
    }
    
    suspend fun executeWithTimeout(timeoutMs: Long): Result<JSValue>
    
    fun cancel()
}

// Usage - completely off main thread
val worker = JSWorker("computeExpensiveResult()")
val result = worker.execute() // Never blocks main thread

// With timeout
val result = worker.executeWithTimeout(5000)
    .getOrElse { /* handle timeout */ }

// Worker pool for parallel execution
class JSWorkerPool(size: Int = Runtime.getRuntime().availableProcessors()) {
    suspend fun execute(code: String): JSValue
    fun shutdown()
}

// Message passing between workers
class JSWorker {
    suspend fun postMessage(message: JSValue)
    fun onMessage(handler: suspend (JSValue) -> Unit)
}
```

**Thread Safety:**
- Each worker has isolated context
- No shared state between workers
- Thread-safe message passing
- Proper cleanup on cancellation

**Performance Benefits:**
- Parallel execution on multi-core devices
- No main thread blocking
- Better CPU utilization
- Isolated memory per worker

**Testing:**
- Concurrent execution stress tests
- ANR detection in test suite
- Memory leak detection per worker
- Cancellation and timeout tests

---

## v2.2.0 - ES Module System

**Release Goal:** ES6 module support with custom module resolution

**Prerequisites:** v2.0.0 (Kotlin API foundation)

### ES Module System 🟢
**Priority:** Medium
**Effort:** 12-16 hours
**Description:**
ES6 module support with custom module resolution. Ships on the frozen 2020-07-05 engine, so dynamic `import()` and top-level await are **not** available until v3.0.0.

**Features:**
- ES6 `import`/`export` syntax
- Dynamic `import()` function *(deferred to v3.0.0 — unsupported by the frozen engine)*
- Custom module resolution
- Module caching with bytecode *(deferred to v3.1.0)*
- Bundler integration support

**API:**
```kotlin
// Enable ES modules
val context = jsEngineContext {
    enableESModules = true
    moduleResolver = CustomModuleResolver { specifier ->
        // Custom resolution logic
        when (specifier) {
            "my-module" -> loadFromAssets("modules/my-module.js")
            else -> null // Use default resolution
        }
    }
}

// Evaluate module
val module = context.evaluateModule("""
    import { func } from './utils.js';
    export const result = func();
""")

// Dynamic import
val dynamicModule = context.import("my-module.js")

// Module with bytecode caching
val module = context.evaluateModule(
    code = "export const x = 42;",
    cacheBytecode = true
)
```

**Module Resolution:**
- File system resolution (Android assets, files)
- HTTP/HTTPS module loading (optional)
- Custom resolver interface
- Module graph caching
- Circular dependency detection

**Bundler Integration:**
- Webpack/Rollup output compatibility
- Source map support
- Tree-shaking friendly
- Code splitting support

**Performance:**
- Module bytecode caching
- Lazy module loading
- Parallel module resolution
- Module graph optimization

**Testing:**
- ES6 module syntax compliance
- Dynamic import tests
- Circular dependency handling
- Module caching verification

---

## v3.0.0 - QuickJS Engine Update

**Release Goal:** Update the vendored QuickJS from 2020-07-05 to a modern release

**Breaking Changes:** Potentially — behavioral differences from a ~6-year engine jump. This is exactly why it is an isolated major release: all v1.x/v2.x work maintains parity with the legacy vendored-jar behavior, and the engine jump ships alone so regressions are attributable.

### QuickJS Update 🔴
**Priority:** Critical (Performance + Features)
**Effort:** 8-12 hours (optimistic; 2-3 days if the custom debugger fork needs major rework)
**Prerequisites:** Stable v2.x line, full regression test suite
**Description:**
- See [QUICKJS_UPDATE_PLAN.md](./QUICKJS_UPDATE_PLAN.md) for the detailed procedure
- **Decision needed first:** Bellard QuickJS vs [quickjs-ng](https://github.com/quickjs-ng/quickjs) (the more actively maintained fork) — affects the custom `quickjs-debugger.*` port
- Unlocks for v2.2.0 ES modules: dynamic `import()`, top-level await

**Performance Improvements Expected:**
- 10-20% faster execution
- 15-25% better memory efficiency
- Improved GC performance

**Testing Focus:**
- Regression testing all JNI bindings against the v2.x test suite
- Behavioral parity checks (type coercion, error messages, edge cases)
- Performance benchmarks vs old engine
- Memory leak detection

---

## v3.1.0 - Bytecode Compilation

**Release Goal:** Performance optimization through pre-compiled bytecode

**Prerequisites:** v3.0.0 (QuickJS engine update). Bytecode format is engine-version-specific — shipping this before the engine update would invalidate all consumer bytecode at v3.0.0.

### Bytecode Compilation 🔴
**Priority:** Critical (Performance)
**Effort:** 10-15 hours
**Description:**
Pre-compile JavaScript to QuickJS bytecode for faster execution

**Performance Benefits:**
- 50-70% faster startup for large scripts
- Smaller distribution size (bytecode vs source)
- Skip parsing phase entirely
- Obfuscation benefit (bytecode is harder to reverse)

**API:**
```kotlin
// Compile at build time or first run
val bytecode: ByteArray = context.compileToBytecode("myScript.js")
saveBytecode(bytecode, "myScript.qjsbc")

// Execute bytecode at runtime - much faster
val result: JSValue = context.evaluateBytecode(loadBytecode("myScript.qjsbc"))

// Or use extension for convenience
val result = context.evaluateBytecode("myScript.qjsbc")
```

**Build-time Integration:**
```kotlin
// Gradle plugin (future)
jsengine {
    compileToBytecode = true
    bytecodeOutputDir = "src/main/assets/js"
}
```

**Implementation Details:**
- Use QuickJS `JS_ReadObject()` / `JS_WriteObject()` APIs
- Validate bytecode version compatibility (embed engine version, fallback to source on mismatch)
- Fallback to source if bytecode invalid
- QuickJS only — Duktape bytecode is a different mechanism and out of scope

**Testing:**
- Performance benchmarks (startup time, execution time)
- Bytecode version compatibility tests
- Error handling for corrupted bytecode
- Size comparison (source vs bytecode)

---

## Long-term Ideas (v3.2+)

### WebAssembly Support 🟢
- Execute WASM modules in QuickJS
- Opens entire ecosystem
- Performance comparable to native

### Native Module System 🟢
- Plugin architecture for custom bindings
- Easier to expose native APIs to JavaScript
- Dynamic loading

### JVM Desktop Support 🟢
- Extract pure Java core
- Support desktop applications
- Unified codebase

---

## Performance & ANR Prevention Principles

### Universal Guidelines

1. **Never Block Main Thread**
   - All JavaScript execution on background threads
   - Coroutines/ExecutorService for async work
   - Timeouts on all operations

2. **Monitor Execution Time**
   - Track all evaluations > 16ms (frame budget)
   - Alert on > 100ms (ANR territory)
   - Kill on > 5000ms (definite ANR)

3. **Memory Awareness**
   - Set max heap limits
   - Monitor growth trends
   - Proactive garbage collection
   - Leak detection in debug builds

4. **Batch Operations**
   - Minimize JNI boundary crossings
   - Use single `evaluate()` vs multiple calls
   - Cache JavaScript function references

5. **Resource Limits**
   - Maximum execution time per operation
   - Maximum heap size per context
   - Maximum concurrent contexts
   - Queue depth limits

6. **Testing Requirements**
   - Performance regression tests
   - ANR detection in CI/CD
   - Memory leak detection
   - Stress testing under load

### Success Metrics

Each release should measure:
- **P50/P95/P99 execution time** for common operations
- **ANR rate** in production (target: 0%)
- **Memory footprint** per context (trend down)
- **Startup time** (trend down)
- **Frame drops** during execution (target: 0)

---

## Release Timeline Estimate

- **v1.0.1** - 1-2 weeks (Build optimization)
- **v1.0.2** - 2-3 weeks (Documentation)
- **v1.1.0** - 3-4 weeks (Kotlin DSL)
- **v1.2.0** - 3-5 weeks (Performance monitoring + execution timeout/interrupt)
- **v1.3.0** - 2-3 weeks (Console API, optional)
- **v2.0.0** - 3-4 weeks (Kotlin API foundation)
- **v2.1.0** - 3-4 weeks (Worker threads)
- **v2.2.0** - 2-3 weeks (ES modules)
- **v3.0.0** - 2-4 weeks (QuickJS engine update)
- **v3.1.0** - 2-3 weeks (Bytecode compilation)

---

## Contributing

When implementing features from this plan:

1. **Performance First**
   - Benchmark before and after
   - Profile hot paths
   - Test on low-end devices

2. **ANR Prevention**
   - Never assume "fast enough"
   - Always provide async alternatives
   - Include timeout mechanisms

3. **Documentation**
   - Document performance characteristics
   - Include ANR prevention examples
   - Show both sync and async usage

4. **Testing**
   - Unit tests for correctness
   - Performance tests for regression
   - Stress tests for ANR scenarios
   - Memory leak detection

---

## Detailed Planning Documents

For in-depth implementation details, see:

| Document | Feature | Target Version |
|----------|---------|----------------|
| [KOTLIN_API_PLAN.md](./KOTLIN_API_PLAN.md) | Kotlin-first API Modernization | v2.0.0 |
| [WORKER_THREADS_PLAN.md](./WORKER_THREADS_PLAN.md) | Worker Thread Support | v2.1.0 |
| [ES_MODULES_PLAN.md](./ES_MODULES_PLAN.md) | ES Module System | v2.2.0 |
| [QUICKJS_UPDATE_PLAN.md](./QUICKJS_UPDATE_PLAN.md) | QuickJS Engine Update | v3.0.0 |

---

**Last Updated:** 2026-07-02
**Current Version:** 0.0.1 (sandbox validation release — promoted unchanged to 1.0.0 once validated)
