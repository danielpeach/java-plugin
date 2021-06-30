![CI](https://github.com/danielpeach/java-plugin/workflows/Build/badge.svg)
[![javadoc](https://javadoc.io/badge2/io.github.danielpeach.java-plugin/java-plugin/javadoc.svg)](https://javadoc.io/doc/io.github.danielpeach.java-plugin/java-plugin)
# Java Plugin

`java-plugin` is a client-side implementation of
[go-plugin](https://github.com/hashicorp/go-plugin). You can use it to run
plugins written in Go (or any language, but Go is best supported) from a JVM application.

## Usage

Let's say you have a Java (or Kotlin, or Groovy) interface. You'd like to be
able to implement that interface in Go rather than in a JVM language. You can use `java-plugin`!

Imagine your interface is a simple `Counter`:

```kotlin
interface Counter {
  fun put(key: String, value: Long, adder: (a: Long, b: Long) -> Long)
  fun get(key: String): Long
}
```

When `put`-ing a new value with a `Counter`, you can pass an `adder` lambda
implementation that maps the old value into a new value to be stored.

If you have a binary that implements this interface, you can fetch a `Counter` like so:

```kotlin
val manager = Manager()
val client = manager.start(
  ClientConfig(
    handshakeConfig = HandshakeConfig(
      magicCookieKey = "key",
      magicCookieValue = "value",
      protocolVersion = 1
    ),
    cmd = listOf("./path/to/plugin/binary"),
    plugins = listOf(CounterPlugin())
  )
)

// This Counter looks like a normal Java interface!
val counter = client.dispense<Counter>()

counter.put("key", 10) { a, b ->
  println("adding $a + $b")
  a + b
}
```

For more implementation guidance, see the [Examples](#examples).

## Features

`java-plugin` implements many of `go-plugin`'s features:

- Bidirectional communication
- Complex arguments and return values
- Protocol versioning
- Logging and stdout/stderr syncing
- Automatic mTLS

`java-plugin` supports plugins communicating via gRPC over Unix domain sockets. It
does not support Go's `net/rpc` protocol or communication over `localhost`.

`java-plugin` is tested on `macos-latest` and `ubuntu-latest`.

## Examples

The `*-example` directories contain complete, runnable plugin examples. Be sure to clone this
repo with its submodules:

```bash
git clone git@github.com:danielpeach/java-plugin.git --recurse-submodules
```

If you're trying to run the examples from Intellij, please remember to run the `buildGo` gradle task
beforehand.

## Bugs

Automatic mTLS does not work for plugins using bidirectional communication.
This is a [bug in the server-side
framework](https://github.com/hashicorp/go-plugin/issues/109).

