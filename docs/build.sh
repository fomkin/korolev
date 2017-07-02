#!/bin/bash

mkdir -p target
asciidoctor -a toc=left user-guide.adoc -o target/user-guide.html
asciidoctor-pdf user-guide.adoc -o target/user-guide.pdf
