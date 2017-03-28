#!/bin/bash

mkdir -p target
asciidoctor manual.adoc -o target/manual.html
asciidoctor-pdf manual.adoc -o target/manual.pdf
