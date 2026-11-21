#!/bin/bash
# Runs the number-matching tests on a PC with a plain JDK (no Android SDK needed).
set -e
cd "$(dirname "$0")"
rm -rf build/test && mkdir -p build/test
javac -encoding UTF-8 -d build/test src/com/smart/seriesblock/Series.java test/com/smart/seriesblock/SeriesTest.java
java -Dstdout.encoding=UTF-8 -cp build/test com.smart.seriesblock.SeriesTest
