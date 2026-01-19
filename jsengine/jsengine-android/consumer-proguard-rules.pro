# JSEngine Consumer ProGuard Rules
# Automatically applied to apps that use JSEngine library
# Users do NOT need to manually add these rules

# ==============================================================================
# CRITICAL: JNI and Reflection Requirements
# ==============================================================================

# Keep JSEngineContext - JNI entry point
-keep class com.hyprmx.android.jsengine.JSEngineContext {
    private java.lang.Object proxyGet(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxyHas(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxySet(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object, java.lang.Object);
    private java.lang.Object proxyApply(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object, java.lang.Object[]);
    private java.lang.Object proxyConstruct(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object[]);
    public synchronized void mapNative(java.lang.Object, java.lang.Object);
    public java.lang.Object unmapNative(java.lang.Object);
    private long getNativePointer(com.hyprmx.android.jsengine.JSEngineJavaScriptObject);
    public *;
}

# Keep JavaScriptObject - accessed from JNI
-keep class com.hyprmx.android.jsengine.JavaScriptObject {
    public <init>(com.hyprmx.android.jsengine.JSEngineContext, long, long);
    public final com.hyprmx.android.jsengine.JSEngineContext jsEngineContext;
    public final long context;
    public final long pointer;
    public *;
}

# Keep JavaObject - created from JNI
-keep class com.hyprmx.android.jsengine.JavaObject {
    public <init>(com.hyprmx.android.jsengine.JSEngineContext, java.lang.Object);
}

# Keep JSValue wrapper
-keep class com.hyprmx.android.jsengine.JSValue {
    public *;
}

# ==============================================================================
# Interfaces - Method signatures must be preserved
# ==============================================================================

-keep interface com.hyprmx.android.jsengine.JSEngineObject { *; }
-keep interface com.hyprmx.android.jsengine.JSEngineJavaScriptObject { *; }
-keep interface com.hyprmx.android.jsengine.JSEngineJavaObject {
    java.lang.Object getObject();
}
-keep interface com.hyprmx.android.jsengine.JSEngineMethodObject { *; }

# JSEngineJsonObject is a CLASS, not an interface
-keep class com.hyprmx.android.jsengine.JSEngineJsonObject {
    public *;
}

# ==============================================================================
# Exception Handling
# ==============================================================================

-keep class com.hyprmx.android.jsengine.JSEngineException {
    static void addJSStack(java.lang.Throwable, java.lang.String);
    static java.lang.String addJavaStack(java.lang.String, java.lang.Throwable);
    public *;
}

# ==============================================================================
# User Code Protection
# ==============================================================================

# Keep public methods of user classes passed to JavaScript
# This ensures JavaScript can call methods via reflection
-keepclassmembers class * {
    # If a class is passed to JSEngineContext, keep its public methods
    public <methods>;
}

# Keep constructors that might be called from JavaScript
-keepclassmembers class * {
    public <init>(...);
}

# Keep classes implementing JSEngineObject
-keep class * implements com.hyprmx.android.jsengine.JSEngineObject {
    public *;
}

# ==============================================================================
# Annotations
# ==============================================================================

-keep @interface com.hyprmx.android.jsengine.JSEngineMethodName
-keepclassmembers class * {
    @com.hyprmx.android.jsengine.JSEngineMethodName *;
}

# ==============================================================================
# Method Invocation
# ==============================================================================

-keep class com.hyprmx.android.jsengine.JavaMethodObject {
    public *;
}

# ==============================================================================
# Kotlin Support
# ==============================================================================

-keep class com.hyprmx.android.jsengine.ExtensionsKt {
    public static <methods>;
}

-keep class com.hyprmx.android.jsengine.JSEnginePromise {
    public *;
}

# ==============================================================================
# Debugging
# ==============================================================================

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,Exception

# ==============================================================================
# Native Methods
# ==============================================================================

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
