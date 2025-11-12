package com.abc.core.jsengine;

public interface JSEnginePromise {
    JSEnginePromise then(JSEnginePromiseReceiver receiver);
    @JSEngineMethodName(name = "catch")
    JSEnginePromise caught(JSEnginePromiseReceiver receiver);
}
