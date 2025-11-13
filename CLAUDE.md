# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSEngine provides Java and Android bindings to JavaScript engines (QuickJS and Duktape). The library includes **16KB page size support** for modern Android compatibility.

Key features:
- Dual JavaScript engine support (QuickJS ES2020 / Duktape ES5.1)
- Modern Android build configuration (AGP 8.8.0, NDK 28.2.13676358)
- 16KB page alignment for native libraries
- Automatic Java ↔ JavaScript type coercion

## Build Commands

### Assemble Project
```bash
./gradlew assemble
```
Run from project root. Output AAR artifact: `jsengine/jsengine-android/build/outputs/aar/jsengine-android-release.aar`

### Clean Build
```bash
./gradlew clean
```

### Run Tests
```bash
# Android instrumentation tests
./gradlew connectedAndroidTest

# Java unit tests (from jsengine-java module)
./gradlew test
```

### Maven Publishing
```bash
# Publish to local Maven repository (~/.m2/repository)
./gradlew publishToMavenLocal

# Publish to remote repository (requires configuration)
./gradlew publish
```

### Build Specific Module
```bash
./gradlew :jsengine:jsengine-android:assemble
```

## Architecture

### Module Structure

**jsengine/jsengine-java/** - Pure Java implementation of JSEngine APIs
- `JSEngineContext.java` - Main entry point for JavaScript engine interaction
- Contains abstract interfaces and Java-side type coercion logic
- Platform-agnostic JavaScript bindings
- Shared by both Android and desktop implementations via `sourceSets` configuration

**jsengine/jsengine-jni/** - Native JNI bridge implementations
- `context-jni.cpp` - Common JNI context management
- `duktape-jni/` - Duktape engine bindings (C++)
- `quickjs-jni/` - QuickJS engine bindings (C++)
  - `QuickJSContext.cpp` - Main QuickJS JNI implementation
- Handles Java ↔ JavaScript type conversion at native level

**jsengine/jsengine-android/** - Android-specific library module
- Includes Java sources from `jsengine-java` via `sourceSets`
- Native build configuration in `src/main/jni/CMakeLists.txt`
- Outputs AAR with compiled native libraries for Android
- Configured with Maven publishing

**quickjs/** - Upstream QuickJS JavaScript engine source
- Referenced directly by CMake build
- Contains C implementation of QuickJS runtime

**jsengine/JSEngine/** - Sample Android library module (namespace: `com.hyprmx.jsengine.sample`)

### JavaScript Engine Support

The library supports two JavaScript engines:
- **QuickJS** (default) - Modern, lightweight ES2020 engine
- **Duktape** - Older ES5.1 engine (legacy support)

Engine selection via `JSEngineContext.create(boolean useQuickJS)` where `true` = QuickJS, `false` = Duktape.

### Key Classes

**JSEngineContext** (`jsengine-java/src/main/java/com/hyprmx/jsengine/jsengine/JSEngineContext.java`)
- Main API for JavaScript execution
- Manages Java ↔ JavaScript type coercion
- Handles both QuickJS and Duktape engines
- Must be explicitly closed to avoid native memory leaks
- Loads native library: `System.loadLibrary("jsengine")`

**JavaScriptObject** - Proxy interface for JavaScript objects in Java
- Allows method calls and property access from Java side

## Maven Configuration

The project is configured for Maven publishing:

**Group ID**: `com.hyprmx.jsengine`
**Artifact ID**: `jsengine`
**Version**: `1.0.4` (latest)

Publishing is configured in `jsengine/jsengine-android/build.gradle` with both `mavenLocal()` and optional remote repository support.

### Version History

- **1.0.4** (2025-11-13) - Removed all "quack" references from public APIs
  - ⚠️ **BREAKING**: `JavaScriptObject.quackContext` → `JavaScriptObject.jsEngineContext`
  - Internal method renames (quackGet → proxyGet, etc.)
  - All parameter names cleaned up
  - Added LICENSE/NOTICE/THIRD_PARTY_LICENSES to AAR artifact
- **1.0.3** (2025-11-13) - Fixed JNI signature mismatches after refactoring
- **1.0.2** - Initial refactored release
- **1.0.0-1.0.1** - Pre-refactoring versions

## Legal Compliance

JSEngine is Apache 2.0 licensed with proper attribution:

**Source Files:**
- All Java source files include Apache 2.0 license headers
- Original author copyright retained: `Copyright (C) 2015 Koushik Dutta`

**AAR Distribution:**
- LICENSE file (Apache 2.0 full text) included at `assets/licenses/LICENSE`
- NOTICE file documenting modifications included at `assets/licenses/NOTICE`
- THIRD_PARTY_LICENSES file documenting QuickJS (MIT) and Duktape (MIT) at `assets/licenses/THIRD_PARTY_LICENSES`

**Attribution:**
- Original source: https://github.com/koush/quack
- All modifications documented in NOTICE file
- Derivative work status clearly identified

## ProGuard/R8 Support

JSEngine v1.0.4+ includes automatic ProGuard/R8 configuration:

**Automatic Configuration:**
- Consumer ProGuard rules bundled in AAR
- Automatically applied to consuming apps
- No manual configuration needed for library internals
- See `PROGUARD.md` for details

**What's Protected:**
- JNI methods and callbacks
- JavaScript-Java bridge classes
- Interface method signatures
- Exception handling

**User Responsibilities:**
- Add keep rules for YOUR custom classes passed to JavaScript
- Keep rules for interfaces implemented by JavaScript
- Test release builds with ProGuard enabled

**Files:**
- Library rules: `jsengine-android/proguard-rules.pro` (for building library)
- Consumer rules: `jsengine-android/consumer-proguard-rules.pro` (bundled in AAR)

## 16KB Page Size Support

**Critical for Android devices with 16KB page alignment.**

### Required Configuration

In `jsengine/jsengine-android/build.gradle`:
- `compileSdkVersion` and `targetSdkVersion` must match (35)
- `minSdkVersion` must be ≥ 21 (lower breaks native compilation)
- `ndkVersion "28.2.13676358"` (or compatible with minSdk)
- `namespace "com.hyprmx.jsengine.jsengine"`

In `jsengine/jsengine-android/src/main/jni/CMakeLists.txt`:
```cmake
target_link_options(jsengine PRIVATE "-Wl,-z,max-page-size=16384")
```
**This line is MANDATORY** - ensures .so files are aligned to 16KB pages.

### Verification

After building, check that this linker flag is present in CMakeLists.txt:45. Without it, the library will fail on 16KB page size devices.

## Type Coercion System

JSEngine uses a sophisticated type coercion system to bridge Java and JavaScript:

- **Java → JavaScript**: Handled by `JavaToJavascriptCoercions` map
  - Automatic conversion of primitives, enums, ByteBuffers
  - Functional interfaces converted to JavaScript functions

- **JavaScript → Java**: Handled by `JavaScriptToJavaCoercions` map
  - JavaScript objects → Java interface proxies
  - Automatic array conversion
  - Single-method interfaces treated as callbacks

Register custom coercions:
```java
context.putJavaToJavaScriptCoercion(MyClass.class, coercion);
context.putJavaScriptToJavaCoercion(MyClass.class, coercion);
```

## Native Memory Management

JavaScript objects maintain references to native heap:
- Always call `JSEngineContext.close()` when done
- JavaScriptObject finalization is queued and processed asynchronously
- Use `context.gc()` to force garbage collection cycles

## JNI Method Naming

Native JNI methods follow the pattern:
```cpp
Java_com_hyprmx_jsengine_jsengine_JSEngineContext_<methodName>
```

When renaming classes or packages, update both:
1. Java class/package names
2. Corresponding JNI method signatures in C++ files

## Common Issues

**Build fails with NDK compatibility errors**: Check that `ndkVersion` in `jsengine-android/build.gradle` is compatible with `minSdkVersion`. NDK 28.2.13676358 requires minSdk ≥ 21.

**Native library fails to load on device**: Verify 16KB alignment linker flag is present in CMakeLists.txt.

**Memory leaks**: Ensure `JSEngineContext.close()` is called. Use try-with-resources pattern.

**QuickJS not found errors**: The quickjs/ directory must be at repository root with all .c/.h files present.

**UnsatisfiedLinkError**: Check that native library name in `System.loadLibrary()` matches the library name in CMakeLists.txt (`jsengine`).
