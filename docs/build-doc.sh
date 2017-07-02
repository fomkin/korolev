#!/bin/bash

mkdir -p target
asciidoctor -a toc=left manual.adoc -o target/manual.html
asciidoctor-pdf manual.adoc -o target/manual.pdf
