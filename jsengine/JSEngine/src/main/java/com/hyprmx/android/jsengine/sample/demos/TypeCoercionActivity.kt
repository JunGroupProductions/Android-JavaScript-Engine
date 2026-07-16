package com.hyprmx.android.jsengine.sample.demos

import com.hyprmx.android.jsengine.JSEngineJsonObject
import com.hyprmx.android.jsengine.JSEngineProperty
import com.hyprmx.android.jsengine.JavaScriptObject
import com.hyprmx.android.jsengine.sample.R
import java.nio.ByteBuffer

/**
 * How values are marshalled across the Java <-> JavaScript boundary. Covers numeric widening,
 * enum round-tripping, JSON injection, ByteBuffer <-> Uint8Array, typed property proxies, and a
 * custom Java->JavaScript coercion. Mirrors testInterface / testEnum* / testJson / testBuffer* /
 * testJavaScriptProperty / testCoercion.
 */
class TypeCoercionActivity : DemoActivity() {

    override val descriptionRes: Int = R.string.type_coercion_desc

    enum class Fruit { APPLE, BANANA, CHERRY }

    /** Typed proxy over a JS object using @JSEngineProperty getters/setters. */
    interface HasName {
        @get:JSEngineProperty(name = "name")
        @set:JSEngineProperty(name = "name")
        var name: String
    }

    /** Coerced into "coerced!" whenever an instance is passed to JavaScript. */
    class Marker

    override fun onRunClicked() = runAsync {
        JSDemoRunner.withContext { context ->
            val identity: JavaScriptObject =
                context.compileFunction("function(x) { return x; }", "identity.js")

            // Numeric widening: a byte round-trips back as an Integer or Double.
            JSDemoRunner.runStep(this, "byte 7 -> JS -> Java type") {
                identity.call(7.toByte())?.javaClass?.simpleName
            }
            JSDemoRunner.runStep(this, "double 3.14 -> JS -> Java type") {
                identity.call(3.14)?.javaClass?.simpleName
            }
            // longs must cross as strings to avoid precision loss.
            JSDemoRunner.runStep(this, "long -> JS -> Java type") {
                identity.call(9_000_000_000L)?.javaClass?.simpleName
            }

            // Enums round-trip as their name string.
            JSDemoRunner.runStep(this, "enum BANANA -> JS") {
                identity.call(Fruit.BANANA)
            }
            JSDemoRunner.runStep(this, "enum name -> Java Fruit") {
                val back = identity.call("CHERRY")
                context.coerceJavaScriptToJava(Fruit::class.java, back)
            }

            // JSEngineJsonObject is parsed into a real JS object.
            JSDemoRunner.runStep(this, "JSON {meaningOfLife:42} -> property") {
                val obj = identity.call(JSEngineJsonObject("{\"meaningOfLife\":42}")) as JavaScriptObject
                obj.get("meaningOfLife")
            }

            // ByteBuffer -> Uint8Array (in).
            JSDemoRunner.runStep(this, "ByteBuffer -> Uint8Array sum") {
                val buf = ByteBuffer.allocate(5)
                for (i in 0 until 5) buf.put(i, i.toByte())
                val sum: JavaScriptObject = context.compileFunction(
                    "function(u) { var s = 0; for (var i = 0; i < u.length; i++) s += u[i]; return s; }",
                    "sum.js"
                )
                sum.call(buf)
            }

            // Uint8Array -> ByteBuffer (out).
            JSDemoRunner.runStep(this, "Uint8Array -> ByteBuffer bytes") {
                val make: JavaScriptObject = context.compileFunction(
                    "function() { var u = new Uint8Array(4); for (var i = 0; i < 4; i++) u[i] = i * 2; return u; }",
                    "make.js"
                )
                val out = context.coerceJavaScriptToJava(ByteBuffer::class.java, make.call()) as ByteBuffer
                ByteArray(4) { out.get(it) }
            }

            // Typed @JSEngineProperty proxy.
            JSDemoRunner.runStep(this, "@JSEngineProperty get/set") {
                val proxied = context.evaluate(HasName::class.java, "({ name: 'initial' })", "proxy.js")
                val before = proxied.name
                proxied.name = "changed"
                "$before -> ${proxied.name}"
            }

            // Custom Java -> JavaScript coercion.
            JSDemoRunner.runStep(this, "Custom coercion Marker -> string") {
                context.putJavaToJavaScriptCoercion(Marker::class.java) { _, _ -> "coerced!" }
                identity.call(Marker())
            }
        }
    }
}
