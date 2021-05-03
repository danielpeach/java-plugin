# JVM Plugin

JVM plugin is a client-side implementation of
[go-plugin](https://github.com/hashicorp/go-plugin). You can use it to run
plugins written in Go (or any language, but Go is best supported) from a JVM application.

## Features

JVM plugin implements many of `go-plugin`'s features:

- Bidirectional communication
- Complex arguments and return values
- Protocol versioning
- Logging and stdout/stderr syncing
- Automatic mTLS

JVM plugin supports plugins communicating via gRPC over Unix domain sockets. It
does not support Go's `net/rpc` protocol or communication over `localhost`.

JVM plugin is tested on `macos-latest` and `ubuntu-latest`.

## Examples

The `*-example` directories contain runnable examples. If you're trying to run
these examples from Intellij, please remember to run the `buildGo` gradle task
beforehand.

## Bugs

Automatic mTLS does not work for plugins using bidirectional communication.
This is a [bug in the server-side
framework](https://github.com/hashicorp/go-plugin/issues/109).

