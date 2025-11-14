# Quack → JSEngine Refactoring Plan

## CRITICAL: Pre-Flight Checks

**⚠️ STOP! Complete these checks BEFORE starting refactoring:**

### 1. Verify Clean QuickJS State

Check if QuickJS has uncommitted changes:
```bash
cd quickjs
git status
```

**If you see modified files in quickjs/:**
- Someone ran `update_quickjs.sh` but didn't update the JNI wrapper code
- The JNI code is incompatible with the new QuickJS version
- **You MUST revert QuickJS to the last working version:**

```bash
cd quickjs
git checkout HEAD -- .
git clean -fd
cd ..
```

### 2. Verify Build Works

**Test that the project builds successfully BEFORE refactoring:**

```bash
./gradlew clean
./gradlew :quack:quack-android:assembleRelease
```

**Expected result:** Build succeeds with no errors.

**If build fails:**
- ❌ DO NOT proceed with refactoring
- Fix the build issues first
- Common issue: QuickJS/JNI version mismatch (see step 1)

### 3. Check for QuickJS API Compatibility

Look for these warning signs in build errors:
- `undefined symbol: JS_NewAtomLenPrivate`
- `undefined symbol: js_debugger_*`
- `undefined symbol: js_dtoa`
- `undefined symbol: js_atod`

**If you see these errors:**
- The QuickJS version is too new for the JNI wrapper
- Revert quickjs/ directory (see step 1)

### 4. Document Current State

Before refactoring, record:
```bash
# Check current QuickJS version
cat quickjs/VERSION

# Check git status
git status > pre-refactor-status.txt

# List all modules
ls -la quack/
```

## Prerequisites
- ✅ Pre-flight checks completed
- ✅ Build passes successfully
- ✅ No uncommitted QuickJS changes (or reverted to working version)
- ✅ Current state documented

## Target Configuration
- **Artifact**: jsengine
- **Package**: com.hyprmx.jsengine.jsengine
- **Group ID**: com.hyprmx.jsengine
- **Version**: 1.0.0
- **Native Library**: libjsengine.so
- **Class Prefix**: JSEngine (e.g., JSEngineContext)

## Step-by-Step Refactoring

### 1. Legal Compliance (Apache 2.0 Requirements)

Create `NOTICE` file:
```
JSEngine
Copyright 2025 HyprMX

This product contains modified portions of Quack, originally created by Koushik Dutta:
  * Copyright 2015 Koushik Dutta
  * Licensed under the Apache License, Version 2.0
  * Source: https://github.com/koush/quack
  * Modifications include:
    - Complete package refactoring (com.koushikdutta.quack → com.hyprmx.jsengine.jsengine)
    - Class renaming (Quack* → JSEngine*)
    - Module restructuring (quack-* → jsengine-*)
    - Native library renaming (libquack.so → libjsengine.so)
    - Maven publishing configuration
    - 16KB page size support enhancements

This product includes QuickJS JavaScript Engine:
  * Copyright 2017-2021 Fabrice Bellard
  * Copyright 2017-2021 Charlie Gordon
  * Licensed under the MIT License
  * Source: https://bellard.org/quickjs/
  * Location: quickjs/ directory
  * See quickjs/LICENSE for full license text
```

Add to each modified Java file (before existing copyright):
```java
/*
 * MODIFIED FILE - Changed from original Quack library
 * Package changed: com.koushikdutta.quack -> com.hyprmx.jsengine.jsengine
 * Class names changed: Quack* -> JSEngine*
 * Modifications (C) 2025 HyprMX
 *
 * ===== Original Copyright Notice =====
 */
```

### 2. Directory Renaming

```bash
# From project root
mv quack jsengine
cd jsengine
mv quack-java jsengine-java
mv quack-jni jsengine-jni
mv quack-android jsengine-android
mv QuackJS JSEngine
cd ..
mv QuackJS JSEngine  # If it exists at root level
```

### 3. Settings.gradle Updates

**Root `settings.gradle`:**
```gradle
rootProject.name = "JSEngine"
include ':jsengine:JSEngine'
include ':jsengine:jsengine-android'
```

**jsengine/settings.gradle:**
```gradle
include 'jsengine-java'
include 'jsengine-android'
include 'jsengine-jni'
include 'JSEngine'
```

### 4. Java Package Refactoring

Create new package structure:
```bash
cd jsengine/jsengine-java/src/main/java
mkdir -p com/hyprmx/jsengine/jsengine
mkdir -p ../test/java/com/hyprmx/jsengine/jsengine
```

Copy files to new location:
```bash
cp -r com/koushikdutta/quack/* com/hyprmx/jsengine/jsengine/
cd ../test/java
cp -r com/koushikdutta/quack/* com/hyprmx/jsengine/jsengine/
```

Update package declarations in all files:
```bash
# In com/hyprmx/jsengine/jsengine
for file in *.java *.kt; do
  sed -i '' 's/package com\.koushikdutta\.quack/package com.hyprmx.jsengine.jsengine/g' "$file"
  sed -i '' 's/import com\.koushikdutta\.quack\./import com.hyprmx.jsengine.jsengine./g' "$file"
done
```

### 5. Class Renaming

In all Java/Kotlin files under `com/hyprmx/jsengine/jsengine/`:

**Search and replace (word boundaries important):**
```
QuackCoercion → JSEngineCoercion
QuackContext → JSEngineContext
QuackException → JSEngineException
QuackInvocationHandlerWrapper → JSEngineInvocationHandlerWrapper
QuackJavaObject → JSEngineJavaObject
QuackJavaScriptObject → JSEngineJavaScriptObject
QuackJsonObject → JSEngineJsonObject
QuackMethodCoercion → JSEngineMethodCoercion
QuackMethodName → JSEngineMethodName
QuackMethodObject → JSEngineMethodObject
QuackObject → JSEngineObject
QuackPromise → JSEnginePromise
QuackPromiseReceiver → JSEnginePromiseReceiver
QuackProperty → JSEngineProperty
QuackReadonlyObject → JSEngineReadonlyObject
```

**Rename files:**
```bash
mv QuackCoercion.java JSEngineCoercion.java
mv QuackContext.java JSEngineContext.java
# ... (rename all Quack*.java to JSEngine*.java)
```

**Update System.loadLibrary in JSEngineContext.java:**
```java
System.loadLibrary("jsengine");  // was "quack"
```

**Remove old package directories:**
```bash
rm -rf com/koushikdutta
```

### 6. Build.gradle Updates

**jsengine/jsengine-android/build.gradle:**

```gradle
apply plugin: 'com.android.library'
apply plugin: 'maven-publish'

android {
  compileSdkVersion 35
  namespace "com.hyprmx.jsengine.jsengine"  // CHANGED
  ndkVersion "28.2.13676358"

  sourceSets {
    main.java.srcDirs += "../jsengine-java/src/main/java/"  // CHANGED
    androidTest.java.srcDirs += "../jsengine-java/src/test/java/"  // CHANGED
  }

  // ... rest stays same ...
}

// Add Maven publishing after android block
afterEvaluate {
  publishing {
    publications {
      release(MavenPublication) {
        groupId = 'com.hyprmx.jsengine'
        artifactId = 'jsengine'
        version = '1.0.0'

        artifact("$buildDir/outputs/aar/${project.name}-release.aar")

        pom {
          name = 'JSEngine'
          description = 'Java and Android bindings for JavaScript engines (QuickJS/Duktape) with 16KB page size support'
          url = 'https://github.com/yourusername/jsengine'

          licenses {
            license {
              name = 'The Apache License, Version 2.0'
              url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
            }
          }

          developers {
            developer {
              id = 'hyprmx'
              name = 'HyprMX'
              email = 'dev@hyprmx.com'
            }
          }
        }
      }
    }

    repositories {
      mavenLocal()
      // maven { url = uri("...") } // For remote publishing
    }
  }
}
```

**jsengine/JSEngine/build.gradle:**
```gradle
namespace "com.hyprmx.jsengine.sample"  // Sample module namespace
```

### 7. CMakeLists.txt Updates

**jsengine/jsengine-android/src/main/jni/CMakeLists.txt:**

```cmake
cmake_minimum_required(VERSION 3.4.1)

project(jsengine)  # CHANGED from quack

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

file(GLOB common_SRC
    "../../../../jsengine-jni/src/main/jni/*.h"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/*.cpp"  # CHANGED
)

file(GLOB_RECURSE duktape_SRC
    "../../../../jsengine-jni/src/main/jni/duktape-jni/*.h"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/duktape-jni/*.c"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/duktape-jni/*.cpp"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/duktape/*.h"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/duktape/*.c"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/duktape/*.cpp"  # CHANGED
)

add_definitions(-DCONFIG_VERSION="2019-10-27")
add_definitions(-DCONFIG_DISABLE_STACK_CHECK)
add_definitions(-DCONFIG_DISABLE_WORKER)

file(GLOB quickjs_SRC
    "../../../../jsengine-jni/src/main/jni/quickjs-jni/*.h"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/quickjs-jni/*.c"  # CHANGED
    "../../../../jsengine-jni/src/main/jni/quickjs-jni/*.cpp"  # CHANGED
    "../../../../../../quickjs/*.h"
    "../../../../../quickjs/quickjs.c"
    "../../../../../quickjs/libbf.c"
    "../../../../../quickjs/quickjs-libc.c"
    "../../../../../quickjs/quickjs-debugger.c"
    "../../../../../quickjs/quickjs-debugger-transport-unix.c"
    "../../../../../quickjs/libunicode.c"
    "../../../../../quickjs/libregexp.c"
    "../../../../../quickjs/cutils.c"
    "../../../../../quickjs/dtoa.c"  # ADD THIS LINE
)

add_library(jsengine SHARED ${common_SRC} ${quickjs_SRC} ${duktape_SRC})  # CHANGED

target_link_libraries(jsengine)  # CHANGED

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
```

### 8. JNI C++ Code Updates

**In all files under `jsengine/jsengine-jni/`:**

Update JNI method signatures:
```bash
find jsengine/jsengine-jni -name "*.cpp" -exec sed -i '' \
  's/Java_com_koushikdutta_quack_QuackContext/Java_com_abc_core_jsengine_JSEngineContext/g' {} \;

find jsengine/jsengine-jni -name "*.h" -exec sed -i '' \
  's/Java_com_koushikdutta_quack_QuackContext/Java_com_abc_core_jsengine_JSEngineContext/g' {} \;
```

Update package paths:
```bash
sed -i '' 's|com/koushikdutta/quack|com/hyprmx/jsengine/jsengine|g' \
  jsengine/jsengine-jni/src/main/jni/quickjs-jni/QuickJSContext.cpp

sed -i '' 's|com/koushikdutta/quack|com/hyprmx/jsengine/jsengine|g' \
  jsengine/jsengine-jni/src/main/jni/duktape-jni/DuktapeContext.cpp
```

### 9. Documentation Updates

**README.md:** (See full content in previous conversation)

Key sections:
- Project name: JSEngine
- Maven coordinates
- Build commands including `publishToMavenLocal`
- Usage example with `JSEngineContext`
- Attribution section

**CLAUDE.md:** (See full content in previous conversation)

Update all references:
- Module paths
- Class names
- Package names
- Maven configuration

### 10. Verification

```bash
# Test build
./gradlew clean
./gradlew :jsengine:jsengine-android:assembleRelease

# Test Maven publishing
./gradlew publishToMavenLocal

# Check output
ls jsengine/jsengine-android/build/outputs/aar/
ls ~/.m2/repository/com/hyprmx/jsengine/jsengine/1.0.0/
```

## Common Issues

### 1. QuickJS Version Mismatch (MOST COMMON)

**Symptoms:**
- Linker errors about undefined symbols
- Symbols like `JS_NewAtomLenPrivate`, `js_debugger_*`, `js_dtoa`, `js_atod`

**Cause:**
- QuickJS was updated but JNI wrapper code wasn't

**Fix:**
```bash
# Revert QuickJS to working version
cd quickjs
git checkout HEAD -- .
git clean -fd
cd ..

# Rebuild
./gradlew clean
./gradlew :jsengine:jsengine-android:assembleRelease
```

### 2. Missing dtoa.c

**Symptoms:**
- Undefined symbols: `js_dtoa`, `js_atod`, `i64toa_radix`

**Fix:**
Add to CMakeLists.txt in quickjs_SRC section:
```cmake
"../../../../../quickjs/dtoa.c"
```

### 3. Gradle Component Error

**Symptoms:**
- `Could not get unknown property 'release' for SoftwareComponent container`

**Fix:**
Use `artifact()` instead of `from components.release` in publishing block:
```gradle
artifact("$buildDir/outputs/aar/${project.name}-release.aar")
```

### 4. Namespace Conflicts

**Symptoms:**
- Build errors about package not found
- Import errors

**Fix:**
Ensure all old package directories are removed:
```bash
find . -path "*/com/koushikdutta" -type d
# Should return nothing after refactoring
```

## Rollback Plan

### If QuickJS Issues During Refactoring:

```bash
# Revert QuickJS only
cd quickjs
git checkout HEAD -- .
git clean -fd
cd ..
```

### If Complete Refactoring Rollback Needed:

```bash
# Revert everything
git checkout .
git clean -fd

# Verify clean state
git status
```

Then restart refactoring from clean state.

## Post-Refactoring: Updating QuickJS Safely

If you need to update QuickJS AFTER refactoring is complete:

1. **Make a backup branch first:**
```bash
git checkout -b backup-working-jsengine
git add -A
git commit -m "Working JSEngine before QuickJS update"
git checkout main
```

2. **Run update script:**
```bash
./update_quickjs.sh https://bellard.org/quickjs/quickjs-YYYY-MM-DD.tar.xz
```

3. **Update JNI wrapper code:**
- Check QuickJS release notes for API changes
- Update `jsengine/jsengine-jni/src/main/jni/quickjs-jni/QuickJSContext.cpp`
- Update any changed function signatures

4. **Test thoroughly:**
```bash
./gradlew clean
./gradlew :jsengine:jsengine-android:assembleRelease
./gradlew :jsengine:jsengine-android:connectedAndroidTest
```

5. **If update fails:**
```bash
git checkout backup-working-jsengine
```
