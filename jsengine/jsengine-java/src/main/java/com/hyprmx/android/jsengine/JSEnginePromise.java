package com.hyprmx.android.jsengine;

public interface JSEnginePromise {
    JSEnginePromise then(JSEnginePromiseReceiver receiver);
    @JSEngineMethodName(name = "catch")
    JSEnginePromise caught(JSEnginePromiseReceiver receiver);
}
