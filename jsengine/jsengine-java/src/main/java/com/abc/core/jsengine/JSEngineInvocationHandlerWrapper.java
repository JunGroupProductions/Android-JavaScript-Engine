package com.abc.core.jsengine;

import java.lang.reflect.InvocationHandler;

public interface JSEngineInvocationHandlerWrapper {
    InvocationHandler wrapInvocationHandler(JavaScriptObject javaScriptObject, InvocationHandler handler);
}
