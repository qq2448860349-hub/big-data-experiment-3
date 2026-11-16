#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
source ./lab_env.sh
test -f "$HBASE_CONF_DIR/hbase-site.xml"
exec "$hbase/bin/hbase" --config "$HBASE_CONF_DIR" shell "$@"
