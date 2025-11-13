package com.hyprmx.jsengine.jsengine;

/**
 * Will be coerced into a JSON object when received by the JavaScript runtime. The string must be valid JSON.
 */
public final class JSEngineJsonObject {
    final public String json;
    public JSEngineJsonObject(String json) {
        this.json = json;
    }
}
