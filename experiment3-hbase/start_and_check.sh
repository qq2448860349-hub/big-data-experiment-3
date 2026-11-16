#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
source ./lab_env.sh
test -x "$hbase/bin/hbase"
test -x "$JAVA_HOME/bin/java"
mkdir -p "$HBASE_LOG_DIR"
if [[ ! -e "$HBASE_CONF_DIR" && ! -L "$HBASE_CONF_DIR" ]]; then
    cp -a "$hbase/conf" "$HBASE_CONF_DIR"
    cp "$lab/hbase-site.xml" "$HBASE_CONF_DIR/hbase-site.xml"
fi
if ! cmp -s "$lab/hbase-site.xml" "$HBASE_CONF_DIR/hbase-site.xml"; then
    echo "Unexpected HBase configuration; existing files were preserved." >&2
    exit 1
fi
echo "Using configuration: $HBASE_CONF_DIR"
if ! jps | awk '{print $2}' | grep -qx HMaster; then
    "$hbase/bin/start-hbase.sh" --config "$HBASE_CONF_DIR"
fi
echo "Checking HBase service; logs: $HBASE_LOG_DIR"
jps
printf "status 'simple'\n" | timeout 90 "$hbase/bin/hbase" --config "$HBASE_CONF_DIR" shell -n
echo 'HBase status command completed.'
