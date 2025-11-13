package com.hyprmx.jsengine.jsengine;

import java.lang.reflect.InvocationHandler;

public interface JSEngineInvocationHandlerWrapper {
    InvocationHandler wrapInvocationHandler(JavaScriptObject javaScriptObject, InvocationHandler handler);
}
