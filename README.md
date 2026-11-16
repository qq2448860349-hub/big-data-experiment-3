# 实验三 PageRank 算法与 HBase 操作

大数据管理与分析课程实验。实验环境为 Ubuntu 22.04、OpenJDK 8、Hadoop 3.2.2、Spark 3.5.8、HBase 2.5.16-hadoop3。

## 目录

- `experiment3`：Java MapReduce PageRank、编译脚本、小样例。
- `experiment3-spark`：PySpark PageRank、共享的 8 节点邻接表、YARN 提交与结果校验。
- `experiment3-mapreduce-format`：将相同邻接表转换成 MapReduce 初始状态，把真实结果格式化后与 Spark 逐行比较。
- `experiment3-hbase`：HBase 单机配置和实验 Shell 入口。
- `experiment3-hbase-java`：HBase Java API 源码、Student、Course、SC 输入数据和运行脚本。

## 运行说明

脚本按原实验环境编写，目录应分别放在 `/home/vboxuser` 下；若使用其他用户名或安装位置，先调整脚本与 HBase XML 中的路径。普通启动 HDFS 和 YARN 即可，不要重新格式化 NameNode。

1. 在 `experiment3` 下运行 `bash scripts/build.sh`，编译 Java PageRank。
2. 在 `experiment3-spark` 下运行 `bash scripts/run_spark.sh`，保留终端显示的 Spark `ranking.txt` 路径。
3. 在 `experiment3-mapreduce-format` 下运行 `bash run.sh "上一步的实际 ranking.txt 绝对路径"`。两种程序使用同一份 Tab 分隔的邻接表，各迭代 10 轮，输出 `(网页,PR值)` 并保留 10 位小数。
4. 在 `experiment3-hbase` 下运行 `bash start_and_check.sh`，然后用 `bash shell.sh` 进入交互式 Shell。另开普通终端，在 `experiment3-hbase-java` 下运行 `bash run.sh`，验证 Java API 并保留三张题目表。

HBase Shell 在独立练习表上验证建表、写入、查看、计数、列族增删与清空。Java API 运行在新建命名空间中，改删与清空操作只使用其中的 ApiDemo 练习表。Student 以姓名作行键，保留同学号的两条独立记录；Course 以课程号作行键，SC 以学号和课程号组合为行键。

## 实际结果

8 节点图在 MapReduce 与 Spark 各运行 10 轮，保留 10 位小数后，排名与得分逐行一致，首位为 `(C,0.3527225456)`。HBase Java API 校验通过，Student 3 行、Course 3 行、SC 6 行；ApiDemo 清空及重建后为 0 行。
