package com.hyprmx.jsengine.jsengine;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface JSEngineProperty {
    String name() default "";
}
