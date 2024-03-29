# Korolev

<img src="https://fomkin.org/korolev/korolev-face-margin.svg" align="right" width="260" />

[![Build Status](https://travis-ci.org/fomkin/korolev.svg?branch=master)](https://travis-ci.org/fomkin/korolev)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Ffomkin%2Fkorolev.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Ffomkin%2Fkorolev?ref=badge_shield)
[![Gitter](https://badges.gitter.im/fomkin/korolev.svg)](https://gitter.im/fomkin/korolev?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Join the chat at https://telegram.me/korolev_io](https://img.shields.io/badge/chat-on_telegram_(russian)-0088cc.svg)](https://telegram.me/korolev_io)

Not long ago we have entered the era of single-page applications. Some people say that we no longer need a server. They say that JavaScript applications can connect to DBMS directly. Fat clients. **We disagree with this.** This project is an attempt to solve the problems of modern fat web.

Korolev runs a single-page application on the server side, keeping in the browser only a bridge to receive commands and send events. The page loads instantly and works fast, because it does a minimal amount of computation. It's important that Korolev provides a unified environment for full stack development. Client and server are now combined into a single app without any REST protocol or something else in the middle.

## Why?

* Lightning-fast page loading speed (~6kB of uncompressed JS)
* Comparable to static HTML client-side RAM consumption
* Indexable pages out of the box
* Routing out of the box
* Build extremely large app without increasing size of the page
* No need to make CRUD REST service
* Connect to infrastructure (DBMS, Message queue) directly from application

## Examples

* [Features](https://github.com/fomkin/korolev/tree/master/examples)
* [Multiplayer match-three game build on Korolev](https://match3.fomkin.org/)
* Goldbricker - Encrypted ToDo List (coming at summer 2020)
 
## Documentation

* [User guide (open site)](https://fomkin.org/korolev/user-guide.html), [(download PDF)](https://fomkin.org/korolev/user-guide.pdf)
* [API overview](https://www.javadoc.io/doc/org.fomkin/korolev_2.13/1.1.0) 

## Articles

* [Slimming pill for Web](https://dev.to/fomkin/korolev-slimming-pill-for-web-549a)
* [Лекарство для веба](https://habr.com/ru/post/429028/)

## Tools

* [HTML to Levsha DSL converter](https://fomkin.org/korolev/html-to-levsha)

[comment]: <> ([![Browser support results]&#40;https://fomkin.org/korolev/browser-support.svg&#41;]&#40;https://saucelabs.com/u/yelbota&#41;)

## License

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Ffomkin%2Fkorolev.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Ffomkin%2Fkorolev?ref=badge_large)
