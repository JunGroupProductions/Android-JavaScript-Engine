# JSEngine Consumer ProGuard Rules - Recommended Balanced Approach
# Automatically applied to apps that use JSEngine library

# ==============================================================================
# Balanced Approach: Keep public API, protect JNI internals
# ==============================================================================

# Keep all public API - this is what users interact with
-keep public class com.hyprmx.android.jsengine.** {
    public *;
}

# Keep all interfaces - needed for JavaScript proxying
-keep interface com.hyprmx.android.jsengine.** { *; }

# CRITICAL: Keep JNI callback methods even if private
# These MUST match exact names expected by native code
-keepclassmembers class com.hyprmx.android.jsengine.JSEngineContext {
    private java.lang.Object proxyGet(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxyHas(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxySet(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object, java.lang.Object);
    private java.lang.Object proxyApply(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object, java.lang.Object[]);
    private java.lang.Object proxyConstruct(com.hyprmx.android.jsengine.JSEngineObject, java.lang.Object[]);
    private long getNativePointer(com.hyprmx.android.jsengine.JSEngineJavaScriptObject);
}

# Keep JavaScriptObject - accessed from JNI
-keep class com.hyprmx.android.jsengine.JavaScriptObject {
    public <init>(com.hyprmx.android.jsengine.JSEngineContext, long, long);
    public final com.hyprmx.android.jsengine.JSEngineContext jsEngineContext;
    public final long context;
    public final long pointer;
}

# Keep constructors called from JNI
-keepclassmembers class com.hyprmx.android.jsengine.JavaObject {
    public <init>(com.hyprmx.android.jsengine.JSEngineContext, java.lang.Object);
}

# Keep exception handling
-keepclassmembers class com.hyprmx.android.jsengine.JSEngineException {
    public static void addJSStack(java.lang.Throwable, java.lang.String);
    public static java.lang.String addJavaStack(java.lang.String, java.lang.Throwable);
}

# Keep JSON field
-keepclassmembers class * implements com.hyprmx.android.jsengine.JSEngineJsonObject {
    public java.lang.String json;
}

# Keep getObject() for Java object wrappers
-keepclassmembers class * implements com.hyprmx.android.jsengine.JSEngineJavaObject {
    public java.lang.Object getObject();
}

# Keep annotations
-keep @interface com.hyprmx.android.jsengine.JSEngineMethodName
-keepclassmembers class * {
    @com.hyprmx.android.jsengine.JSEngineMethodName *;
}

# Debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,Exception

# Native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
