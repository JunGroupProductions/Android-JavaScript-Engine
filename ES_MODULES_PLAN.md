# ES Modules Plan

## Overview

**Target Version:** 2.2.0
**Priority:** Medium
**Effort:** 12-16 hours
**Breaking Changes:** No (additive API)
**Prerequisites:** v2.0.0 (Kotlin API foundation)

> Ships on the **frozen 2020-07-05 engine** — dynamic `import()` and top-level await are unavailable until the v3.0.0 engine update; bytecode module caching arrives with v3.1.0.

This document outlines the plan to implement ES6 module support in JSEngine, enabling `import`/`export` syntax and modular JavaScript code organization.

---

## Goals

1. **ES6 Modules** - Full `import`/`export` syntax support
2. **Custom Loaders** - Load modules from assets, filesystem, network, etc.
3. **Module Caching** - Compile once, reuse many times
4. **Path Resolution** - Standard relative/absolute path handling
5. **Tree Shaking** - Only load what's needed

---

## How ES Modules Work in QuickJS

QuickJS has native ES module support, but requires:

1. A **module normalizer** callback to resolve import paths
2. A **module loader** callback to fetch source code
3. Using `JS_Eval` with `JS_EVAL_TYPE_MODULE` flag
4. Proper module caching to avoid re-parsing

### QuickJS Module Flow

```
┌─────────────────────────────────────────────────────────────┐
│                      Kotlin/Java                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  context.setModuleLoader { path ->                   │   │
│  │      loadFromAssets(path)                            │   │
│  │  }                                                   │   │
│  │                                                      │   │
│  │  context.evaluateModule("""                          │   │
│  │      import { foo } from './utils.js'                │   │
│  │      foo()                                           │   │
│  │  """)                                                │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                         JNI                                 │
│  JS_SetModuleLoaderFunc(rt, normalizer, loader, opaque)     │
│  JS_Eval(ctx, code, len, name, JS_EVAL_TYPE_MODULE)         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       QuickJS                               │
│  1. Parse import statement                                  │
│  2. Call normalizer(baseName, importName) → resolved path   │
│  3. Call loader(ctx, resolvedPath) → source code            │
│  4. Compile and cache module                                │
│  5. Link and execute                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Architecture

### Module Resolution Flow

```
import { foo } from './utils.js'
        │
        ▼
┌───────────────────┐
│ Module Normalizer │  Converts relative to absolute path
│ "./utils.js"      │  baseName: "main.js"
│      ↓            │  importName: "./utils.js"
│ "lib/utils.js"    │  result: "lib/utils.js"
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  Module Loader    │  Fetches source code
│ "lib/utils.js"    │
│      ↓            │
│ "export func..." │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  QuickJS Compile  │  Compile to bytecode (cached)
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  Module Linking   │  Resolve all imports recursively
└───────────────────┘
        │
        ▼
┌───────────────────┐
│  Execution        │  Run module code
└───────────────────┘
```

---

## Implementation

### 1. Module Loader Interface

**File:** `modules/ModuleLoader.kt`

```kotlin
package com.hyprmx.android.jsengine.modules

/**
 * Interface for loading ES modules.
 *
 * Implement this to provide custom module resolution logic.
 *
 * ```kotlin
 * val loader = ModuleLoader { moduleName ->
 *     when {
 *         moduleName.startsWith("./") -> loadFromAssets(moduleName)
 *         moduleName.startsWith("http") -> fetchFromNetwork(moduleName)
 *         else -> loadFromBuiltins(moduleName)
 *     }
 * }
 * ```
 */
fun interface ModuleLoader {
    /**
     * Load module source code by name.
     *
     * @param moduleName The resolved module name (after normalization)
     * @return Module source code, or null if module not found
     */
    fun load(moduleName: String): String?
}

/**
 * Interface for normalizing module paths.
 *
 * Converts relative imports to absolute paths.
 *
 * ```kotlin
 * val normalizer = ModuleNormalizer { baseName, importName ->
 *     if (importName.startsWith("./")) {
 *         val baseDir = baseName.substringBeforeLast("/")
 *         "$baseDir/${importName.removePrefix("./")}"
 *     } else {
 *         importName
 *     }
 * }
 * ```
 */
fun interface ModuleNormalizer {
    /**
     * Normalize a module import path.
     *
     * @param baseName The name of the module doing the import
     * @param importName The raw import path from the source
     * @return Normalized absolute module name
     */
    fun normalize(baseName: String, importName: String): String
}

/**
 * Exception thrown when a module cannot be found.
 */
class ModuleNotFoundException(
    val moduleName: String,
    message: String = "Module not found: $moduleName"
) : Exception(message)

/**
 * Exception thrown when a module fails to compile.
 */
class ModuleCompilationException(
    val moduleName: String,
    val sourceError: String,
    message: String = "Failed to compile module '$moduleName': $sourceError"
) : Exception(message)
```

### 2. Built-in Module Loaders

**File:** `modules/ModuleLoaders.kt`

```kotlin
package com.hyprmx.android.jsengine.modules

import android.content.Context
import java.io.File

/**
 * Pre-built module loaders for common use cases.
 */
object ModuleLoaders {

    /**
     * Load modules from Android assets folder.
     *
     * ```kotlin
     * context.setModuleLoader(ModuleLoaders.fromAssets(androidContext, "js"))
     * // Loads from: assets/js/moduleName.js
     * ```
     */
    fun fromAssets(
        context: Context,
        basePath: String = ""
    ): ModuleLoader = ModuleLoader { moduleName ->
        try {
            val path = if (basePath.isEmpty()) moduleName else "$basePath/$moduleName"
            context.assets.open(path).bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load modules from the file system.
     *
     * ```kotlin
     * context.setModuleLoader(ModuleLoaders.fromFileSystem(File("/data/js")))
     * ```
     */
    fun fromFileSystem(baseDir: File): ModuleLoader = ModuleLoader { moduleName ->
        val file = File(baseDir, moduleName)
        if (file.exists() && file.isFile) {
            file.readText()
        } else {
            null
        }
    }

    /**
     * Load modules from a map (useful for bundled/embedded modules).
     *
     * ```kotlin
     * context.setModuleLoader(ModuleLoaders.fromMap(mapOf(
     *     "utils.js" to "export function log(msg) { console.log(msg); }",
     *     "math.js" to "export const PI = 3.14159;"
     * )))
     * ```
     */
    fun fromMap(modules: Map<String, String>): ModuleLoader = ModuleLoader { moduleName ->
        modules[moduleName]
    }

    /**
     * Chain multiple loaders - tries each in order until one succeeds.
     *
     * ```kotlin
     * context.setModuleLoader(ModuleLoaders.chain(
     *     ModuleLoaders.fromMap(builtins),
     *     ModuleLoaders.fromAssets(ctx, "js"),
     *     ModuleLoaders.fromFileSystem(cacheDir)
     * ))
     * ```
     */
    fun chain(vararg loaders: ModuleLoader): ModuleLoader = ModuleLoader { moduleName ->
        loaders.asSequence()
            .mapNotNull { it.load(moduleName) }
            .firstOrNull()
    }

    /**
     * Load modules with caching.
     *
     * Wraps another loader and caches results in memory.
     */
    fun cached(delegate: ModuleLoader): ModuleLoader {
        // containsKey check instead of getOrPut: getOrPut never stores null,
        // so a missing module would re-hit the delegate on every import.
        val cache = mutableMapOf<String, String?>()
        return ModuleLoader { moduleName ->
            if (cache.containsKey(moduleName)) {
                cache[moduleName]
            } else {
                delegate.load(moduleName).also { cache[moduleName] = it }
            }
        }
    }

    /**
     * Load modules with logging for debugging.
     */
    fun withLogging(
        delegate: ModuleLoader,
        tag: String = "ModuleLoader"
    ): ModuleLoader = ModuleLoader { moduleName ->
        android.util.Log.d(tag, "Loading module: $moduleName")
        val result = delegate.load(moduleName)
        if (result != null) {
            android.util.Log.d(tag, "Loaded module: $moduleName (${result.length} chars)")
        } else {
            android.util.Log.w(tag, "Module not found: $moduleName")
        }
        result
    }
}
```

### 3. Built-in Module Normalizers

**File:** `modules/ModuleNormalizers.kt`

```kotlin
package com.hyprmx.android.jsengine.modules

/**
 * Pre-built module normalizers.
 */
object ModuleNormalizers {

    /**
     * Standard normalizer that handles relative paths.
     *
     * - `./foo` -> relative to current module
     * - `../foo` -> parent directory
     * - `foo` -> absolute (no change)
     */
    val standard: ModuleNormalizer = ModuleNormalizer { baseName, importName ->
        when {
            importName.startsWith("./") -> {
                val baseDir = baseName.substringBeforeLast("/", "")
                val relative = importName.removePrefix("./")
                if (baseDir.isEmpty()) relative else "$baseDir/$relative"
            }
            importName.startsWith("../") -> {
                val baseParts = baseName.split("/").dropLast(1).toMutableList()
                var remaining = importName
                while (remaining.startsWith("../")) {
                    if (baseParts.isNotEmpty()) baseParts.removeLast()
                    remaining = remaining.removePrefix("../")
                }
                (baseParts + remaining).joinToString("/")
            }
            else -> importName
        }
    }

    /**
     * Normalizer that adds .js extension if missing.
     */
    val withJsExtension: ModuleNormalizer = ModuleNormalizer { baseName, importName ->
        val normalized = standard.normalize(baseName, importName)
        if (normalized.endsWith(".js") || normalized.contains(".")) {
            normalized
        } else {
            "$normalized.js"
        }
    }

    /**
     * Node.js style normalizer (index.js support).
     *
     * - `./foo` -> tries `./foo.js`, then `./foo/index.js`
     */
    fun nodeStyle(loader: ModuleLoader): ModuleNormalizer = ModuleNormalizer { baseName, importName ->
        val normalized = standard.normalize(baseName, importName)

        // Try exact match first
        if (loader.load(normalized) != null) {
            return@ModuleNormalizer normalized
        }

        // Try with .js extension
        val withJs = "$normalized.js"
        if (loader.load(withJs) != null) {
            return@ModuleNormalizer withJs
        }

        // Try index.js
        val indexJs = "$normalized/index.js"
        if (loader.load(indexJs) != null) {
            return@ModuleNormalizer indexJs
        }

        // Return original, will fail at load time
        normalized
    }
}
```

### 4. Context Extensions for Modules

**File:** `modules/ModuleExtensions.kt`

```kotlin
package com.hyprmx.android.jsengine.modules

import com.hyprmx.android.jsengine.core.JSEngineContext
import com.hyprmx.android.jsengine.types.JSValue

/**
 * Module configuration holder (per context).
 */
internal class ModuleConfig {
    var loader: ModuleLoader? = null
    var normalizer: ModuleNormalizer = ModuleNormalizers.standard
    val compiledModules: MutableSet<String> = mutableSetOf()
}

// Keyed by the context INSTANCE, not hashCode() — hash collisions would
// cross-contaminate contexts, and Int keys leak forever. WeakHashMap lets
// entries die with the context. (In the v2.0 rewrite, prefer making this a
// plain field on JSEngineContext and delete this map entirely.)
private val moduleConfigs =
    java.util.Collections.synchronizedMap(java.util.WeakHashMap<JSEngineContext, ModuleConfig>())

internal fun JSEngineContext.getModuleConfig(): ModuleConfig {
    return moduleConfigs.getOrPut(this) { ModuleConfig() }
}

internal fun JSEngineContext.clearModuleConfig() {
    moduleConfigs.remove(this)
}

/**
 * Set the module loader for this context.
 *
 * ```kotlin
 * context.setModuleLoader { moduleName ->
 *     assets.open("js/$moduleName").reader().readText()
 * }
 * ```
 */
fun JSEngineContext.setModuleLoader(loader: ModuleLoader) {
    val config = getModuleConfig()
    config.loader = loader
    nativeSetModuleLoader(loader, config.normalizer)
}

/**
 * Set the module normalizer for this context.
 */
fun JSEngineContext.setModuleNormalizer(normalizer: ModuleNormalizer) {
    val config = getModuleConfig()
    config.normalizer = normalizer
    config.loader?.let { loader ->
        nativeSetModuleLoader(loader, normalizer)
    }
}

/**
 * Configure modules using a builder DSL.
 *
 * ```kotlin
 * context.configureModules {
 *     loader = ModuleLoaders.fromAssets(androidContext, "js")
 *     normalizer = ModuleNormalizers.withJsExtension
 * }
 * ```
 */
inline fun JSEngineContext.configureModules(block: ModuleConfigBuilder.() -> Unit) {
    val builder = ModuleConfigBuilder().apply(block)
    builder.loader?.let { setModuleLoader(it) }
    builder.normalizer?.let { setModuleNormalizer(it) }
}

class ModuleConfigBuilder {
    var loader: ModuleLoader? = null
    var normalizer: ModuleNormalizer? = null
}

/**
 * Evaluate code as an ES module.
 *
 * ```kotlin
 * context.evaluateModule("""
 *     import { helper } from './utils.js';
 *     export default helper();
 * """, moduleName = "main.js")
 * ```
 */
fun JSEngineContext.evaluateModule(
    code: String,
    moduleName: String = "<eval>"
): JSValue {
    if (getModuleConfig().loader == null) {
        throw IllegalStateException(
            "Module loader not configured. Call setModuleLoader() first."
        )
    }
    return nativeEvaluateModule(code, moduleName)
}

/**
 * Evaluate module asynchronously.
 */
suspend fun JSEngineContext.evaluateModuleAsync(
    code: String,
    moduleName: String = "<eval>"
): JSValue = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    evaluateModule(code, moduleName)
}

/**
 * Import a module by name and get its exports.
 *
 * ```kotlin
 * val mathModule = context.importModule("./math.js")
 * val pi = mathModule["PI"] // JSValue.Number(3.14159)
 * ```
 */
fun JSEngineContext.importModule(moduleName: String): JSValue.Object {
    val result = evaluateModule(
        "import * as m from '$moduleName'; m;",
        moduleName = "<import:$moduleName>"
    )
    return result as? JSValue.Object
        ?: throw ModuleNotFoundException(moduleName, "Module did not return an object")
}

/**
 * Import specific exports from a module.
 *
 * ```kotlin
 * val (add, subtract) = context.importFrom("./math.js", "add", "subtract")
 * ```
 */
fun JSEngineContext.importFrom(
    moduleName: String,
    vararg exports: String
): List<JSValue> {
    val module = importModule(moduleName)
    return exports.map { export ->
        module[export]
    }
}

// Native method declarations
private external fun JSEngineContext.nativeSetModuleLoader(
    loader: ModuleLoader,
    normalizer: ModuleNormalizer
)

private external fun JSEngineContext.nativeEvaluateModule(
    code: String,
    moduleName: String
): JSValue
```

### 5. JNI Implementation

**File:** `jsengine-jni/src/main/jni/quickjs-jni/QuickJSModules.cpp`

```cpp
#include <jni.h>
#include "quickjs.h"
#include <string>
#include <map>
#include <mutex>
#include <android/log.h>

#define LOG_TAG "JSEngine-Modules"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Per-context module configuration.
// IMPORTANT: never cache a JNIEnv* — it is thread-local and per-invocation.
// Cache the JavaVM* and acquire the env at each callback; a cached env used
// from another thread is undefined behavior (crashes).
struct ModuleContext {
    JavaVM* vm;
    jobject loader;     // global ref to ModuleLoader instance
    jobject normalizer; // global ref to ModuleNormalizer instance
    jmethodID loadMethod;
    jmethodID normalizeMethod;

    JNIEnv* getEnv() {
        JNIEnv* env = nullptr;
        if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
            vm->AttachCurrentThread(&env, nullptr);
        }
        return env;
    }
};

// Store module context per JSRuntime.
// Guarded by g_moduleContextsMutex — registration and lookup can happen from
// different threads.
static std::mutex g_moduleContextsMutex;
static std::map<JSRuntime*, ModuleContext*> g_moduleContexts;

// Module normalizer callback for QuickJS
static char* js_module_normalizer(
    JSContext *ctx,
    const char *base_name,
    const char *name,
    void *opaque
) {
    JSRuntime *rt = JS_GetRuntime(ctx);
    ModuleContext *mc = nullptr;
    {
        std::lock_guard<std::mutex> guard(g_moduleContextsMutex);
        auto it = g_moduleContexts.find(rt);
        if (it != g_moduleContexts.end()) mc = it->second;
    }
    if (!mc || !mc->normalizer) {
        // No normalizer, return as-is
        return js_strdup(ctx, name);
    }

    JNIEnv *env = mc->getEnv(); // acquired per call — never cached

    jstring jBaseName = env->NewStringUTF(base_name ? base_name : "");
    jstring jName = env->NewStringUTF(name);

    jstring jResult = (jstring)env->CallObjectMethod(
        mc->normalizer,
        mc->normalizeMethod,
        jBaseName,
        jName
    );

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("Exception in module normalizer");
        return js_strdup(ctx, name);
    }

    const char *result = env->GetStringUTFChars(jResult, nullptr);
    char *dup = js_strdup(ctx, result);
    env->ReleaseStringUTFChars(jResult, result);

    env->DeleteLocalRef(jBaseName);
    env->DeleteLocalRef(jName);
    env->DeleteLocalRef(jResult);

    LOGD("Normalized: %s + %s -> %s", base_name, name, dup);

    return dup;
}

// Module loader callback for QuickJS
static JSModuleDef* js_module_loader(
    JSContext *ctx,
    const char *module_name,
    void *opaque
) {
    JSRuntime *rt = JS_GetRuntime(ctx);
    ModuleContext *mc = nullptr;
    {
        std::lock_guard<std::mutex> guard(g_moduleContextsMutex);
        auto it = g_moduleContexts.find(rt);
        if (it != g_moduleContexts.end()) mc = it->second;
    }
    if (!mc || !mc->loader) {
        JS_ThrowReferenceError(ctx, "Module loader not configured");
        return nullptr;
    }

    JNIEnv *env = mc->getEnv(); // acquired per call — never cached

    jstring jModuleName = env->NewStringUTF(module_name);

    jstring jSource = (jstring)env->CallObjectMethod(
        mc->loader,
        mc->loadMethod,
        jModuleName
    );

    env->DeleteLocalRef(jModuleName);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        JS_ThrowReferenceError(ctx, "Exception in module loader for: %s", module_name);
        return nullptr;
    }

    if (jSource == nullptr) {
        JS_ThrowReferenceError(ctx, "Module not found: %s", module_name);
        return nullptr;
    }

    const char *source = env->GetStringUTFChars(jSource, nullptr);
    size_t source_len = strlen(source);

    LOGD("Loading module: %s (%zu bytes)", module_name, source_len);

    // Compile the module
    JSValue func_val = JS_Eval(
        ctx,
        source,
        source_len,
        module_name,
        JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY
    );

    env->ReleaseStringUTFChars(jSource, source);
    env->DeleteLocalRef(jSource);

    if (JS_IsException(func_val)) {
        // Exception already set by JS_Eval
        return nullptr;
    }

    // Get module definition from compiled function
    JSModuleDef *m = (JSModuleDef *)JS_VALUE_GET_PTR(func_val);
    JS_FreeValue(ctx, func_val);

    return m;
}

// JNI: Set module loader and normalizer
extern "C" JNIEXPORT void JNICALL
Java_com_hyprmx_android_jsengine_modules_ModuleExtensionsKt_nativeSetModuleLoader(
    JNIEnv *env,
    jobject thiz,
    jlong ctx_ptr,
    jobject loader,
    jobject normalizer
) {
    JSContext *ctx = (JSContext *)ctx_ptr;
    JSRuntime *rt = JS_GetRuntime(ctx);

    std::lock_guard<std::mutex> guard(g_moduleContextsMutex);

    // Clean up existing config
    auto it = g_moduleContexts.find(rt);
    if (it != g_moduleContexts.end()) {
        ModuleContext *old = it->second;
        if (old->loader) env->DeleteGlobalRef(old->loader);
        if (old->normalizer) env->DeleteGlobalRef(old->normalizer);
        delete old;
    }

    // Create new config — store the JavaVM, never the JNIEnv
    ModuleContext *mc = new ModuleContext();
    env->GetJavaVM(&mc->vm);
    mc->loader = env->NewGlobalRef(loader);
    mc->normalizer = env->NewGlobalRef(normalizer);

    // Cache method IDs
    jclass loaderClass = env->GetObjectClass(loader);
    mc->loadMethod = env->GetMethodID(
        loaderClass,
        "load",
        "(Ljava/lang/String;)Ljava/lang/String;"
    );

    jclass normalizerClass = env->GetObjectClass(normalizer);
    mc->normalizeMethod = env->GetMethodID(
        normalizerClass,
        "normalize",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    );

    g_moduleContexts[rt] = mc;

    // Register with QuickJS
    JS_SetModuleLoaderFunc(rt, js_module_normalizer, js_module_loader, nullptr);
}

// JNI: Evaluate as module
extern "C" JNIEXPORT jobject JNICALL
Java_com_hyprmx_android_jsengine_modules_ModuleExtensionsKt_nativeEvaluateModule(
    JNIEnv *env,
    jobject thiz,
    jlong ctx_ptr,
    jstring script,
    jstring module_name
) {
    JSContext *ctx = (JSContext *)ctx_ptr;
    // No env bookkeeping needed — callbacks acquire their own env via
    // ModuleContext::getEnv().

    const char *script_str = env->GetStringUTFChars(script, nullptr);
    const char *name_str = env->GetStringUTFChars(module_name, nullptr);

    JSValue result = JS_Eval(
        ctx,
        script_str,
        strlen(script_str),
        name_str,
        JS_EVAL_TYPE_MODULE
    );

    env->ReleaseStringUTFChars(script, script_str);
    env->ReleaseStringUTFChars(module_name, name_str);

    if (JS_IsException(result)) {
        JSValue exception = JS_GetException(ctx);
        // Convert exception to Java exception
        // ... (exception handling code)
        JS_FreeValue(ctx, exception);
        return nullptr;
    }

    // Convert result to Java object
    // ... (conversion code using existing jsValueToJava)

    return jsValueToJava(env, ctx, result);
}

// Cleanup when context is destroyed.
// NOTE: do NOT define a new Java_..._nativeClose — the JNI close entry point
// already exists. Instead, call this helper from the EXISTING close path
// before JS_FreeContext:
static void module_context_cleanup(JNIEnv *env, JSRuntime *rt) {
    std::lock_guard<std::mutex> guard(g_moduleContextsMutex);
    auto it = g_moduleContexts.find(rt);
    if (it != g_moduleContexts.end()) {
        ModuleContext *mc = it->second;
        if (mc->loader) env->DeleteGlobalRef(mc->loader);
        if (mc->normalizer) env->DeleteGlobalRef(mc->normalizer);
        delete mc;
        g_moduleContexts.erase(it);
    }
}
```

---

## Usage Examples

### Example 1: Load Modules from Assets

```kotlin
// Setup
val context = JSEngineContext.create()

context.configureModules {
    loader = ModuleLoaders.fromAssets(androidContext, "js")
    normalizer = ModuleNormalizers.withJsExtension
}

// assets/js/math.js:
// export const PI = 3.14159;
// export function square(x) { return x * x; }

// Usage
val result = context.evaluateModule("""
    import { PI, square } from './math';
    square(PI);
""")

println(result) // JSValue.Number(9.8696...)
```

### Example 2: Inline Modules with Map Loader

```kotlin
val context = JSEngineContext.create()

context.setModuleLoader(ModuleLoaders.fromMap(mapOf(
    "utils.js" to """
        export function greet(name) {
            return `Hello, ${'$'}{name}!`;
        }
    """,
    "constants.js" to """
        export const VERSION = "1.0.0";
        export const APP_NAME = "MyApp";
    """
)))

val greeting = context.evaluateModule("""
    import { greet } from 'utils.js';
    import { APP_NAME } from 'constants.js';
    greet(APP_NAME);
""")

println(greeting) // JSValue.String("Hello, MyApp!")
```

### Example 3: Chained Loaders (Builtins + Assets + Cache)

```kotlin
val builtins = mapOf(
    "console.js" to """
        export function log(...args) {
            _nativeLog(args.join(' '));
        }
    """
)

context.setModuleLoader(ModuleLoaders.chain(
    ModuleLoaders.fromMap(builtins),           // Try builtins first
    ModuleLoaders.fromAssets(ctx, "js"),       // Then assets
    ModuleLoaders.fromFileSystem(cacheDir)     // Then cache dir
))
```

### Example 4: Dynamic Module Loading

```kotlin
class DynamicModuleLoader(
    private val context: Context,
    private val cacheDir: File
) : ModuleLoader {

    private val cache = mutableMapOf<String, String>()

    override fun load(moduleName: String): String? {
        // Check memory cache
        cache[moduleName]?.let { return it }

        // Check file cache
        val cacheFile = File(cacheDir, moduleName.replace("/", "_"))
        if (cacheFile.exists()) {
            return cacheFile.readText().also { cache[moduleName] = it }
        }

        // Try assets
        return try {
            context.assets.open("js/$moduleName")
                .bufferedReader()
                .readText()
                .also { cache[moduleName] = it }
        } catch (e: Exception) {
            null
        }
    }
}
```

### Example 5: Import Specific Exports

```kotlin
// math.js exports: add, subtract, multiply, divide, PI

val mathModule = context.importModule("./math.js")
val add = mathModule["add"] as JSValue.Function
val result = add(2, 3) // JSValue.Number(5.0)

// Or import multiple at once
val (multiply, divide) = context.importFrom("./math.js", "multiply", "divide")
```

---

## Module Caching

QuickJS automatically caches compiled modules by name. Once a module is loaded and compiled, subsequent imports use the cached version.

```
First import of './math.js':
  Load source → Parse → Compile → Cache → Execute

Second import of './math.js':
  Cache hit → Execute (no re-parsing)
```

### Cache Invalidation

To reload a module (e.g., during development):

```kotlin
// Option 1: Create new context
context.close()
context = JSEngineContext.create()

// Option 2: Use versioned module names
context.evaluateModule("import './math.js?v=2'")
```

---

## Error Handling

### Module Not Found

```kotlin
try {
    context.evaluateModule("import { foo } from './nonexistent.js'")
} catch (e: ModuleNotFoundException) {
    Log.e(TAG, "Module not found: ${e.moduleName}")
}
```

### Compilation Error

```kotlin
try {
    context.evaluateModule("import { foo } from './broken.js'")
} catch (e: ModuleCompilationException) {
    Log.e(TAG, "Failed to compile ${e.moduleName}: ${e.sourceError}")
}
```

### Circular Dependencies

QuickJS handles circular dependencies automatically using the ES6 module semantics (hoisting exports before execution).

---

## Limitations

These reflect the **frozen 2020-07-05 engine** this release ships on (see the v3.0.0 engine-update policy in FUTURE_RELEASES.md). Items 1 and 2 are lifted by the v3.0.0 QuickJS update — modern QuickJS supports both.

1. **No Dynamic Import** - `import()` expression not supported by the 2020 engine *(lifted at v3.0.0)*
2. **No Top-level Await** - Modules must be synchronous *(lifted at v3.0.0)*
3. **Single Thread** - Module loading happens on calling thread
4. **No Source Maps** - Error line numbers refer to original source

### Future Work

1. **Dynamic Import** - Native support arrives with the v3.0.0 engine update
2. **Module Preloading** - Pre-compile modules at app startup
3. **Bytecode Modules** - Load pre-compiled bytecode instead of source (v3.1.0 — requires the updated engine)
4. **Virtual File System** - Abstract file system for module resolution

---

## Dependencies

```gradle
dependencies {
    // No additional dependencies required
    // Uses existing JSEngine core
}
```

---

## Testing Requirements

1. **Unit Tests**
   - Module loader interface
   - Module normalizer paths
   - Chain loader behavior
   - Cache behavior

2. **Integration Tests**
   - End-to-end module loading
   - Nested imports (A imports B imports C)
   - Circular dependency handling
   - Error cases

3. **Performance Tests**
   - Module caching effectiveness
   - Large module compilation time
   - Many-module loading time

---

## Success Criteria

- [ ] Basic `import`/`export` syntax works
- [ ] Relative paths resolve correctly
- [ ] Assets loader works
- [ ] File system loader works
- [ ] Map loader works
- [ ] Chain loader works
- [ ] Module caching works
- [ ] Errors are properly reported
- [ ] Circular dependencies handled
- [ ] All tests pass

---

## Related Documents

- [FUTURE_RELEASES.md](./FUTURE_RELEASES.md) - Release roadmap
- [KOTLIN_API_PLAN.md](./KOTLIN_API_PLAN.md) - Kotlin API modernization
- [WORKER_THREADS_PLAN.md](./WORKER_THREADS_PLAN.md) - Worker thread support
- [QUICKJS_UPDATE_PLAN.md](./QUICKJS_UPDATE_PLAN.md) - QuickJS update plan

---

**Last Updated:** 2026-07-02
**Status:** Planning
