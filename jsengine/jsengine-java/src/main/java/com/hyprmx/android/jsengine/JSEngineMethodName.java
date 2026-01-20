package com.hyprmx.android.jsengine;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface JSEngineMethodName {
    String name() default "";
}
