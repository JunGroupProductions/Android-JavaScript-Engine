# JSEngine Consumer ProGuard Rules - Simplified Approach
# Automatically applied to apps that use JSEngine library

# ==============================================================================
# Simple Wildcard - Keep Everything
# ==============================================================================

# Keep all JSEngine classes and members
-keep class com.hyprmx.jsengine.jsengine.** { *; }

# Keep Kotlin extensions
-keep class com.hyprmx.jsengine.jsengine.ExtensionsKt { *; }

# Keep debugging attributes
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,Exception

# Keep native methods (redundant but explicit)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
