#!/usr/bin/env bash
set -euo pipefail
printf 'User: %s\n' "$(whoami)"
java -version
javac -version
python3 --version
hadoop version
processes="$(jps)"
printf '%s\n' "$processes"
for name in NameNode DataNode SecondaryNameNode ResourceManager NodeManager; do
    if ! awk '{print $2}' <<< "$processes" | grep -qx "$name"; then
        echo "Missing process: $name. Start HDFS/YARN; do not format NameNode." >&2
        exit 1
    fi
done
hdfs dfs -ls /user/vboxuser
echo 'Environment check passed.'
