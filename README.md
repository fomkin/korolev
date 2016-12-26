[![Build Status](https://travis-ci.org/fomkin/korolev.svg?branch=master)](https://travis-ci.org/fomkin/korolev) [![Gitter](https://badges.gitter.im/fomkin/korolev.svg)](https://gitter.im/fomkin/korolev?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge) [![Join the chat at https://telegram.me/korolev_io](https://img.shields.io/badge/chat-on\ telegram\ \(russian\)-0088cc.svg)](https://telegram.me/korolev_io)
# Korolev

Not long ago we enter to the era of single-page applications. Some people say we no longer need a server. They say JavaScript applications can connect to DBMS directly. Fat clients. We are disagree with it. This project is an approach to solve problem of modern fat web.

Korolev runs single-page application on the server side, keeping in browser  only the bridge to receive commands and send events. The page loads instantly and works fast, cause doesn't produce almost no computations. It's important that Korolev makes single environment for full stack development. Client and server now is one app without REST protocol or something else.

The project supports static page rendering to allow search engines to index a pages and view pages immediately.

**Warning!** Korolev is under heavy development and not ready to use until 0.1.0 release. API is unstable.

## Principles

1. **Thin client.** Let's be fair, modern JavaScript application are too greedy. Any JavaScript developer thinks that his page is special. But user has different opinion. He opens dozens of tabs. Every tab contains a tons of code, and this works very slow. So we make our JavaScript bridge as lightweight as possible.

2. **Immutable and pure.** Really, we don't need mutability even in frontend. Especially in frontend. Most of modern JavaScript frameworks trying to be functional. And we are too.

3. **Rapid development.** Everything for efficiency. We trying to make Korolev bullshit and boilerplate free. Hope our words are not bullshit.

## Quick Start

Add `korolev-server` to your `build.sbt`

```scala
libraryDependencies += "com.github.fomkin" %% "korolev-server" % "0.0.6"
```

Look for [Example.scala](https://github.com/fomkin/korolev/blob/master/example/src/main/scala/Example.scala). It's updates very frequently, so we don't paste code here, sorry guys.

## Architecture

### Summary
Every *session* has the *State*. When *Event* happened you can modify *State* with *Transition*. Every state *renders* to pseudo-HTML. Browser receives only list of changes via WebSocket.

![Principle Diagram](principle-diagram.png)

### State

Single source of truth as [Redux guys say](http://redux.js.org/docs/introduction/ThreePrinciples.html#single-source-of-truth). The only one source of data to render a page. If you have something you want to display it should be stored in *State*. Usually it is a sealed trait with several case classes correspond to GUI screen. State should be immutable.

### Transition

A function that takes current *State* and transform it to new *State*. This is a only one way to modify a *State*.

### Events

You can subscribe to client-side DOM events. Event flow is similar to [standard](http://www.w3.org/TR/uievents/#event-flow).   Event handlers produce *Transitions*.

## Plans

All features and changes are registered in [issues](https://github.com/fomkin/korolev/issues) of this repository. First goal is to ship a ready to use version with semi-stable API.
