/*
 * Copyright (C) 2015 Koushik Dutta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyprmx.android.jsengine;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings({"unchecked", "rawtypes"})
public class JavaScriptObject implements JSEngineObject, JSEngineJavaScriptObject {
    final public JSEngineContext jsEngineContext;
    public final long context;
    final public long pointer;
    public JavaScriptObject(JSEngineContext jsEngineContext, long context, long pointer) {
        this.jsEngineContext = jsEngineContext;
        this.context = context;
        this.pointer = pointer;
    }

    @Override
    public long getNativePointer() {
        return pointer;
    }

    @Override
    public long getNativeContext() {
        return context;
    }

    @Override
    public JavaScriptObject getJavaScriptObject() {
        return this;
    }

    @Override
    public JavaScriptObject construct(Object... args) {
        return constructCoerced(JavaScriptObject.class, args);
    }

    public <T> T constructCoerced(Class<T> clazz, Object... args) {
        jsEngineContext.coerceJavaArgsToJavaScript(args);
        return (T)jsEngineContext.coerceJavaScriptToJava(clazz, jsEngineContext.callConstructor(pointer, args));
    }

    public String typeof() {
        return (String)jsEngineContext.evaluateForJavaScriptObject("(function(f) { return typeof f; })").call(this);
    }

    public String stringify() {
        return jsEngineContext.stringify(pointer);
    }

    public Object get(String key) {
        return jsEngineContext.coerceJavaScriptToJava(null, jsEngineContext.getKeyString(pointer, key));
    }

    public Object get(int index) {
        return jsEngineContext.coerceJavaScriptToJava(null, jsEngineContext.getKeyInteger(pointer, index));
    }

    public Object call(Object... args) {
        jsEngineContext.coerceJavaArgsToJavaScript(args);
        return jsEngineContext.coerceJavaScriptToJava(null, jsEngineContext.call(pointer, args));
    }

    @Override
    public Object callMethod(Object thiz, Object... args) {
        jsEngineContext.coerceJavaArgsToJavaScript(args);
        return jsEngineContext.coerceJavaScriptToJava(null, jsEngineContext.callMethod(pointer, jsEngineContext.coerceJavaToJavaScript(thiz), args));
    }

    public Object callProperty(Object property, Object... args) {
        jsEngineContext.coerceJavaArgsToJavaScript(args);
        return jsEngineContext.coerceJavaScriptToJava(null, jsEngineContext.callProperty(pointer, property, args));
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String)
            return get((String)key);

        if (key instanceof Number) {
            Number number = (Number)key;
            if (((Integer)number.intValue()).equals(number))
                return get(number.intValue());
        }

        return jsEngineContext.coerceJavaScriptToJava(null, jsEngineContext.getKeyObject(pointer, jsEngineContext.coerceJavaToJavaScript(key)));
    }

    public boolean set(String key, Object value) {
        return jsEngineContext.setKeyString(pointer, key, value);
    }

    public boolean set(int index, Object value) {
        return jsEngineContext.setKeyInteger(pointer, index, value);
    }

    @Override
    public boolean set(Object key, Object value) {
        if (key instanceof String) {
            return set((String)key, value);
        }

        if (key instanceof Number) {
            Number number = (Number)key;
            if (number.doubleValue() == number.intValue()) {
                return set(number.intValue(), value);
            }
        }

        return jsEngineContext.setKeyObject(pointer, key, value);
    }

    @Override
    public String toString() {
        Object ret = callProperty("toString");
        if (ret == null)
            return null;
        return ret.toString();
    }

    static Object[] coerceArgs(JSEngineContext jsEngineContext, Method method, Object[] args) {
        if (args != null && args.length > 0) {
            Class[] types = method.getParameterTypes();

            if (args.length != types.length)
                throw new AssertionError("JavaScript.createInvocationHandler different args count?");

            int numParameters = types.length;
            if (method.isVarArgs())
                numParameters--;

            for (int i = 0; i < numParameters; i++) {
                args[i] = jsEngineContext.coerceJavaToJavaScript(types[i], args[i]);
            }

            if (method.isVarArgs()) {
                Class varargType = method.getParameterTypes()[numParameters].getComponentType();
                ArrayList<Object> varargs = new ArrayList<>(Arrays.asList(args).subList(0, numParameters));
                Object varargArg = args[numParameters];
                for (int i = 0; i < Array.getLength(varargArg); i++) {
                    Object vararg = Array.get(varargArg, i);
                    varargs.add(jsEngineContext.coerceJavaScriptToJava(varargType, vararg));
                }
                args = varargs.toArray();
            }
        }

        return args;
    }

    public InvocationHandler getWrappedInvocationHandler(InvocationHandler wrapped) {
        return jsEngineContext.getWrappedInvocationHandler(this, (proxy, method, args) -> {
            if (method.getDeclaringClass() == JSEngineJavaScriptObject.class)
                return method.invoke(JavaScriptObject.this, args);

            return wrapped.invoke(proxy, method, args);
        });
    }

    public InvocationHandler createInvocationHandler() {
        InvocationHandler handler = (proxy, method, args) -> {
            Method interfaceMethod = JSEngineContext.getInterfaceMethod(method);
            JSEngineMethodCoercion methodCoercion = jsEngineContext.JavaToJavascriptMethodCoercions.get(interfaceMethod);
            if (methodCoercion != null)
                return methodCoercion.invoke(interfaceMethod, this, args);

            JSEngineProperty property = method.getAnnotation(JSEngineProperty.class);
            if (property != null) {
                if (args == null || args.length == 0)
                    return jsEngineContext.coerceJavaScriptToJava(method.getReturnType(), JavaScriptObject.this.get(property.name()));
                JavaScriptObject.this.set(property.name(), jsEngineContext.coerceJavaScriptToJava(method.getParameterTypes()[0], args[0]));
                return null;
            }

            String methodName = method.getName();
            JSEngineMethodName annotation = method.getAnnotation(JSEngineMethodName.class);
            if (annotation != null)
                methodName = annotation.name();

            return jsEngineContext.coerceJavaScriptToJava(method.getReturnType(), JavaScriptObject.this.callProperty(methodName, coerceArgs(jsEngineContext, method, args)));
        };

        return getWrappedInvocationHandler(handler);
    }

    public <T> T proxyInterface(Class<T> clazz, Class... more) {
        ArrayList<Class> classes = new ArrayList<>();
        classes.add(JSEngineJavaScriptObject.class);
        classes.add(clazz);
        if (more != null)
            Collections.addAll(classes, more);

        return (T)Proxy.newProxyInstance(clazz.getClassLoader(), classes.toArray(new Class[0]), createInvocationHandler());
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (jsEngineContext != null)
            jsEngineContext.finalizeJavaScriptObject(pointer);
    }

    public JSValue asJSValue() {
        return new JSValue(jsEngineContext, this);
    }
}
