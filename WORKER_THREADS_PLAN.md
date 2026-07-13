# Worker Threads Plan

## Overview

**Target Version:** 2.1.0
**Priority:** Critical (ANR Prevention)
**Effort:** 15-20 hours
**Breaking Changes:** No (additive API)
**Prerequisites:** v2.0.0 (Kotlin API / coroutines foundation), v1.2.0 (native interrupt support via `JS_SetInterruptHandler` — without it, timeouts and cancellation cannot actually stop a running script; they only stop the caller from waiting while the script pins its worker thread)

This document outlines the plan to implement dedicated worker threads for JavaScript execution, ensuring the main thread is never blocked and preventing ANRs.

---

## Goals

1. **ANR Prevention** - Main thread never blocked by JS execution
2. **Simplicity** - No manual thread management for users
3. **Parallelism** - Multiple scripts on multi-core devices
4. **Cancellation** - Coroutine cancellation support
5. **Lifecycle Integration** - Automatic cleanup with Android lifecycle

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Main Thread                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  val worker = JSWorker.create()                      │   │
│  │  launch { val result = worker.execute("code") }      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ suspendCancellableCoroutine
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    JSWorker Thread                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  HandlerThread("JSWorker-1")                         │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │  JSEngineContext (thread-confined)           │    │   │
│  │  │  - evaluate()                                │    │   │
│  │  │  - runPendingJobs()                          │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Thread Confinement

Each `JSWorker` owns a single `JSEngineContext` that is only accessed from its dedicated `HandlerThread`. This eliminates thread-safety concerns within the context itself.

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  JSWorker-1 │     │  JSWorker-2 │     │  JSWorker-3 │
│  ┌───────┐  │     │  ┌───────┐  │     │  ┌───────┐  │
│  │Context│  │     │  │Context│  │     │  │Context│  │
│  └───────┘  │     │  └───────┘  │     │  └───────┘  │
│  Thread-1   │     │  Thread-2   │     │  Thread-3   │
└─────────────┘     └─────────────┘     └─────────────┘
      │                   │                   │
      └───────────────────┼───────────────────┘
                          │
                    No shared state
```

---

## Implementation

### 1. JSWorker Class

**File:** `worker/JSWorker.kt`

```kotlin
package com.hyprmx.android.jsengine.worker

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import com.hyprmx.android.jsengine.core.JSEngineContext
import com.hyprmx.android.jsengine.types.JSValue
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A dedicated worker thread for JavaScript execution.
 *
 * JSWorker ensures all JavaScript execution happens off the main thread,
 * preventing ANRs. Each worker has its own JSEngineContext that is
 * confined to the worker thread.
 *
 * Basic usage:
 * ```kotlin
 * val worker = JSWorker.create()
 *
 * // Execute code (suspends, never blocks main thread)
 * val result = worker.execute("2 + 2")
 *
 * // Clean up
 * worker.terminate()
 * ```
 *
 * With lifecycle:
 * ```kotlin
 * class MyViewModel : ViewModel() {
 *     private val worker = JSWorker.create()
 *
 *     override fun onCleared() {
 *         worker.terminate()
 *     }
 * }
 * ```
 */
class JSWorker private constructor(
    private val name: String,
    private val engine: JSEngineContext.Engine,
    private val threadPriority: Int
) {
    private val thread = HandlerThread(name, threadPriority).apply { start() }
    private val handler = Handler(thread.looper)

    // Context is created ON the worker thread
    private var context: JSEngineContext? = null
    private val contextInitialized = AtomicBoolean(false)
    private val terminated = AtomicBoolean(false)

    /**
     * Current state of the worker.
     */
    enum class State { CREATED, RUNNING, TERMINATED }

    val state: State
        get() = when {
            terminated.get() -> State.TERMINATED
            contextInitialized.get() -> State.RUNNING
            else -> State.CREATED
        }

    /**
     * Initialize the context on the worker thread.
     * Called automatically on first execute(), but can be called
     * explicitly for eager initialization.
     */
    suspend fun initialize(): Unit = suspendCancellableCoroutine { cont ->
        if (contextInitialized.get()) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        handler.post {
            try {
                if (context == null) {
                    context = JSEngineContext.create(engine)
                    contextInitialized.set(true)
                }
                cont.resume(Unit)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Execute JavaScript code on the worker thread.
     *
     * This method suspends until execution completes but never blocks
     * the calling thread.
     *
     * @param script JavaScript code to execute
     * @return Execution result
     * @throws JSEngineException if execution fails
     * @throws IllegalStateException if worker is terminated
     */
    suspend fun execute(script: String): JSValue = suspendCancellableCoroutine { cont ->
        checkNotTerminated()

        handler.post {
            try {
                // Lazy initialization
                if (context == null) {
                    context = JSEngineContext.create(engine)
                    contextInitialized.set(true)
                }

                val result = context!!.evaluate(script)

                // Run any pending async jobs
                while (context!!.hasPendingJobs()) {
                    context!!.runPendingJobs()
                }

                cont.resume(result)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

        cont.invokeOnCancellation {
            // Trigger the native interrupt handler (v1.2.0 feature) so the
            // running script actually stops. Without this, cancellation only
            // abandons the coroutine while the script keeps pinning the thread.
            context?.requestInterrupt()
        }
    }

    /**
     * Execute with timeout.
     *
     * On timeout the running script is interrupted via the native interrupt
     * handler (v1.2.0) — not merely abandoned — so the worker thread is freed.
     *
     * @return Result or null if timeout exceeded
     */
    suspend fun executeWithTimeout(script: String, timeoutMs: Long): JSValue? {
        return withTimeoutOrNull(timeoutMs) {
            execute(script)
        }
    }

    /**
     * Execute multiple scripts in sequence.
     */
    suspend fun executeAll(scripts: List<String>): List<JSValue> {
        return scripts.map { execute(it) }
    }

    /**
     * Post code for execution without waiting for result.
     * Useful for fire-and-forget operations.
     */
    fun post(script: String) {
        checkNotTerminated()
        handler.post {
            try {
                if (context == null) {
                    context = JSEngineContext.create(engine)
                    contextInitialized.set(true)
                }
                context!!.evaluate(script)
                while (context!!.hasPendingJobs()) {
                    context!!.runPendingJobs()
                }
            } catch (e: Exception) {
                // Log but don't throw - fire and forget
                android.util.Log.e("JSWorker", "Error in posted script", e)
            }
        }
    }

    /**
     * Set a global variable in the worker's context.
     */
    suspend fun setGlobal(name: String, value: Any?): Unit = suspendCancellableCoroutine { cont ->
        checkNotTerminated()
        handler.post {
            try {
                if (context == null) {
                    context = JSEngineContext.create(engine)
                    contextInitialized.set(true)
                }
                context!!.setGlobal(name, value)
                cont.resume(Unit)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Force garbage collection on the worker's context.
     */
    fun gc() {
        if (!contextInitialized.get()) return
        handler.post {
            context?.gc()
        }
    }

    /**
     * Get heap size of the worker's context.
     */
    suspend fun getHeapSize(): Long = suspendCancellableCoroutine { cont ->
        if (!contextInitialized.get()) {
            cont.resume(0L)
            return@suspendCancellableCoroutine
        }
        handler.post {
            cont.resume(context?.heapSize ?: 0L)
        }
    }

    /**
     * Terminate the worker and release all resources.
     *
     * After termination, the worker cannot be reused.
     */
    fun terminate() {
        if (terminated.getAndSet(true)) return

        handler.post {
            context?.close()
            context = null
        }
        thread.quitSafely()
    }

    /**
     * Terminate and wait for completion.
     */
    suspend fun terminateAndJoin() {
        terminate()
        // Wait for thread to finish
        withContext(Dispatchers.IO) {
            thread.join(5000) // 5 second timeout
        }
    }

    private fun checkNotTerminated() {
        if (terminated.get()) {
            throw IllegalStateException("JSWorker has been terminated")
        }
    }

    companion object {
        private val workerCount = AtomicInteger(0)

        /**
         * Create a new JSWorker.
         *
         * @param name Optional name for the worker thread
         * @param engine Which JS engine to use
         * @param threadPriority Thread priority (default: THREAD_PRIORITY_BACKGROUND)
         */
        fun create(
            name: String = "JSWorker-${workerCount.incrementAndGet()}",
            engine: JSEngineContext.Engine = JSEngineContext.Engine.QUICKJS,
            threadPriority: Int = Process.THREAD_PRIORITY_BACKGROUND
        ): JSWorker {
            return JSWorker(name, engine, threadPriority)
        }
    }
}
```

### 2. JSWorkerPool Class

**File:** `worker/JSWorkerPool.kt`

```kotlin
package com.hyprmx.android.jsengine.worker

import com.hyprmx.android.jsengine.core.JSEngineContext
import com.hyprmx.android.jsengine.types.JSValue
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pool of JSWorkers for parallel JavaScript execution.
 *
 * Use when you need to execute multiple independent scripts concurrently.
 *
 * ```kotlin
 * val pool = JSWorkerPool.create(size = 4)
 *
 * // Execute in parallel
 * val results = pool.executeAll(listOf(
 *     "heavyComputation1()",
 *     "heavyComputation2()",
 *     "heavyComputation3()"
 * ))
 *
 * pool.terminateAll()
 * ```
 */
class JSWorkerPool private constructor(
    private val workers: List<JSWorker>
) {
    private val nextWorker = AtomicInteger(0)
    private val size: Int get() = workers.size

    /**
     * Execute on the next available worker (round-robin).
     */
    suspend fun execute(script: String): JSValue {
        // floorMod: getAndIncrement() eventually overflows to negative Int,
        // and a plain % would then produce a negative index.
        val index = Math.floorMod(nextWorker.getAndIncrement(), size)
        return workers[index].execute(script)
    }

    /**
     * Execute all scripts in parallel across workers.
     *
     * @param scripts List of scripts to execute
     * @return Results in same order as input scripts
     */
    suspend fun executeAll(scripts: List<String>): List<JSValue> = coroutineScope {
        scripts.mapIndexed { index, script ->
            async {
                val workerIndex = index % size
                workers[workerIndex].execute(script)
            }
        }.awaitAll()
    }

    /**
     * Execute all scripts with timeout.
     *
     * @return List of results, with null for scripts that timed out
     */
    suspend fun executeAllWithTimeout(
        scripts: List<String>,
        timeoutMs: Long
    ): List<JSValue?> = coroutineScope {
        scripts.mapIndexed { index, script ->
            async {
                val workerIndex = index % size
                workers[workerIndex].executeWithTimeout(script, timeoutMs)
            }
        }.awaitAll()
    }

    /**
     * Execute a map operation in parallel.
     *
     * ```kotlin
     * val results = pool.map(items) { item ->
     *     "processItem('$item')"
     * }
     * ```
     */
    suspend fun <T> map(
        items: List<T>,
        transform: (T) -> String
    ): List<JSValue> = coroutineScope {
        items.mapIndexed { index, item ->
            async {
                val workerIndex = index % size
                workers[workerIndex].execute(transform(item))
            }
        }.awaitAll()
    }

    /**
     * Get a specific worker by index.
     */
    fun getWorker(index: Int): JSWorker = workers[index % size]

    /**
     * Force garbage collection on all workers.
     */
    fun gcAll() {
        workers.forEach { it.gc() }
    }

    /**
     * Terminate all workers.
     */
    fun terminateAll() {
        workers.forEach { it.terminate() }
    }

    /**
     * Terminate all workers and wait for completion.
     */
    suspend fun terminateAllAndJoin() {
        coroutineScope {
            workers.map { worker ->
                launch { worker.terminateAndJoin() }
            }
        }
    }

    companion object {
        /**
         * Create a worker pool.
         *
         * @param size Number of workers (default: number of CPU cores)
         * @param engine Which JS engine to use
         */
        fun create(
            size: Int = Runtime.getRuntime().availableProcessors(),
            engine: JSEngineContext.Engine = JSEngineContext.Engine.QUICKJS
        ): JSWorkerPool {
            val workers = List(size) { index ->
                JSWorker.create(
                    name = "JSWorkerPool-$index",
                    engine = engine
                )
            }
            return JSWorkerPool(workers)
        }
    }
}
```

### 3. Lifecycle Integration

**File:** `worker/JSWorkerLifecycle.kt`

```kotlin
package com.hyprmx.android.jsengine.worker

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Extension to tie JSWorker lifecycle to Android lifecycle.
 *
 * ```kotlin
 * class MyFragment : Fragment() {
 *     private val worker = JSWorker.create().bindTo(lifecycle)
 *
 *     // Worker automatically terminated when Fragment is destroyed
 * }
 * ```
 */
fun JSWorker.bindTo(lifecycle: Lifecycle): JSWorker {
    lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                this@bindTo.terminate()
            }
        }
    })
    return this
}

/**
 * Extension for JSWorkerPool.
 */
fun JSWorkerPool.bindTo(lifecycle: Lifecycle): JSWorkerPool {
    lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                this@bindTo.terminateAll()
            }
        }
    })
    return this
}
```

### 4. ViewModel Integration

**File:** `worker/JSWorkerViewModel.kt`

```kotlin
package com.hyprmx.android.jsengine.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Base ViewModel with built-in JSWorker support.
 *
 * ```kotlin
 * class MyViewModel : JSWorkerViewModel() {
 *     fun processData(data: String) {
 *         executeJS("processData('$data')") { result ->
 *             // Handle result on main thread
 *         }
 *     }
 * }
 * ```
 */
abstract class JSWorkerViewModel(
    engine: JSEngineContext.Engine = JSEngineContext.Engine.QUICKJS
) : ViewModel() {

    protected val worker: JSWorker = JSWorker.create(engine = engine)

    /**
     * Execute JavaScript and handle result.
     */
    protected fun executeJS(
        script: String,
        onResult: (JSValue) -> Unit
    ) {
        viewModelScope.launch {
            val result = worker.execute(script)
            onResult(result)
        }
    }

    /**
     * Execute JavaScript with error handling.
     */
    protected fun executeJSSafe(
        script: String,
        onResult: (JSValue) -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = worker.execute(script)
                onResult(result)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        worker.terminate()
    }
}
```

---

## Usage Examples

### Example 1: Simple Worker Usage

```kotlin
class MyViewModel : ViewModel() {
    private val worker = JSWorker.create()

    fun processData(data: String) {
        viewModelScope.launch {
            worker.setGlobal("inputData", data)
            val result = worker.execute("""
                processInputData(inputData)
            """)
            _uiState.value = result.asStringOrNull() ?: "Error"
        }
    }

    override fun onCleared() {
        worker.terminate()
    }
}
```

### Example 2: Parallel Processing with Pool

```kotlin
class DataProcessor {
    private val pool = JSWorkerPool.create(size = 4)

    suspend fun processItems(items: List<String>): List<String> {
        val results = pool.map(items) { item ->
            "transformItem('${item.replace("'", "\\'")}')"
        }
        return results.mapNotNull { it.asStringOrNull() }
    }

    fun close() {
        pool.terminateAll()
    }
}
```

### Example 3: With Timeout for Safety

```kotlin
suspend fun safeEvaluate(worker: JSWorker, script: String): JSValue {
    return worker.executeWithTimeout(script, timeoutMs = 5000)
        ?: throw TimeoutException("Script execution timed out")
}
```

### Example 4: Fragment with Lifecycle Binding

```kotlin
class MyFragment : Fragment() {
    private val worker by lazy {
        JSWorker.create().bindTo(lifecycle)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val result = worker.execute("getData()")
            // Update UI
        }
    }
    // No need to manually terminate - lifecycle binding handles it
}
```

### Example 5: Background Data Processing

```kotlin
class BackgroundProcessor(context: Context) {
    private val worker = JSWorker.create(
        threadPriority = Process.THREAD_PRIORITY_BACKGROUND
    )

    init {
        // Load processing script
        runBlocking {
            val script = context.assets.open("processor.js")
                .bufferedReader().readText()
            worker.execute(script)
        }
    }

    suspend fun process(data: ByteArray): ByteArray {
        val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
        worker.setGlobal("inputBase64", base64)

        val result = worker.execute("processData(inputBase64)")
        val outputBase64 = result.asStringOrNull()
            ?: throw IllegalStateException("Processing failed")

        return Base64.decode(outputBase64, Base64.NO_WRAP)
    }

    fun close() {
        worker.terminate()
    }
}
```

---

## Thread Safety Considerations

### Safe Patterns

```kotlin
// SAFE: Each worker has its own context
val worker1 = JSWorker.create()
val worker2 = JSWorker.create()

coroutineScope {
    launch { worker1.execute("task1()") }
    launch { worker2.execute("task2()") }
}
```

### Unsafe Patterns (Prevented by Design)

```kotlin
// PREVENTED: Cannot access context directly
// worker.context.evaluate(...) // Compile error - context is private

// PREVENTED: Cannot share context between workers
// Workers are completely isolated
```

### Data Passing

```kotlin
// SAFE: Data is copied when passed via setGlobal
worker.setGlobal("data", largeObject) // Object is serialized

// SAFE: Results are copied back
val result = worker.execute("getData()") // Result is deserialized
```

---

## Performance Considerations

### Worker Creation Cost

| Operation | Approximate Time |
|-----------|------------------|
| Create HandlerThread | ~1-2ms |
| Create JSEngineContext | ~5-10ms |
| Total worker creation | ~6-12ms |

**Recommendation:** Reuse workers instead of creating per-operation.

### Memory Overhead

| Component | Memory |
|-----------|--------|
| HandlerThread | ~1MB stack |
| JSEngineContext (empty) | ~500KB |
| Per worker total | ~1.5MB |

**Recommendation:** Use pool size = CPU cores, not unlimited.

### Pool Sizing Guidelines

| Use Case | Recommended Pool Size |
|----------|----------------------|
| CPU-bound JS | CPU cores |
| I/O-bound JS | CPU cores * 2 |
| Mixed workload | CPU cores |
| Memory constrained | 2-4 |

---

## Dependencies

```gradle
dependencies {
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.x"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.x"

    // Optional: Lifecycle integration
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.6.x"
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.x"
}
```

---

## Testing Requirements

1. **Unit Tests**
   - Worker creation and termination
   - Execute returns correct results
   - Timeout behavior
   - State transitions

2. **Concurrency Tests**
   - Multiple workers don't interfere
   - Pool distributes work correctly
   - No race conditions

3. **ANR Tests**
   - Main thread never blocked
   - Long-running scripts don't cause ANR
   - StrictMode violations detected

4. **Lifecycle Tests**
   - Workers terminated on lifecycle destroy
   - No leaks after Fragment/Activity destroy

---

## Success Criteria

- [ ] Main thread never blocked during JS execution
- [ ] Workers properly isolated (no shared state)
- [ ] Coroutine cancellation works correctly
- [ ] Lifecycle binding prevents leaks
- [ ] Pool parallelizes work effectively
- [ ] Memory usage scales linearly with pool size
- [ ] All tests pass including StrictMode

---

## Future Enhancements

1. **Priority Queues** - High/low priority script execution
2. **Shared Memory** - SharedArrayBuffer-like data sharing
3. **Worker Messages** - postMessage/onMessage pattern
4. **Warm Workers** - Pre-initialized worker pool

---

## Related Documents

- [FUTURE_RELEASES.md](./FUTURE_RELEASES.md) - Release roadmap
- [KOTLIN_API_PLAN.md](./KOTLIN_API_PLAN.md) - Kotlin API modernization
- [ES_MODULES_PLAN.md](./ES_MODULES_PLAN.md) - ES module support

---

**Last Updated:** 2026-07-02
**Status:** Planning
