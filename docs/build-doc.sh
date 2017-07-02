#!/bin/bash

mkdir -p target
asciidoctor -a stylesheet=theme.css manual.adoc -o target/manual.html
asciidoctor-pdf manual.adoc -o target/manual.pdf
