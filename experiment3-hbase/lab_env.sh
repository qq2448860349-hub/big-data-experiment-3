#!/usr/bin/env bash
lab=/home/vboxuser/experiment3-hbase
hbase=/home/vboxuser/hbase-2.5.16
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export HBASE_CONF_DIR="$lab/runtime-conf"
export HBASE_LOG_DIR="$lab/records"
export HBASE_PID_DIR="$lab/records"
export HBASE_IDENT_STRING=experiment3
export HBASE_DISABLE_HADOOP_CLASSPATH_LOOKUP=true
export HBASE_HEAPSIZE=768m
unset HADOOP_IN_PATH
