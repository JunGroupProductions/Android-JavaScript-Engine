package com.hyprmx.jsengine.jsengine

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun JSEnginePromise.await(): Any {
    return suspendCoroutine<Any> { resume ->
        this.then {
            resume.resume(it)
        }.caught {
            try {
                if (it !is JavaScriptObject)
                    throw JSEngineException("JavaScript Error type not thrown")
                val jo: JavaScriptObject = it
                jo.jsEngineContext.evaluateForJavaScriptObject("(function(t) { throw t; })").call(it);
            }
            catch (e: Throwable) {
                resume.resumeWithException(e)
            }
        }
    }
}
