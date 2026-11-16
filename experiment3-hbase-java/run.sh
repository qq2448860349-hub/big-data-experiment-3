#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
root="$PWD"
source /home/vboxuser/experiment3-hbase/lab_env.sh
test -f "$HBASE_CONF_DIR/hbase-site.xml"
mkdir -p records
work="$(mktemp -d "$root/records/exp3_java_$(date +%Y%m%d%H%M%S)_XXXXXX")"
mkdir "$work/classes"
cp="$HBASE_CONF_DIR:$hbase/lib/*:$hbase/lib/client-facing-thirdparty/*"
echo "Run directory: $work"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -cp "$cp" \
    -d "$work/classes" "$root/src/HBaseLab.java" 2>&1 | tee "$work/compile.log"
echo 'Java compilation finished.'
timeout -k 5s 240s "$JAVA_HOME/bin/java" -Xmx512m -cp "$work/classes:$cp" \
    edu.course.bigdata.hbase.HBaseLab "$(basename "$work")" "$root/data" \
    2>&1 | tee "$work/hbase-java.log"
