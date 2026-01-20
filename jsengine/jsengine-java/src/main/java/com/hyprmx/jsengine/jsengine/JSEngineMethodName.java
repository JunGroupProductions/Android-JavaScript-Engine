package com.hyprmx.jsengine.jsengine;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface JSEngineMethodName {
    String name() default "";
}
