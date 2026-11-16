#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/classes
classpath="$(hadoop classpath)"
javac -encoding UTF-8 -source 8 -target 8 -cp "$classpath" -d build/classes src/main/java/edu/course/bigdata/pagerank/PageRank.java
jar cf build/experiment3.jar -C build/classes .
echo "Build complete: $PWD/build/experiment3.jar"
