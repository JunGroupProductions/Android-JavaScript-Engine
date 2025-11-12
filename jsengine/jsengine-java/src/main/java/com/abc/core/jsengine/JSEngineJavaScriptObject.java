package com.abc.core.jsengine;

public interface JSEngineJavaScriptObject {
    long getNativePointer();
    long getNativeContext();
    JavaScriptObject getJavaScriptObject();
}
