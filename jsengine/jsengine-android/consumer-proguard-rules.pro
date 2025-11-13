# JSEngine Consumer ProGuard Rules
# Automatically applied to apps that use JSEngine library
# Users do NOT need to manually add these rules

# ==============================================================================
# CRITICAL: JNI and Reflection Requirements
# ==============================================================================

# Keep JSEngineContext - JNI entry point
-keep class com.abc.core.jsengine.JSEngineContext {
    private java.lang.Object proxyGet(com.abc.core.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxyHas(com.abc.core.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxySet(com.abc.core.jsengine.JSEngineObject, java.lang.Object, java.lang.Object);
    private java.lang.Object proxyApply(com.abc.core.jsengine.JSEngineObject, java.lang.Object, java.lang.Object[]);
    private java.lang.Object proxyConstruct(com.abc.core.jsengine.JSEngineObject, java.lang.Object[]);
    public synchronized void mapNative(java.lang.Object, java.lang.Object);
    public java.lang.Object unmapNative(java.lang.Object);
    private long getNativePointer(com.abc.core.jsengine.JSEngineJavaScriptObject);
    public *;
}

# Keep JavaScriptObject - accessed from JNI
-keep class com.abc.core.jsengine.JavaScriptObject {
    public <init>(com.abc.core.jsengine.JSEngineContext, long, long);
    public final com.abc.core.jsengine.JSEngineContext jsEngineContext;
    public final long context;
    public final long pointer;
    public *;
}

# Keep JavaObject - created from JNI
-keep class com.abc.core.jsengine.JavaObject {
    public <init>(com.abc.core.jsengine.JSEngineContext, java.lang.Object);
}

# Keep JSValue wrapper
-keep class com.abc.core.jsengine.JSValue {
    public *;
}

# ==============================================================================
# Interfaces - Method signatures must be preserved
# ==============================================================================

-keep interface com.abc.core.jsengine.JSEngineObject { *; }
-keep interface com.abc.core.jsengine.JSEngineJavaScriptObject { *; }
-keep interface com.abc.core.jsengine.JSEngineJavaObject {
    java.lang.Object getObject();
}
-keep interface com.abc.core.jsengine.JSEngineMethodObject { *; }
-keep interface com.abc.core.jsengine.JSEngineJsonObject { *; }

# ==============================================================================
# Exception Handling
# ==============================================================================

-keep class com.abc.core.jsengine.JSEngineException {
    public static void addJSStack(java.lang.Throwable, java.lang.String);
    public static java.lang.String addJavaStack(java.lang.String, java.lang.Throwable);
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

# Keep JSON object fields
-keepclassmembers class * implements com.abc.core.jsengine.JSEngineJsonObject {
    public java.lang.String json;
}

# Keep classes implementing JSEngineObject
-keep class * implements com.abc.core.jsengine.JSEngineObject {
    public *;
}

# ==============================================================================
# Annotations
# ==============================================================================

-keep @interface com.abc.core.jsengine.JSEngineMethodName
-keepclassmembers class * {
    @com.abc.core.jsengine.JSEngineMethodName *;
}

# ==============================================================================
# Method Invocation
# ==============================================================================

-keep class com.abc.core.jsengine.JavaMethodObject {
    public *;
}

# ==============================================================================
# Kotlin Support
# ==============================================================================

-keep class com.abc.core.jsengine.ExtensionsKt {
    public static **;
}

-keep class com.abc.core.jsengine.JSEnginePromise {
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
