package com.hyprmx.android.jsengine.sample;

import com.hyprmx.android.jsengine.JSEngineProperty;

// Exercised from JS to drive JavaObject.construct() (constructor candidate lookup),
// JavaMethodObject's method dispatch, and JavaObject's annotated getGetterMethod/
// getSetterMethod lookup - the four call sites R8 horizontal class merging broke,
// via mismatched lambda-capture array types, before PLAYER-27424.
public class Widget {
    private String label = "";
    private int counter;

    public Widget() {
    }

    public Widget(String label) {
        this.label = label;
    }

    public Widget(String label, int counter) {
        this.label = label;
        this.counter = counter;
    }

    @JSEngineProperty(name = "label")
    public String getLabel() {
        return label;
    }

    @JSEngineProperty(name = "label")
    public void setLabel(String label) {
        this.label = label;
    }

    public String greet(String name) {
        counter++;
        return "hello, " + name + " (#" + counter + ")";
    }
}
