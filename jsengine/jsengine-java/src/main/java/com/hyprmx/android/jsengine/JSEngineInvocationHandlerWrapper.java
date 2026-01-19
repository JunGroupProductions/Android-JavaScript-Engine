package com.hyprmx.android.jsengine;

import java.lang.reflect.InvocationHandler;

public interface JSEngineInvocationHandlerWrapper {
    InvocationHandler wrapInvocationHandler(JavaScriptObject javaScriptObject, InvocationHandler handler);
}
