# JSEngine

JSEngine provides Java (Android and desktop) bindings to JavaScript engines. 

## Runtimes

JSEngine supports both the Duktape and QuickJS runtimes.

## Features

* Share objects between runtimes seamlessly.
* Javascript Debugging support
* Invocations can be intercepted and coerced both in and out of the JavaScript runtime.

## Examples

### Evaluating JavaScript

#### JavaScript
```javascript
'hello'
```

#### Java:

Calling JSEngineContext.evaluate will return a Java Object with the evaluation result.

```java
JSEngineContext quack = JSEngineContext.create();
Object result = quack.evaluate(javascriptString);
System.out.println(result);
// prints "hello"
```

### Evaluating and Calling JavaScript Functions

#### JavaScript
```javascript
(function () {
  return 'hello';
})
```
#### Java
```java
JSEngineContext quack = JSEngineContext.create();
JavaScriptObject result = quack.evaluateForJavaScriptObject(javascriptString);
System.out.println(result.call());
// prints "hello"
```

### Passing Data to JavaScript

#### JavaScript

```javascript
(function (str) {
  return str + ' world';
})
```
#### Java
```java
JSEngineContext quack = JSEngineContext.create();
JavaScriptObject result = quack.evaluateForJavaScriptObject(javascriptString);
System.out.println(result.call("hello"));
// prints "hello world"
```


### Setting and using Global Properties

#### JavaScript

```javascript
System.out.println('hello world');
```
#### Java
```java
JSEngineContext quack = JSEngineContext.create();
quack.getGlobalObject().set("System", System.class);
quack.evaluate(javascriptString);
// prints "hello world"
```

### Passing Objects to JavaScript

#### JavaScript
```javascript
(function(obj) {
  obj.hello();
})
```
#### Java
```java
class Foo {
  public void hello() {
    System.out.println("hello");
  }
}

JSEngineContext quack = JSEngineContext.create();
JavaScriptObject result = quack.evaluateForJavaScriptObject(javascriptString);
quack.call(new Foo());
// prints "hello world"
```

### Passing Interfaces to JavaScript

#### JavaScript
```javascript
(function(func) {
  // notice that it is not func.run()
  // single method interfaces (lambdas) are automatically coerced into functions!
  func();
})
```
#### Java
```java
Runnable runnable = () -> {
  System.out.println("hello world");
}

JSEngineContext quack = JSEngineContext.create();
JavaScriptObject result = quack.evaluateForJavaScriptObject(javascriptString);
result.call(runnable);
// prints "hello world"
```

### Passing Interfaces back to Java
#### JavaScript
```javascript
return {
  hello: function(printer) {
    printer('hello world');
  }
}
```
#### Java
```java
interface Foo {
  void hello(Printer printer);
}

interface Printer {
  print(String str);
}

JSEngineContext quack = JSEngineContext.create();
Foo result = quack.evaluate(javascriptString, Foo.class);  
result.hello(str -> System.out.println(str));
// prints "hello world"
```

### Creating Java Objects in JavaScript
#### JavaScript
```javascript
var foo = new Foo();
foo.hello('hello world');
```
#### Java
```java
class Foo {
  public void hello(String str) {
    System.out.println(str);
  }
}

JSEngineContext quack = JSEngineContext.create();
quack.getGlobalObject().set("Foo", Foo.class);
quack.evaluate(javascriptString);
// prints "hello world"
```

### Creating Java Objects in JavaScript (simplified)
#### JavaScript
```javascript
var Foo = JavaClass.forName("com.whatever.Foo");
var foo = new Foo();
foo.hello('hello world');
```
#### Java
```java
class Foo {
  public void hello(String str) {
    System.out.println(str);
  }
}

JSEngineContext quack = JSEngineContext.create();
quack.getGlobalObject().set("JavaClass", Class.class);
quack.evaluate(javascriptString);
// prints "hello world"
```

## Marshalling

Types need to be marshalled when passing between the runtimes. The class specifier and parameter types
are used to determine the behavior when being marshalled. The following builtin types are marshalled as follows:

JavaScript (Input) | Java (Output)
|---|---|
number | Number (Integer or Double)
Uint8Array | ByteBuffer (direct, deep copy)
undefined | null

Java (Input) | JavaScript (Output)
|---|---|
long | string (otherwise precision is lost)
ByteBuffer (direct or byte array backed) | Uint8Array (deep copy)
byte, short, int, float, double | number
null | null


### Coercions

Types and methods can be coerced between runtimes.

### Java to JavaScript Type Coercion
#### JavaScript
```javascript
(function(data) {
  return data;
})
```
#### Java
```java
class Foo {}

JSEngineContext quack = JSEngineContext.create();
// all instances of Foo sent to JavaScript get coerced into the String "hello world"
quack.putJavaToJavaScriptCoercion(Foo.class, (clazz, o) -> "hello world");
System.out.println(quack.evaluateForJavaScriptObject.call(new Foo()));
// prints "hello world"
```

## Concurrency

JavaScript runtimes are single threaded. All execution in the JavaScript runtime is gauranteed thread safe, by way of Java synchronization.

## Garbage Collection

When a Java object is passed to the JavaScript runtime, a hard reference is held by the JavaScript proxy counterpart. This reference is removed when the JavaScriptObject is finalized. And same for when a Java object is passed to the JavaScript runtime.
JavaScriptObjects sent to the Java runtime will be deduped, so the same proxy instance is always used. JavaObjects sent to JavaScript will marshall a new Proxy object every time.

## Debugging

Install the appropriate [QuickJS Debugger](https://marketplace.visualstudio.com/items?itemName=koush.quickjs-debug) or [Duktape Debugger](https://marketplace.visualstudio.com/items?itemName=HaroldBrenes.duk-debug) for VS Code. QuickJS is the default runtime used by JSEngine.

### JavaScript
```javascript
System.out.println('set a breakpoint here!');
```

### Java
```java
JSEngineContext quack = JSEngineContext.create();
quack.getGlobalObject().set("System", System.class);
quack.waitForDebugger("0.0.0.0:9091")
// attach using VS Code
quack.evaluate(javascriptString);
```

## Attribution

JSEngine was refactored from Quack by Koushik Dutta (https://github.com/koush/quack), which was initially forked from Square's Duktape Android library.
