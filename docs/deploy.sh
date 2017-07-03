#!/bin/bash
set -e
./build.sh
scp target/* fomkin.org:/var/www/html/korolev/
