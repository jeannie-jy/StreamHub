# StreamHub 调优基线

本文档记录当前本地环境的可观测参数和检查命令。它用于复现和比较压测结果，不代表生产环境的最终参数。

## 当前运行条件

- Java：JDK 25 运行本地 Spring Boot JAR，编译目标仍为 Java 17。
- 依赖：MySQL 8.4、Redis 7.4、RocketMQ 5.3.1、Nacos 2.4.3。
- 压测：Docker 中的 `grafana/k6:0.53.0`，秒杀 20 VU/15s，WebSocket 20 VU/15s。
- Live 节点：8083 和 8093；网关：8088；实时网关：8090 和 8091。
- 本地对账演练把 `fixed-delay-ms` 调为 5000、`pending-age-ms` 调为 0；正常默认值分别为 60000 和 30000。

## 数据库和慢 SQL

核心幂等和查询索引由 Flyway 脚本维护。排查慢查询时先在 MySQL 中执行：

~~~sql
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';
SHOW INDEX FROM gift_order;
SHOW INDEX FROM activity_order;
SHOW INDEX FROM chat_message;
EXPLAIN SELECT order_no, user_id, status
FROM activity_order
WHERE activity_id = 6
ORDER BY id;
~~~

重点观察活动订单的 `(activity_id, user_id)` 唯一索引、订单号唯一索引、消息的 `(room_id, id)` 查询路径，以及榜单是否仍然只从 Redis 读取。慢 SQL 优化前后都应保存 `EXPLAIN` 输出和对应压测 p95。

## 连接池、线程池和 JVM

当前业务服务使用 Spring Boot 默认 Hikari 连接池，没有为了本地压测伪造一个生产容量值；可通过 Actuator、JMX 或启动日志记录实际连接池配置。调整时应同时观察 MySQL 活跃连接、请求 p95、线程池队列和 RocketMQ 消费延迟。

常用检查命令：

~~~powershell
jcmd <pid> VM.flags
jcmd <pid> GC.heap_info
jcmd <pid> Thread.print
Invoke-RestMethod http://localhost:8083/actuator/metrics/jvm.memory.used
Invoke-RestMethod http://localhost:8083/actuator/metrics/jvm.threads.live
Invoke-RestMethod http://localhost:8083/actuator/prometheus
~~~

本地启动时没有固定 `-Xms/-Xmx`，由 JVM 自动选择堆大小。生产压测应固定堆、记录 GC 日志，并把 Hikari 最大连接数、HTTP 工作线程、WebSocket 事件循环和 MQ 消费线程作为同一组参数调整。

## 当前判断

本地基线的主要瓶颈仍应通过 Prometheus 和压测结果确认，不能仅凭服务数量推断容量。已有结果表明秒杀请求 p95 为 35.4 ms、WebSocket 会话 p95 为 49.79 ms；这些数字只适用于当前 Docker Desktop 资源和单机依赖，扩展到更大规模前需要独立压测机、CDN/媒体服务器和明确的连接数边界。
