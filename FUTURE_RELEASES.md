# JSEngine Future Releases Plan

This document outlines the roadmap for future JSEngine releases with emphasis on **performance optimization** and **ANR prevention**.

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
android {
    splits {
        abi {
            enable true
            reset()
            include 'arm64-v8a', 'armeabi-v7a'
        }
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
   // Future feature: context.setMaxExecutionTime(5000); // 5 seconds
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

#### Flow Integration for Reactive Patterns
```kotlin
fun JSEngineContext.observeProperty(name: String): Flow<Any?> = flow {
    // Efficient property watching without polling
    while (currentCoroutineContext().isActive) {
        val value = getGlobalObject().get(name)
        emit(value)
        delay(100) // Configurable
    }
}.flowOn(Dispatchers.Default)
```

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
- Flow-based reactive patterns avoid polling

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

### QuickJS Update 🔴
**Priority:** Critical (Performance + Features)
**Effort:** 8-12 hours
**Prerequisites:** Testing infrastructure (v1.0.1)
**Description:**
- Update QuickJS from 2021 to 2024 version
- Performance improvements in newer versions
- Better memory management
- Bug fixes

**Performance Improvements Expected:**
- 10-20% faster execution
- 15-25% better memory efficiency
- Improved GC performance

**Testing Focus:**
- Regression testing all JNI bindings
- Performance benchmarks vs old version
- ANR risk assessment
- Memory leak detection

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

## v2.0.0 - Major Architecture Update

**Release Goal:** Modern architecture with advanced performance features

### API Modernization (Kotlin-first) 🟡
**Breaking Changes:** Yes
**Effort:** 20-30 hours
**Description:**
- Rewrite core APIs in Kotlin
- Null safety throughout
- Sealed classes for type safety
- Coroutines as primary async mechanism

```kotlin
sealed class JSValue {
    data class String(val value: kotlin.String) : JSValue()
    data class Number(val value: Double) : JSValue()
    data class Boolean(val value: kotlin.Boolean) : JSValue()
    data class Object(val value: JSEngineObject) : JSValue()
    object Null : JSValue()
    object Undefined : JSValue()
}

suspend fun JSEngineContext.evaluate(code: String): JSValue
```

### Worker Thread Support 🔴
**Priority:** Critical (Performance + ANR Prevention)
**Effort:** 15-20 hours
**Description:**
Parallel JavaScript execution with proper thread safety

```kotlin
class JSWorker(private val code: String) {
    private val context = JSEngineContext.create(useQuickJS = true)
    private val thread = HandlerThread("JSWorker")

    suspend fun execute(): Any? = suspendCoroutine { continuation ->
        thread.handler.post {
            try {
                val result = context.evaluate(code)
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}

// Usage - completely off main thread
val worker = JSWorker("computeExpensiveResult()")
val result = worker.execute() // Never blocks main thread
```

**ANR Prevention:**
- Dedicated worker threads for long-running scripts
- Message passing between contexts
- Parallel execution on multi-core devices
- Main thread never blocked

### ES Module System 🟢
**Priority:** Medium
**Effort:** 12-16 hours
**Description:**
- Full ES6 module support
- Dynamic imports
- Module resolution customization
- Bundler integration

### Bytecode Compilation 🔴
**Priority:** Critical (Performance)
**Effort:** 10-15 hours
**Description:**
Pre-compile scripts to QuickJS bytecode

**Performance Benefits:**
- 50-70% faster startup for large scripts
- Smaller distribution (bytecode vs source)
- Skip parsing phase entirely
- Obfuscation benefit

```kotlin
// At build time or first run
val bytecode = context.compileToByteCode("myScript.js")
saveBytecode(bytecode)

// At runtime - much faster
val result = context.evaluateBytecode(loadBytecode())
```

---

## Long-term Ideas (v3.0+)

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
- **v1.2.0** - 4-6 weeks (Performance monitoring + QuickJS update)
- **v1.3.0** - 2-3 weeks (Console API, optional)
- **v2.0.0** - 8-12 weeks (Major rewrite)

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

**Last Updated:** 2025-11-14
**Current Version:** 0.0.1
