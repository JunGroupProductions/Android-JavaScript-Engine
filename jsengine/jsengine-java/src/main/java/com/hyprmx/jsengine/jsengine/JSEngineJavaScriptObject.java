package com.hyprmx.jsengine.jsengine;

public interface JSEngineJavaScriptObject {
    long getNativePointer();
    long getNativeContext();
    JavaScriptObject getJavaScriptObject();
}
