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
package com.abc.core.jsengine;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class JSValue implements JSEngineJavaObject {
    JSEngineContext jsEngineContext;
    Object value;
    JSValue(JSEngineContext jsEngineContext, Object value) {
        this.jsEngineContext = jsEngineContext;
        this.value = value;
    }

    @Override
    public Object getObject() {
        return value;
    }

    public boolean isNumber() {
        return value instanceof Number;
    }

    public boolean isString() {
        return value instanceof String;
    }

    public boolean isJavaScriptObject() {
        return value instanceof JavaScriptObject;
    }

    public boolean isByteBuffer() {
        return value instanceof ByteBuffer;
    }

    public boolean isNullOrUndefined() {
        return value == null;
    }

    public <T> T as(Class<T> clazz) {
        return (T)jsEngineContext.coerceJavaScriptToJava(clazz, value);
    }

    public <T> Iterable<T> asIterable(Class<T> clazz) {
        JSValue iteratorSymbol = jsEngineContext.evaluateForJavaScriptObject("Symbol").asJSValue().get("iterator");
        JSValue iteratorFunc = get(iteratorSymbol);
        JSValue iterator = iteratorFunc.apply(this);
        JSValue iteratorNext = iterator.get("next");
        return () -> new Iterator<T>() {
            JSValue current;

            private void maybeNext() {
                if (current != null)
                    return;
                current = iteratorNext.apply(iterator);
            }

            @Override
            public boolean hasNext() {
                maybeNext();
                return !(Boolean)current.get("done").getObject();
            }

            @Override
            public T next() {
                maybeNext();
                if ((Boolean)current.get("done").getObject())
                    throw new NoSuchElementException("end of iterator");
                T ret = current.get("value").as(clazz);
                current = null;
                return ret;
            }
        };
    }

    private JSEngineObject jsEngineContextify() {
        if (value instanceof JSEngineObject)
            return (JSEngineObject)value;
        return new JavaObject(jsEngineContext, value);
    }

    public JSValue get(Object key) {
        return new JSValue(jsEngineContext, jsEngineContextify().get(key));
    }

    public boolean set(Object key, Object value) {
        return jsEngineContextify().set(key, value);
    }

    public boolean has(Object key) {
        return jsEngineContextify().has(key);
    }

    public JSValue apply(Object thiz, Object... args) {
        return new JSValue(jsEngineContext, jsEngineContextify().callMethod(thiz, args));
    }

    public JSValue construct(Object... args) {
        return new JSValue(jsEngineContext, jsEngineContextify().construct(args));
    }

    @Override
    public String toString() {
        if (value == null)
            return "null";
        return value.toString();
    }
}
