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

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hyprmx.android.jsengine.JSEngineContext.isEmpty;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class JavaObject implements JSEngineObject, JSEngineJavaObject {
    private final Object target;
    private final JSEngineContext jsEngineContext;

    public JavaObject(JSEngineContext jsEngineContext, Object target) {
        this.jsEngineContext = jsEngineContext;
        this.target = target;
    }

    @Override
    public Object getObject() {
        return target;
    }

    public static Method getGetterMethod(String key, Method[] methods) {
        // Captured as AccessibleObject[] (not Method[]): R8 horizontally merges the
        // MemoizeFunc lambda classes in this library, and merged constructors whose
        // captures are sibling array types (Method[] here, Constructor[] in construct())
        // share a register without a checkcast, causing a VerifyError at class load
        // in minified consumer apps. Identical capture types make the merge safe.
        //noinspection UnnecessaryLocalVariable
        AccessibleObject[] members = methods;
        return JSEngineContext.javaObjectGetter.memoize(() -> {
            for (AccessibleObject member : members) {
                Method method = (Method) member;
                // name match, no args, and a return type
                if (method.getParameterTypes().length != 0)
                    continue;
                if (method.getReturnType() == void.class || method.getReturnType() == Void.class)
                    continue;
                JSEngineProperty property = method.getAnnotation(JSEngineProperty.class);
                if (property == null)
                    continue;
                String propName = property.name();
                if (isEmpty(propName))
                    propName = method.getName();
                if (propName.equals(key))
                    return method;
            }
            return null;
        }, key, methods);
    }

    public static Method getSetterMethod(String key, Method[] methods) {
        // AccessibleObject[] capture: see getGetterMethod.
        //noinspection UnnecessaryLocalVariable
        AccessibleObject[] members = methods;
        return JSEngineContext.javaObjectSetter.memoize(() -> {
            for (AccessibleObject member : members) {
                Method method = (Method) member;
                // name match, no args, and a return type
                if (method.getParameterTypes().length != 1)
                    continue;
                if (method.getReturnType() != void.class && method.getReturnType() != Void.class)
                    continue;
                JSEngineProperty property = method.getAnnotation(JSEngineProperty.class);
                if (property == null)
                    continue;
                String propName = property.name();
                if (isEmpty(propName))
                    propName = method.getName();
                if (propName.equals(key))
                    return method;
            }
            return null;
        }, key, methods);
    }

    private static boolean hasMethod(Class clazz, String key, boolean requiresStatic) {
        // try to get methods
        for (Method method : clazz.getMethods()) {
            if (requiresStatic && !Modifier.isStatic(method.getModifiers()))
                continue;
            if (method.getName().equals(key))
                return true;
            JSEngineMethodName annotation = method.getAnnotation(JSEngineMethodName.class);
            if (annotation != null && annotation.name().equals(key))
                return true;
        }
        return false;
    }

    private Field findField(String key, Class clazz) {
        return JSEngineContext.javaObjectFields.memoize(() -> {
            // try to get fields
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getName().equals(key) && ((field.getModifiers() & Modifier.STATIC) == 0) && ((field.getModifiers() & Modifier.PUBLIC) != 0))
                    return field;
            }

            if (target instanceof Class) {
                for (Field field : ((Class)target).getDeclaredFields()) {
                    if (field.getName().equals(key) && ((field.getModifiers() & Modifier.STATIC) != 0) && ((field.getModifiers() & Modifier.PUBLIC) != 0))
                        return field;
                }
            }

            return null;
        }, key, clazz.getDeclaredFields());
    }

    public Object get(String key) {
        Object ret = getMap(key);
        if (ret != null)
            return ret;

        Class clazz = target.getClass();
        if (!Proxy.isProxyClass(clazz)) {
            // length is not a field of the array class. it's a language property.
            // Nor can arrays be cast to Array.
            if (clazz.isArray() && "length".equals(key))
                return Array.getLength(target);

            Field f = findField(key, clazz);

            if (f != null) {
                try {
                    return jsEngineContext.coerceJavaToJavaScript(f.get(target));
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }

        Method g = getGetterMethod(key, clazz.getMethods());
        if (g != null) {
            try {
                return jsEngineContext.coerceJavaToJavaScript(g.invoke(target));
            }
            catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        Boolean m = JSEngineContext.javaObjectMethods.memoize(() -> {
            if (hasMethod(clazz, key, false))
                return true;
            if (target instanceof Class)
                return hasMethod((Class)target, key, true);
            return false;
        }, key, clazz.getMethods());

        if (m)
            return new JavaMethodObject(jsEngineContext, target, key);

        return null;
    }

    public Object get(int index) {
        if (target.getClass().isArray())
            return Array.get(target, index);
        if (target instanceof List)
            return ((List)target).get(index);
        return null;
    }

    private Object getMap(Object key) {
        if (target instanceof Map)
            return jsEngineContext.coerceJavaToJavaScript(((Map)target).get(key));
        return null;
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String)
            return get((String)key);

        if (key instanceof Number) {
            Number number = (Number)key;
            if (number.doubleValue() == number.intValue())
                return get(number.intValue());
        }

        return getMap(key);
    }

    private void noSet() {
        throw new UnsupportedOperationException("can not set value on this JavaObject");
    }

    public boolean set(int index, Object value) {
        if (target instanceof Array) {
            Array.set(target, index, value);
            return true;
        }
        if (target instanceof List) {
            ((List)target).set(index, value);
            return true;
        }

        noSet();
        return false;
    }

    private boolean putMap(Object key, Object value) {
        if (target instanceof Map) {
            ((Map)target).put(key, value);
            return true;
        }
        noSet();
        return false;
    }

    public boolean set(String key, Object value) {
        Class clazz = target.getClass();

        Field f = findField(key, clazz);
        if (f != null) {
            try {
                f.set(target, jsEngineContext.coerceJavaScriptToJava(f.getType(), value));
                return true;
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
        }

        Method s = getSetterMethod(key, clazz.getMethods());
        if (s != null) {
            try {
                jsEngineContext.coerceJavaToJavaScript(s.invoke(target, jsEngineContext.coerceJavaScriptToJava(s.getParameterTypes()[0], value)));
            }
            catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
            return true;
        }

        return putMap(key, value);
    }

    @Override
    public boolean set(Object key, Object value) {
        if (key instanceof Number) {
            Number number = (Number)key;
            if (number.doubleValue() == number.intValue()) {
                return set(number.intValue(), value);
            }
        }

        if (key instanceof String) {
            return set((String)key, value);
        }

        return putMap(key, value);
    }

    @Override
    public Object callMethod(Object thiz, Object... args) {
        throw new UnsupportedOperationException("can not call " + target);
    }

    public Object callProperty(Object property, Object... args) {
        if (property == null)
            throw new NullPointerException();
        property = get(property);
        if (property instanceof JSEngineObject)
            return ((JSEngineObject)property).callMethod(this, args);
        throw new UnsupportedOperationException("can not call " + target);
    }

    @Override
    public Object construct(Object... args) {
        if (!(target instanceof Class))
            return JSEngineObject.super.construct(args);

        Class clazz = (Class)target;
        Constructor[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            }
            catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        ArrayList<Class> argTypes = new ArrayList<>();
        for (Object arg: args) {
            if (arg == null)
                argTypes.add(null);
            else
                argTypes.add(arg.getClass());
        }
        // AccessibleObject[] capture: see getGetterMethod.
        //noinspection UnnecessaryLocalVariable
        AccessibleObject[] members = constructors;
        Constructor best = JSEngineContext.javaObjectConstructorCandidates.memoize(() -> {
            Constructor ret = null;
            int bestScore = Integer.MAX_VALUE;
            for (AccessibleObject member: members) {
                Constructor constructor = (Constructor) member;
                // parameter count is most important
                int score = Math.abs(argTypes.size() - constructor.getParameterTypes().length) * 1000;
                // tiebreak by checking parameter types
                for (int i = 0; i < Math.min(constructor.getParameterTypes().length, argTypes.size()); i++) {
                    // check if the class is assignable or both parameters are numbers
                    Class<?> argType = argTypes.get(i);
                    Class<?> paramType = constructor.getParameterTypes()[i];
                    if (paramType == argType) {
                        score -= 4;
                    }
                    if (JSEngineContext.isNumberClass(paramType) && JSEngineContext.isNumberClass(argType)) {
                        score -= 3;
                    }
                    else if ((paramType == Long.class || paramType == long.class) && argType == String.class) {
                        score -= 2;
                    }
                    else if (argType == null || paramType.isAssignableFrom(argType)) {
                        score -= 1;
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    ret = constructor;
                }
            }
            return ret;
        }, target, constructors, argTypes.toArray());

        try {
            int numParameters = best.getParameterTypes().length;
            if (best.isVarArgs())
                numParameters--;
            ArrayList<Object> coerced = new ArrayList<>();
            int i = 0;
            for (; i < numParameters; i++) {
                if (i < args.length)
                    coerced.add(jsEngineContext.coerceJavaScriptToJava(best.getParameterTypes()[i], args[i]));
                else
                    coerced.add(null);
            }
            if (best.isVarArgs()) {
                Class varargType = best.getParameterTypes()[numParameters].getComponentType();
                ArrayList<Object> varargs = new ArrayList<>();
                for (; i < args.length; i++) {
                    varargs.add(jsEngineContext.coerceJavaScriptToJava(varargType, args[i]));
                }
                coerced.add(JavaMethodObject.toArray(varargType, varargs));
            }
            else if (i < args.length) {
                System.err.println("dropping javascript to java arguments on the floor: " + (args.length - i) + " " + best.toString());
            }
            return jsEngineContext.coerceJavaToJavaScript(best.newInstance(coerced.toArray()));
        }
        catch (IllegalAccessException e) {
            throw new IllegalArgumentException(best.toString(), e);
        }
        catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException)
                throw (RuntimeException)e.getTargetException();
            throw new IllegalArgumentException(best.toString(), e);
        }
        catch (InstantiationException e) {
            throw new IllegalArgumentException(best.toString(), e);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(best.toString(), e);
        }
    }
}
