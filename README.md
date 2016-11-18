# Korolev

Not long ago we enter to the era of single-page applications. Some people say we no longer need a server. They say JavaScript applications can connect to DBMS directly. Fat clients. I disagree with it. This project is an approach to solve problem of modern fat web.

Korolev runs single-page application on the server side, keeping in browser  only the bridge to receive commands and send events. The page loads instantly and works fast, cause doesn't produce almost no computations. It's important that Korolev makes single environment for full stack development. Client and server now is one app without REST protocol or something else.

The project supports static page rendering to allow search engines to index a pages and view pages immediately.

**Warning!** Korolev is under heavy development and not ready to use until 0.1.0 release. Now API is highly unstable.

## Principles

1. **Thin client.** Let's be fair, modern JavaScript application are to greedy. Any JavaScript developer thinks that his page is special. But user has different opinion. He opens dozens of tabs. Every tab contains a tons of code, and this works very slow. So we make our JavaScript bridge as lightweight as possible.

2. **Immutable and pure.** Really, we don't need mutability even in frontend. Especially in frontend. Most of modern JavaScript frameworks trying to be functional. And we are too.

3. **Rapid development.** Everything for efficiency. We trying to make Korolev bullshit and boilerplate free. Hope our words are not bullshit.

## Quick Start

Add `korolev-server` to your `build.sbt`

```scala
libraryDependencies += "com.github.fomkin" %% "korolev-server" % "0.0.5-PRE"
```

Look for [Example.scala](https://github.com/fomkin/korolev/blob/master/example/src/main/scala/Example.scala). It's updates very frequently, so we don't paste code here, sorry guys.

## Plans

All new features and changes are registered in [issues](https://github.com/fomkin/korolev/issues) of this repository. First goal is to ship a ready to use version with semi-stable API.
