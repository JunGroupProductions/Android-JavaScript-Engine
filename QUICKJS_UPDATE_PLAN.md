# QuickJS Update Plan

## Current State

**QuickJS Version:** 2020-07-05
**Latest Version:** 2025-09-13 (as of Nov 2025)
**Gap:** ~5 years of development

**Debugger Support:** ✅ Custom debugger files present
- `quickjs-debugger.c`
- `quickjs-debugger-transport-unix.c`
- `quickjs-debugger.h`

## When to Update QuickJS

⚠️ **DO NOT update QuickJS until AFTER JSEngine refactoring is complete and stable.**

**Recommended timeline:**
1. Complete JSEngine refactoring (follow REFACTORING_PLAN.md)
2. Test and stabilize JSEngine build
3. Create backup branch of working JSEngine
4. THEN proceed with QuickJS update

## Pre-Update Checks

### 1. Verify Current State is Stable

```bash
# Ensure JSEngine refactoring is complete
ls -la jsengine/

# Build must pass
./gradlew clean
./gradlew :jsengine:jsengine-android:assembleRelease

# Tests should pass
./gradlew :jsengine:jsengine-android:connectedAndroidTest
```

### 2. Create Backup Branch

```bash
git checkout -b backup-before-quickjs-update
git add -A
git commit -m "Stable JSEngine before QuickJS update to 2025-09-13"
git push origin backup-before-quickjs-update
git checkout main  # or your working branch
```

### 3. Document Current QuickJS Configuration

```bash
# Save current version
cat quickjs/VERSION > quickjs-version-before-update.txt

# Save current file list
ls -la quickjs/ > quickjs-files-before-update.txt

# Check what's being used in build
grep -r "quickjs" jsengine/jsengine-android/src/main/jni/CMakeLists.txt > cmake-quickjs-deps.txt
```

## Update Process

### Step 1: Find Latest Release

Visit: https://bellard.org/quickjs/

**As of 2025-11-11:**
- Latest: `quickjs-2025-09-13.tar.xz`
- URL: `https://bellard.org/quickjs/quickjs-2025-09-13.tar.xz`

**Check for newer releases:**
```bash
curl -s https://bellard.org/quickjs/ | grep -o 'quickjs-[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}\.tar\.xz' | sort -r | head -1
```

### Step 2: Run Update Script

```bash
# Make script executable
chmod +x update_quickjs.sh

# Run with latest version URL
./update_quickjs.sh https://bellard.org/quickjs/quickjs-YYYY-MM-DD.tar.xz
```

**Script will:**
- Download and extract QuickJS
- Show file comparison (current vs new)
- Validate required files exist
- Ask for confirmation before updating

**Review the comparison carefully!**
- Check which files will be added
- Check which files will be removed
- Ensure debugger files are preserved or included

### Step 3: Initial Build Test

```bash
./gradlew clean
./gradlew :jsengine:jsengine-android:assembleRelease 2>&1 | tee build-log.txt
```

**Expected: Build will fail with linker errors**

### Step 4: Identify API Changes

Review build errors and categorize:

#### Common Error Categories:

**A. Missing Symbols (New APIs Added)**
```
undefined symbol: js_dtoa
undefined symbol: js_atod
```
**Fix:** Add new .c files to CMakeLists.txt

**B. Removed/Renamed APIs**
```
undefined symbol: JS_NewAtomLenPrivate
```
**Fix:** Update JNI wrapper code in `QuickJSContext.cpp`

**C. Changed Function Signatures**
```
error: too few arguments to function 'JS_SomeFunction'
```
**Fix:** Update calls in JNI wrapper to match new signature

**D. Debugger API Changes**
```
undefined symbol: js_debugger_info
undefined symbol: js_debugger_stack_depth
```
**Fix:** Update debugger integration code

### Step 5: Fix CMakeLists.txt

Location: `jsengine/jsengine-android/src/main/jni/CMakeLists.txt`

**Check QuickJS release notes for new files:**
- New math functions → add dtoa.c
- New string handling → add new .c files
- Module system changes → check for new module files

**Example additions:**
```cmake
file(GLOB quickjs_SRC
    # ... existing files ...
    "../../../../../quickjs/dtoa.c"           # If present in new version
    "../../../../../quickjs/new-feature.c"   # Any new files
)
```

### Step 6: Update JNI Wrapper Code

**File:** `jsengine/jsengine-jni/src/main/jni/quickjs-jni/QuickJSContext.cpp`

#### 6.1 Check QuickJS API Documentation

Look for changelog in downloaded archive:
```bash
cat tmp/quickjs-YYYY-MM-DD/Changelog | head -50
```

#### 6.2 Common API Updates Needed

**Atom API Changes:**
```cpp
// OLD (2020-07-05)
JS_NewAtomLenPrivate(ctx, str, len);

// Might be in NEW version:
JS_NewAtomLen(ctx, str, len);  // Check actual API
```

**Object Creation:**
```cpp
// Check if JS_MKPTR signature changed
// Check if JS_GetOpaque/JS_SetOpaque changed
```

**Debugger Integration:**
```cpp
// Review all js_debugger_* function calls
// Check if debugger API changed
// May need to update debugger attach/detach logic
```

#### 6.3 Search and Replace Strategy

1. **Find all QuickJS API calls:**
```bash
grep -n "JS_" jsengine/jsengine-jni/src/main/jni/quickjs-jni/QuickJSContext.cpp | wc -l
```

2. **For each API call, verify it still exists in new QuickJS:**
```bash
grep "^JS_NewAtom" quickjs/quickjs.h
```

3. **Update as needed based on new signatures**

### Step 7: Fix Debugger Compatibility

**Critical Files:**
- `quickjs/quickjs-debugger.c`
- `quickjs/quickjs-debugger-transport-unix.c`
- `jsengine/jsengine-jni/src/main/jni/quickjs-jni/QuickJSContext.cpp` (debugger integration)

**Check:**
1. Do debugger files compile against new QuickJS core?
2. Are debugger symbols resolved?
3. Does debugger initialization work?

**Test Commands:**
```cpp
// In QuickJSContext constructor, verify:
- js_debugger_attach() still exists
- js_debugger_cooperate() still exists
- Debugger transport still works
```

### Step 8: Incremental Testing

**Test in this order:**

1. **Basic Compilation:**
   ```bash
   ./gradlew :jsengine:jsengine-android:assembleRelease
   ```

2. **Basic JS Execution:**
   ```java
   JSEngineContext ctx = JSEngineContext.create();
   Object result = ctx.evaluate("2 + 2");
   System.out.println(result); // Should print 4
   ctx.close();
   ```

3. **Type Coercion:**
   ```java
   // Test Java ↔ JavaScript conversion
   // Test arrays, objects, functions
   ```

4. **Debugger Functionality:**
   ```java
   // Test debugger attach
   // Test breakpoints (if supported)
   // Test step through code
   ```

5. **Full Test Suite:**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

### Step 9: Performance Comparison

**Optional but recommended:**

```java
// Benchmark before/after update
long start = System.currentTimeMillis();
for (int i = 0; i < 10000; i++) {
    ctx.evaluate("function test() { return 42; } test();");
}
long duration = System.currentTimeMillis() - start;
```

Save results to compare performance improvements.

## Common Issues and Solutions

### Issue 1: Missing dtoa Functions

**Error:**
```
undefined symbol: js_dtoa
undefined symbol: js_atod
```

**Solution:**
```cmake
# Add to CMakeLists.txt quickjs_SRC section:
"../../../../../quickjs/dtoa.c"
```

### Issue 2: Atom API Changed

**Error:**
```
undefined symbol: JS_NewAtomLenPrivate
```

**Investigation:**
```bash
# Check new API
grep "JS_NewAtom" quickjs/quickjs.h
```

**Solution:**
Update all calls in QuickJSContext.cpp to use new API.

### Issue 3: Debugger Symbols Missing

**Error:**
```
undefined symbol: js_debugger_info
undefined symbol: js_debugger_stack_depth
```

**Cause:** Debugger API internals changed in new QuickJS

**Solutions (in order of preference):**

1. **Update debugger integration code** to match new QuickJS internals
2. **Disable debugger temporarily** (comment out in CMakeLists.txt)
3. **Port debugger to new API** (most work, but best long-term)

### Issue 4: Memory Management Changes

**Symptoms:**
- Crashes in JS_FreeValue
- Crashes in garbage collection
- Memory leaks

**Solution:**
Review QuickJS changelog for memory management changes and update accordingly.

### Issue 5: Module System Changes

**Error:**
```
Module import/export not working as expected
```

**Solution:**
Check if module system API changed (JS_EvalModule, etc.)

## Rollback Plan

### If Update Fails Completely:

```bash
# Return to backup branch
git checkout backup-before-quickjs-update

# Or revert QuickJS directory only
cd quickjs
git checkout backup-before-quickjs-update -- .
cd ..
```

### If Need to Try Different Version:

```bash
# Revert quickjs to pre-update state
cd quickjs
git checkout HEAD -- .
git clean -fd
cd ..

# Try a middle version (e.g., 2024-01-13)
./update_quickjs.sh https://bellard.org/quickjs/quickjs-2024-01-13.tar.xz
```

## Alternative Approach: Staged Updates

Instead of jumping from 2020-07-05 → 2025-09-13, do incremental updates:

1. **2020-07-05 → 2022-XX-XX** (2 year jump)
2. Test and stabilize
3. **2022-XX-XX → 2024-XX-XX** (2 year jump)
4. Test and stabilize
5. **2024-XX-XX → 2025-09-13** (1 year jump)

**Pros:** Smaller API changes per step, easier debugging
**Cons:** More time consuming

## Success Criteria

Update is complete when:

- ✅ Build completes without errors
- ✅ All unit tests pass
- ✅ Basic JS execution works
- ✅ Type coercion works (Java ↔ JS)
- ✅ Debugger features work (if required)
- ✅ No memory leaks detected
- ✅ Performance is same or better
- ✅ All instrumented tests pass

## Estimated Effort

**Conservative estimate:** 4-8 hours
- 1 hour: Setup and initial build attempt
- 2-4 hours: Fix JNI wrapper API compatibility
- 1-2 hours: Fix debugger compatibility (or disable if too complex)
- 1 hour: Testing and validation

**Worst case:** 2-3 days
- If debugger requires major rework
- If QuickJS core API changed significantly
- If memory management changed

## Resources

- QuickJS Homepage: https://bellard.org/quickjs/
- QuickJS GitHub Mirror: https://github.com/bellard/quickjs
- QuickJS Changelog: In downloaded archive
- QuickJS Documentation: https://bellard.org/quickjs/quickjs.pdf

## Notes for Future Sessions

When you're ready to update QuickJS, start a session with:

> "Follow QUICKJS_UPDATE_PLAN.md to update QuickJS to the latest version"

**Prerequisites before starting:**
- ✅ JSEngine refactoring complete
- ✅ JSEngine builds successfully
- ✅ Backup branch created
- ✅ All tests passing
