# JSEngine

JSEngine provides Java and Android bindings to JavaScript engines (QuickJS ES2020 and Duktape ES5.1) with **16KB page size support** for modern Android devices.

## Features

- **Dual JavaScript Engine Support**
  - QuickJS (ES2020) - Modern, lightweight engine
  - Duktape (ES5.1) - Legacy support
- **16KB Page Alignment** - Compatible with Android devices requiring 16KB page sizes
- **Automatic Type Coercion** - Seamless Java ↔ JavaScript type conversion
- **Modern Build Configuration** - AGP 8.8.0, NDK 28.2.13676358, Gradle 8.10.2

## Maven Coordinates

```gradle
repositories {
    mavenLocal()  // Or your Maven repository
    google()
    mavenCentral()
}

dependencies {
    implementation 'com.hyprmx.jsengine:jsengine:1.0.0'
}
```

## Quick Start

```java
import com.hyprmx.jsengine.jsengine.JSEngineContext;

// Create context (true = QuickJS, false = Duktape)
try (JSEngineContext context = JSEngineContext.create(true)) {
    // Execute JavaScript
    Object result = context.evaluate("2 + 2");
    System.out.println(result); // 4

    // Call JavaScript functions
    context.evaluate("function greet(name) { return 'Hello, ' + name; }");
    Object greeting = context.call("greet", "World");
    System.out.println(greeting); // "Hello, World"

    // Pass Java objects to JavaScript
    context.putJavaToJavaScriptCoercion(MyClass.class, myCoercion);
}
```

## Building from Source

### Prerequisites

- Android Studio or Gradle 8.10.2+
- NDK 28.2.13676358
- JDK 8+

### Build Commands

```bash
# Clean build
./gradlew clean

# Build release AAR
./gradlew :jsengine:jsengine-android:assembleRelease

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Run tests
./gradlew :jsengine:jsengine-android:connectedAndroidTest
```

### Output Artifacts

After building, you'll find:
- **AAR**: `jsengine/jsengine-android/build/outputs/aar/jsengine-android-release.aar`
- **Maven**: `~/.m2/repository/com/hyprmx/jsengine/jsengine/1.0.4/`

## 16KB Page Size Support

This library is configured for 16KB page alignment, which is **critical for modern Android devices**:

1. **Build Configuration** (`jsengine/jsengine-android/build.gradle`):
   - `compileSdkVersion` and `targetSdkVersion` must match (35)
   - `minSdkVersion` must be ≥ 21
   - `ndkVersion "28.2.13676358"`

2. **CMake Configuration** (`jsengine/jsengine-android/src/main/jni/CMakeLists.txt`):
   ```cmake
   target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
   ```

## Architecture

### Module Structure

```
jsengine/
├── jsengine-java/        # Pure Java API implementation
├── jsengine-jni/         # Native JNI bridge (C++)
├── jsengine-android/     # Android library module (outputs AAR)
└── JSEngine/             # Sample Android app
```

### Key Classes

- **JSEngineContext** - Main API for JavaScript execution
- **JavaScriptObject** - Proxy interface for JavaScript objects in Java
- **JSEngineCoercion** - Custom type conversion rules

## Type Coercion System

JSEngine automatically converts between Java and JavaScript types:

```java
// Register custom coercion
context.putJavaToJavaScriptCoercion(MyClass.class, (ctx, javaObj) -> {
    // Convert Java object to JavaScript
    return jsObject;
});

context.putJavaScriptToJavaCoercion(MyClass.class, (ctx, jsObj) -> {
    // Convert JavaScript object to Java
    return javaObject;
});
```

## Memory Management

JavaScript objects maintain references to native heap:

```java
// Always close context when done
try (JSEngineContext context = JSEngineContext.create(true)) {
    // Your code here
} // Auto-closes

// Or manually
JSEngineContext context = JSEngineContext.create(true);
try {
    // Your code here
} finally {
    context.close();
}

// Force garbage collection
context.gc();
```

## Attribution

JSEngine is a modified version of [Quack](https://github.com/koush/quack) by Koushik Dutta.

This product contains:
- **Quack** - Copyright 2015 Koushik Dutta (Apache 2.0 License)
- **QuickJS** - Copyright 2017-2021 Fabrice Bellard & Charlie Gordon (MIT License)
- **Duktape** - Various contributors (MIT License)

See [NOTICE](NOTICE) file for complete attribution.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) file for details.

## Modifications from Original Quack

- Complete package refactoring: `com.koushikdutta.quack` → `com.hyprmx.jsengine.jsengine`
- Class renaming: `Quack*` → `JSEngine*`
- Module restructuring: `quack-*` → `jsengine-*`
- Native library renaming: `libquack.so` → `libjsengine.so`
- Maven publishing configuration
- 16KB page size support enhancements
- Updated build tools (AGP 8.8.0, NDK 28.2.13676358)
