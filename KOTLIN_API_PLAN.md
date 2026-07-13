# Kotlin-first API Plan

## Overview

**Target Version:** 2.0.0
**Priority:** High
**Effort:** 20-30 hours
**Breaking Changes:** Yes

This document outlines the plan to modernize JSEngine's API with Kotlin-first design, including sealed classes for type safety, coroutine support, and modern idioms.

---

## Goals

1. **Type Safety** - Eliminate runtime type errors with sealed classes
2. **Null Safety** - Leverage Kotlin's null safety throughout
3. **Coroutine Support** - Native async/await patterns
4. **Modern Idioms** - Extension functions, DSL builders, operator overloading
5. **Java Compatibility** - Maintain interop for Java consumers

---

## Current Architecture

```
jsengine-java/src/main/java/com/hyprmx/android/jsengine/
├── JSEngineContext.java          # Main entry point
├── JavaScriptObject.java         # JS object proxy
├── JSEngineRunnable.java         # Runnable wrapper
├── JavaToJavaScriptCoercion.java # Type conversion interface
├── JavaScriptToJavaCoercion.java # Type conversion interface
└── ... (other support classes)
```

### Current API (Java)

```java
JSEngineContext context = JSEngineContext.create(true);
try {
    Object result = context.evaluate("2 + 2");
    // result is Object - no type safety
    if (result instanceof Number) {
        int value = ((Number) result).intValue();
    }
} finally {
    context.close();
}
```

### Problems with Current API

1. `evaluate()` returns `Object` - no compile-time type checking
2. Manual null checks required everywhere
3. No async support - caller must manage threads
4. Verbose boilerplate for common operations
5. No IDE assistance for JS return types

---

## Proposed Architecture

```
jsengine-kotlin/src/main/kotlin/com/hyprmx/android/jsengine/
├── core/
│   ├── JSEngineContext.kt        # Main entry point (rewritten)
│   ├── JSEngineObject.kt         # JS object proxy (renamed + rewritten)
│   └── JSEngineException.kt      # Custom exceptions
├── types/
│   ├── JSValue.kt                # Sealed class hierarchy
│   ├── JSFunction.kt             # Function reference type
│   └── JSPromise.kt              # Promise wrapper
├── coercion/
│   ├── TypeCoercion.kt           # Unified coercion interface
│   ├── JavaToJS.kt               # Java → JS conversions
│   └── JSToJava.kt               # JS → Java conversions
├── extensions/
│   ├── ContextExtensions.kt      # Extension functions
│   └── FlowExtensions.kt         # Reactive extensions
├── dsl/
│   └── JSEngineBuilder.kt        # DSL for context creation
└── compat/
    └── JavaCompat.kt             # Java interop helpers
```

---

## Detailed Implementation

### 1. JSValue Sealed Class Hierarchy

**File:** `types/JSValue.kt`

```kotlin
package com.hyprmx.android.jsengine.types

import com.hyprmx.android.jsengine.core.JSEngineObject

/**
 * Type-safe representation of JavaScript values.
 *
 * Use exhaustive `when` to handle all cases:
 * ```
 * when (value) {
 *     is JSValue.String -> ...
 *     is JSValue.Number -> ...
 *     is JSValue.Boolean -> ...
 *     is JSValue.Object -> ...
 *     is JSValue.Array -> ...
 *     is JSValue.Function -> ...
 *     JSValue.Null -> ...
 *     JSValue.Undefined -> ...
 * }
 * ```
 */
sealed class JSValue {

    /** JavaScript string value */
    data class String(val value: kotlin.String) : JSValue() {
        override fun toString() = value
    }

    /** JavaScript number (always Double in JS) */
    data class Number(val value: Double) : JSValue() {
        fun toInt(): Int = value.toInt()
        fun toLong(): Long = value.toLong()
        fun toFloat(): Float = value.toFloat()
        override fun toString() = value.toString()
    }

    /** JavaScript boolean */
    data class Boolean(val value: kotlin.Boolean) : JSValue() {
        override fun toString() = value.toString()
    }

    /** JavaScript object (non-null, non-array, non-function) */
    data class Object(val value: JSEngineObject) : JSValue() {
        operator fun get(key: kotlin.String): JSValue = value.get(key)
        operator fun set(key: kotlin.String, v: Any?) = value.set(key, v)
        fun keys(): List<kotlin.String> = value.keys()
    }

    /** JavaScript array */
    data class Array(val value: List<JSValue>) : JSValue(), List<JSValue> by value {
        constructor(vararg elements: JSValue) : this(elements.toList())
    }

    /** JavaScript function reference */
    data class Function(
        val value: JSEngineObject,
        val name: kotlin.String? = null
    ) : JSValue() {
        /** Invoke the function with arguments */
        operator fun invoke(vararg args: Any?): JSValue = value.call(*args)

        /** Invoke as constructor (new Foo()) */
        fun construct(vararg args: Any?): JSValue = value.construct(*args)
    }

    /** JavaScript null */
    object Null : JSValue() {
        override fun toString() = "null"
    }

    /** JavaScript undefined */
    object Undefined : JSValue() {
        override fun toString() = "undefined"
    }

    // Convenience methods

    /** Returns true if this is Null or Undefined */
    val isNullish: kotlin.Boolean get() = this is Null || this is Undefined

    /** Safe cast to String or null */
    fun asStringOrNull(): kotlin.String? = (this as? String)?.value

    /** Safe cast to Number or null */
    fun asNumberOrNull(): Double? = (this as? Number)?.value

    /** Safe cast to Boolean or null */
    fun asBooleanOrNull(): kotlin.Boolean? = (this as? Boolean)?.value

    /** Safe cast to Object or null */
    fun asObjectOrNull(): JSEngineObject? = (this as? Object)?.value

    /** Safe cast to Array or null */
    fun asArrayOrNull(): List<JSValue>? = (this as? Array)?.value

    /** Safe cast to Function or null */
    fun asFunctionOrNull(): Function? = this as? Function

    companion object {
        /** Convert any Kotlin/Java value to JSValue */
        fun from(value: Any?): JSValue = when (value) {
            null -> Null
            is kotlin.String -> String(value)
            is kotlin.Number -> Number(value.toDouble())
            is kotlin.Boolean -> Boolean(value)
            is JSEngineObject -> Object(value)
            is List<*> -> Array(value.map { from(it) })
            else -> throw IllegalArgumentException("Cannot convert ${value::class} to JSValue")
        }
    }
}
```

### 2. Main Context Class

**File:** `core/JSEngineContext.kt`

```kotlin
package com.hyprmx.android.jsengine.core

import com.hyprmx.android.jsengine.types.JSValue
import kotlinx.coroutines.*
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * Main entry point for JavaScript execution.
 *
 * Basic usage:
 * ```kotlin
 * JSEngineContext.create().use { context ->
 *     val result = context.evaluate("2 + 2")
 *     println(result) // JSValue.Number(4.0)
 * }
 * ```
 *
 * Async usage:
 * ```kotlin
 * val context = JSEngineContext.create()
 * val result = context.evaluateAsync("fetchData()") // Suspends, doesn't block
 * context.close()
 * ```
 */
class JSEngineContext private constructor(
    private val contextPtr: Long,
    val engine: Engine
) : Closeable {

    enum class Engine { QUICKJS, DUKTAPE }

    private var closed = false
    private val lock = Any()

    // Native method declarations
    private external fun nativeEvaluate(contextPtr: Long, script: String): Any?
    private external fun nativeEvaluateModule(contextPtr: Long, script: String, moduleName: String): Any?
    private external fun nativeGetGlobalObject(contextPtr: Long): Any?
    private external fun nativeSetGlobalProperty(contextPtr: Long, name: String, value: Any?)
    private external fun nativeGarbageCollect(contextPtr: Long)
    private external fun nativeGetHeapSize(contextPtr: Long): Long
    private external fun nativeClose(contextPtr: Long)
    private external fun nativeHasPendingJobs(contextPtr: Long): Boolean
    private external fun nativeRunPendingJobs(contextPtr: Long): Int

    /**
     * Evaluate JavaScript code synchronously.
     *
     * Warning: This blocks the calling thread. For main thread safety,
     * use [evaluateAsync] instead.
     *
     * @param script JavaScript code to execute
     * @return Result as [JSValue]
     * @throws JSEngineException if evaluation fails
     */
    fun evaluate(script: String): JSValue {
        // Note: checkNotClosed() runs INSIDE the lock (here and in every method
        // below) — checking outside races with close() on another thread and
        // can hit a freed native pointer.
        synchronized(lock) {
            checkNotClosed()
            val result = nativeEvaluate(contextPtr, script)
            return convertToJSValue(result)
        }
    }

    /**
     * Evaluate JavaScript code asynchronously.
     *
     * This is the recommended way to execute JavaScript as it never blocks
     * the main thread.
     *
     * @param script JavaScript code to execute
     * @param dispatcher Coroutine dispatcher (default: Dispatchers.Default)
     * @return Result as [JSValue]
     */
    suspend fun evaluateAsync(
        script: String,
        dispatcher: CoroutineContext = Dispatchers.Default
    ): JSValue = withContext(dispatcher) {
        evaluate(script)
    }

    /**
     * Evaluate with timeout. Returns null if timeout exceeded.
     */
    suspend fun evaluateWithTimeout(
        script: String,
        timeoutMs: Long,
        dispatcher: CoroutineContext = Dispatchers.Default
    ): JSValue? = withTimeoutOrNull(timeoutMs) {
        evaluateAsync(script, dispatcher)
    }

    /**
     * Get the global object (equivalent to `globalThis` in JS).
     */
    fun getGlobalObject(): JSValue.Object {
        synchronized(lock) {
            checkNotClosed()
            val result = nativeGetGlobalObject(contextPtr)
            return JSValue.Object(result as JSEngineObject)
        }
    }

    /**
     * Set a global property.
     *
     * ```kotlin
     * context.setGlobal("myValue", 42)
     * context.setGlobal("myCallback") { args ->
     *     println("Called with: ${args.joinToString()}")
     * }
     * ```
     */
    fun setGlobal(name: String, value: Any?) {
        synchronized(lock) {
            checkNotClosed()
            nativeSetGlobalProperty(contextPtr, name, value)
        }
    }

    /**
     * Operator for setting globals: `context["name"] = value`
     */
    operator fun set(name: String, value: Any?) = setGlobal(name, value)

    /**
     * Operator for getting globals: `context["name"]`
     */
    operator fun get(name: String): JSValue = getGlobalObject()[name]

    /**
     * Check if there are pending async jobs (promises, etc.)
     */
    fun hasPendingJobs(): Boolean {
        synchronized(lock) {
            checkNotClosed()
            return nativeHasPendingJobs(contextPtr)
        }
    }

    /**
     * Run pending jobs (promises, async callbacks).
     * @return Number of jobs executed
     */
    fun runPendingJobs(): Int {
        synchronized(lock) {
            checkNotClosed()
            return nativeRunPendingJobs(contextPtr)
        }
    }

    /**
     * Run all pending jobs until none remain.
     */
    suspend fun drainPendingJobs(
        dispatcher: CoroutineContext = Dispatchers.Default
    ) = withContext(dispatcher) {
        while (hasPendingJobs()) {
            runPendingJobs()
            yield() // Allow cancellation
        }
    }

    /**
     * Force garbage collection.
     */
    fun gc() {
        synchronized(lock) {
            checkNotClosed()
            nativeGarbageCollect(contextPtr)
        }
    }

    /**
     * Get current heap size in bytes.
     */
    val heapSize: Long
        get() {
            synchronized(lock) {
                checkNotClosed()
                return nativeGetHeapSize(contextPtr)
            }
        }

    /**
     * Close the context and release native resources.
     */
    override fun close() {
        if (closed) return
        synchronized(lock) {
            if (closed) return
            closed = true
            nativeClose(contextPtr)
        }
    }

    private fun checkNotClosed() {
        if (closed) throw IllegalStateException("JSEngineContext is closed")
    }

    private fun convertToJSValue(value: Any?): JSValue = when (value) {
        null -> JSValue.Null
        is String -> JSValue.String(value)
        is Number -> JSValue.Number(value.toDouble())
        is Boolean -> JSValue.Boolean(value)
        is JSEngineObject -> {
            when {
                value.isFunction() -> JSValue.Function(value)
                value.isArray() -> JSValue.Array(value.toList().map { convertToJSValue(it) })
                else -> JSValue.Object(value)
            }
        }
        is List<*> -> JSValue.Array(value.map { convertToJSValue(it) })
        else -> throw JSEngineException("Unknown type: ${value::class}")
    }

    companion object {
        init {
            System.loadLibrary("jsengine")
        }

        private external fun nativeCreate(useQuickJS: Boolean): Long

        /**
         * Create a new JavaScript engine context.
         *
         * @param engine Which engine to use (default: QuickJS)
         * @return New context instance
         */
        fun create(engine: Engine = Engine.QUICKJS): JSEngineContext {
            val ptr = nativeCreate(engine == Engine.QUICKJS)
            return JSEngineContext(ptr, engine)
        }

        /**
         * Create context using builder DSL.
         *
         * ```kotlin
         * val context = JSEngineContext.build {
         *     engine = Engine.QUICKJS
         *     // Future: maxHeapSize, timeout, etc.
         * }
         * ```
         */
        inline fun build(block: Builder.() -> Unit): JSEngineContext {
            return Builder().apply(block).build()
        }
    }

    class Builder {
        var engine: Engine = Engine.QUICKJS
        // Future options:
        // var maxHeapSize: Long = 16 * 1024 * 1024
        // var maxExecutionTime: Long = 0 // 0 = unlimited
        // var moduleLoader: ModuleLoader? = null

        fun build(): JSEngineContext = create(engine)
    }
}
```

### 3. Extension Functions

**File:** `extensions/ContextExtensions.kt`

```kotlin
package com.hyprmx.android.jsengine.extensions

import com.hyprmx.android.jsengine.core.JSEngineContext
import com.hyprmx.android.jsengine.types.JSValue

/**
 * Evaluate and cast to specific type.
 */
inline fun <reified T : JSValue> JSEngineContext.evaluateAs(script: String): T {
    val result = evaluate(script)
    return result as? T
        ?: throw ClassCastException("Expected ${T::class.simpleName}, got ${result::class.simpleName}")
}

/**
 * Evaluate and get string result.
 */
fun JSEngineContext.evaluateString(script: String): String =
    evaluateAs<JSValue.String>(script).value

/**
 * Evaluate and get number result.
 */
fun JSEngineContext.evaluateNumber(script: String): Double =
    evaluateAs<JSValue.Number>(script).value

/**
 * Evaluate and get boolean result.
 */
fun JSEngineContext.evaluateBoolean(script: String): Boolean =
    evaluateAs<JSValue.Boolean>(script).value

/**
 * Execute a JavaScript function by name with arguments.
 *
 * Resolves the function reference and invokes it directly — no global
 * namespace pollution, no string evaluation, safe for concurrent callers.
 */
fun JSEngineContext.call(functionName: String, vararg args: Any?): JSValue {
    val fn = getGlobalObject()[functionName] as? JSValue.Function
        ?: throw JSEngineException("Global function not found: $functionName")
    return fn(*args)
}

// NOTE: A polling-based `observeGlobal(name): Flow<JSValue>` was considered and
// rejected — a delay() loop reading properties drains battery while pretending
// to be reactive. Reactive updates must be push-based: expose a Kotlin callback
// to JavaScript and let JS invoke it on change.

/**
 * Use context with automatic cleanup.
 */
inline fun <R> JSEngineContext.use(block: (JSEngineContext) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}
```

### 4. Java Compatibility Layer

**File:** `compat/JavaCompat.kt`

```kotlin
package com.hyprmx.android.jsengine.compat

import com.hyprmx.android.jsengine.core.JSEngineContext
import com.hyprmx.android.jsengine.types.JSValue
import java.util.concurrent.CompletableFuture

/**
 * Java-friendly API for projects that can't use Kotlin coroutines.
 */
object JSEngineCompat {

    /**
     * Evaluate asynchronously, returning a CompletableFuture.
     *
     * Java usage:
     * ```java
     * CompletableFuture<JSValue> future = JSEngineCompat.evaluateAsync(context, "2+2");
     * future.thenAccept(result -> System.out.println(result));
     * ```
     */
    @JvmStatic
    fun evaluateAsync(context: JSEngineContext, script: String): CompletableFuture<JSValue> {
        return CompletableFuture.supplyAsync {
            context.evaluate(script)
        }
    }

    /**
     * Convert JSValue to Java Object for Java interop.
     */
    @JvmStatic
    fun toJavaObject(value: JSValue): Any? = when (value) {
        is JSValue.String -> value.value
        is JSValue.Number -> value.value
        is JSValue.Boolean -> value.value
        is JSValue.Object -> value.value
        is JSValue.Array -> value.value.map { toJavaObject(it) }
        is JSValue.Function -> value.value
        JSValue.Null -> null
        JSValue.Undefined -> null
    }
}
```

---

## Migration Strategy

### Phase 1: Add Kotlin Module (Non-breaking)

1. Create new `jsengine-kotlin` module alongside `jsengine-java`
2. Add Kotlin wrappers that delegate to Java implementation
3. Users can opt-in to new API

### Phase 2: Deprecate Java API

1. Mark Java classes as `@Deprecated`
2. Provide migration guide
3. Support both APIs for 1-2 minor versions

### Phase 3: Remove Java API (v2.0.0)

1. Remove deprecated Java classes
2. Kotlin API becomes the only API
3. Java compat layer provides interop

### Backwards Compatibility

```kotlin
// Deprecated Java API wrapper for migration period
@Deprecated(
    message = "Use JSEngineContext directly",
    replaceWith = ReplaceWith("JSEngineContext.create()")
)
object QuackContext {
    @JvmStatic
    fun create(useQuickJS: Boolean = true): JSEngineContext {
        return JSEngineContext.create(
            if (useQuickJS) JSEngineContext.Engine.QUICKJS
            else JSEngineContext.Engine.DUKTAPE
        )
    }
}
```

---

## Dependencies

### New Dependencies Required

```gradle
dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:2.0.x"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.x"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.x"
}
```

### Kotlin Version Upgrade

Current: `1.6.21`
Required: `2.0.x` (current stable line — pin exact versions when work starts)

---

## Testing Requirements

1. **Unit Tests**
   - JSValue sealed class behavior
   - Type coercion correctness
   - Extension functions
   - Java compat layer

2. **Integration Tests**
   - End-to-end evaluation
   - Coroutine behavior
   - Thread safety

3. **Migration Tests**
   - Verify old Java code still works during transition
   - Verify deprecation warnings appear

---

## Success Criteria

- [ ] All existing tests pass
- [ ] New Kotlin tests cover 90%+ of new code
- [ ] Exhaustive `when` expressions compile without `else`
- [ ] Coroutine functions work correctly
- [ ] Java compat layer allows gradual migration
- [ ] Documentation updated with Kotlin examples
- [ ] Migration guide complete

---

## Open Questions

1. **Kotlin version**: Require the current 2.0.x stable line, or maintain older compat for consumers?
2. **Coroutine scope**: Should context have its own CoroutineScope?
3. **Flow support**: How deep should reactive integration go?
4. **Multiplatform**: Should we consider KMP for future desktop support?

---

## Related Documents

- [FUTURE_RELEASES.md](./FUTURE_RELEASES.md) - Release roadmap
- [WORKER_THREADS_PLAN.md](./WORKER_THREADS_PLAN.md) - Worker thread implementation
- [ES_MODULES_PLAN.md](./ES_MODULES_PLAN.md) - ES module support

---

**Last Updated:** 2026-07-02
**Status:** Planning
