package com.hyprmx.android.jsengine;

import java.lang.reflect.AccessibleObject;

// Like MemoizeFunc, but takes the reflect array as a process() parameter instead of a
// captured local. R8 horizontally merges lambda classes across call sites; if two lambdas
// captured sibling array types (e.g. Method[] vs Constructor[]) as fields, the merged class
// would have a shared field typed as non-array AccessibleObject, which ART's bytecode
// verifier rejects at class load. Passing the array as a parameter instead makes that
// impossible to reintroduce - never change this back to a captured field.
public interface MemoizeArrayFunc<T> {
  T process(AccessibleObject[] members);
}
