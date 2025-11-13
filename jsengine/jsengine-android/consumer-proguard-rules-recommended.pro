# JSEngine Consumer ProGuard Rules - Recommended Balanced Approach
# Automatically applied to apps that use JSEngine library

# ==============================================================================
# Balanced Approach: Keep public API, protect JNI internals
# ==============================================================================

# Keep all public API - this is what users interact with
-keep public class com.abc.core.jsengine.** {
    public *;
}

# Keep all interfaces - needed for JavaScript proxying
-keep interface com.abc.core.jsengine.** { *; }

# CRITICAL: Keep JNI callback methods even if private
# These MUST match exact names expected by native code
-keepclassmembers class com.abc.core.jsengine.JSEngineContext {
    private java.lang.Object proxyGet(com.abc.core.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxyHas(com.abc.core.jsengine.JSEngineObject, java.lang.Object);
    private boolean proxySet(com.abc.core.jsengine.JSEngineObject, java.lang.Object, java.lang.Object);
    private java.lang.Object proxyApply(com.abc.core.jsengine.JSEngineObject, java.lang.Object, java.lang.Object[]);
    private java.lang.Object proxyConstruct(com.abc.core.jsengine.JSEngineObject, java.lang.Object[]);
    private long getNativePointer(com.abc.core.jsengine.JSEngineJavaScriptObject);
}

# Keep JavaScriptObject - accessed from JNI
-keep class com.abc.core.jsengine.JavaScriptObject {
    public <init>(com.abc.core.jsengine.JSEngineContext, long, long);
    public final com.abc.core.jsengine.JSEngineContext jsEngineContext;
    public final long context;
    public final long pointer;
}

# Keep constructors called from JNI
-keepclassmembers class com.abc.core.jsengine.JavaObject {
    public <init>(com.abc.core.jsengine.JSEngineContext, java.lang.Object);
}

# Keep exception handling
-keepclassmembers class com.abc.core.jsengine.JSEngineException {
    public static void addJSStack(java.lang.Throwable, java.lang.String);
    public static java.lang.String addJavaStack(java.lang.String, java.lang.Throwable);
}

# Keep JSON field
-keepclassmembers class * implements com.abc.core.jsengine.JSEngineJsonObject {
    public java.lang.String json;
}

# Keep getObject() for Java object wrappers
-keepclassmembers class * implements com.abc.core.jsengine.JSEngineJavaObject {
    public java.lang.Object getObject();
}

# Keep annotations
-keep @interface com.abc.core.jsengine.JSEngineMethodName
-keepclassmembers class * {
    @com.abc.core.jsengine.JSEngineMethodName *;
}

# Debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,Exception

# Native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
