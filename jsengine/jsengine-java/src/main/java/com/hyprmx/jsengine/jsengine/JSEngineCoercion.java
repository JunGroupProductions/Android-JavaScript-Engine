package com.hyprmx.jsengine.jsengine;

/**
 * Coerce a value passing through Duktape to the desired output class.
 */
@SuppressWarnings("rawtypes")
public interface JSEngineCoercion<T, F> {
  T coerce(Class clazz, F o);
}
