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

//import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

@SuppressWarnings({"unchecked", "rawtypes"})
public class JavaMethodObject implements JSEngineMethodObject {
    String target;
    JSEngineContext jsEngineContext;
    Object originalThis;
    public JavaMethodObject(JSEngineContext jsEngineContext, Object originalThis, String method) {
        this.jsEngineContext = jsEngineContext;
        this.originalThis = originalThis;
        this.target = method;
    }

    protected Object getThis(Object thiz, Method method) {
        return thiz;
    }

    protected Method[] getMethods(Object thiz) {
        Method[] methods = thiz.getClass().getMethods();
        if (!(thiz instanceof Class))
            return methods;

        ArrayList<Method> arr = new ArrayList<>();
        Collections.addAll(arr, methods);
        Collections.addAll(arr, ((Class)thiz).getMethods());
        return arr.toArray(new Method[0]);
    }

    @Override
    public Object callMethod(Object thiz, Object... args) {
        if (thiz == null || thiz instanceof JavaScriptObject)
            thiz = originalThis;
        if (thiz == null)
            throw new UnsupportedOperationException("can not call " + target);
        thiz = jsEngineContext.coerceJavaScriptToJava(Object.class, thiz);

        Method[] thisMethods = getMethods(thiz);
        ArrayList<Class> argTypes = new ArrayList<>();
        for (Object arg: args) {
            if (arg == null)
                argTypes.add(null);
            else
                argTypes.add(arg.getClass());
        }
        Method best = JSEngineContext.javaObjectMethodCandidates.memoize(() -> {
            Method ret = null;
            int bestScore = Integer.MAX_VALUE;
            for (Method method: thisMethods) {
                if (!method.getName().equals(target)) {
                    JSEngineMethodName annotation = method.getAnnotation(JSEngineMethodName.class);
                    if (annotation == null || !annotation.name().equals(target))
                        continue;
                }
                // parameter count is most important
                int score = Math.abs(argTypes.size() - method.getParameterTypes().length) * 1000;
                // tiebreak by checking parameter types
                for (int i = 0; i < Math.min(method.getParameterTypes().length, argTypes.size()); i++) {
                    // check if the class is assignable or both parameters are numbers
                    Class<?> argType = argTypes.get(i);
                    Class<?> paramType = method.getParameterTypes()[i];
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
                    ret = method;
                }
            }
            return ret;
        }, target, thisMethods, argTypes.toArray());

        if (best == null)
            throw new UnsupportedOperationException("can not call " + target);

        thiz = getThis(thiz, best);

        try {
            Method interfaceMethod = JSEngineContext.getInterfaceMethod(best);
            JSEngineMethodCoercion methodCoercion = jsEngineContext.JavaScriptToJavaMethodCoercions.get(interfaceMethod);
            if (methodCoercion != null)
                return methodCoercion.invoke(interfaceMethod, thiz, args);

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
                coerced.add(toArray(varargType, varargs));
            }
            else if (i < args.length) {
                System.err.println("dropping javascript to java arguments on the floor: " + (args.length - i) + " " + best.toString());
            }
//            System.out.println(best.getDeclaringClass().getSimpleName() + "." + best.getName());
            return jsEngineContext.coerceJavaToJavaScript(best.invoke(thiz, coerced.toArray()));
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException)
                throw (RuntimeException)e.getTargetException();
            if (e.getTargetException() instanceof Error)
                throw (Error)e.getTargetException();
            throw new RuntimeException(e.getTargetException());
        }
    }

    static <T> T[] toArray(Class<T> varargType, ArrayList<T> varargs) {
        return varargs.toArray((T[])Array.newInstance(varargType, 0));
    }
}
