# StreamHub 压测基线

这份文档记录可重复的压测条件和结果。每次测试都应固定代码提交、依赖版本、Java 参数、压测机配置和 Docker 资源限制。

## 当前脚本

- 秒杀：`load-tests/k6/seckill.js`
- WebSocket：`load-tests/k6/websocket.js`
- 指标：Prometheus `http://localhost:9090`，Grafana `http://localhost:3000`

## 结果记录

| 日期 | Git commit | 场景 | VUS | 时长 | p95 | 错误率 | 业务成功数 | 备注 |
| --- | --- | --- | ---: | --- | ---: | ---: | ---: | --- |
| 2026-09-10 | e084859 | 秒杀（Live 内部端口） | 20 | 15s | 35.4 ms | 自定义异常状态率 0%；k6 内置失败率 2%（业务拒绝） | 9 | 300 次请求，约 19.7 req/s；库存 10→1；9 个成功用户 |
| 2026-09-10 | e084859 | WebSocket（Live 内部端口） | 20 | 15s | 49.79 ms | 0%（28779 项检查通过） | 9593 次会话 | ws_connecting p95 24.53 ms；CONNECTED 和 HEARTBEAT_ACK 均通过；另确认 8083/8093 跨节点广播 |

## 故障演练结果

| 日期 | Git commit | 场景 | 注入与恢复 | 结果 |
| --- | --- | --- | --- | --- |
| 2026-09-10 | a359c6b | Redis 派生数据损坏 | 将活动 5 库存改为 999、删除榜单，等待约 8s | 对账任务恢复库存 1、用户集合 9 人和贡献榜；日志记录 `repairedActivities=5/6` |
| 2026-09-11 | a359c6b | Live-B 节点摘除与重启 | 停止 8093，确认 A 节点 8083 健康后重启 B | A 节点持续返回 200，B 节点恢复 200，客户端可重新连接并用历史接口补偿 |
| 2026-09-11 | a359c6b | RocketMQ 中断后的 PENDING 重试 | 停止 broker，制造活动 6 的 PENDING 订单 `drill-mq-1789056048130`，再启动 broker | 订单最终为 SUCCESS；CREATE/CLOSE 出现重复投递，订单和库存处理保持幂等 |

## 验收检查

1. 秒杀完成后查询 MySQL，库存不能为负，同一用户最多一笔成功订单。
2. 对比 Redis 库存、MySQL 订单和 RocketMQ 消费结果，记录差异及补偿结果。
3. WebSocket 测试期间观察 `streamhub_realtime_broadcast_published_total`、`streamhub_realtime_broadcast_dropped_total` 和 HTTP p95。
4. 发生 MQ 重试、Redis 故障或应用重启时，单独记录恢复时间和最终一致性结果。

本次结果使用 Docker Desktop 中的 grafana/k6:0.53.0，秒杀通过 USER_IDS=1,2,3,4,5,6,7,8,9 访问 Live 内部端口 8083，WebSocket 直接访问 8083；业务 Token 鉴权已通过 Gateway 闭环单独验证。

故障演练使用双 Live 实例、Redis 7.4、RocketMQ 5.3.1 和自动对账任务临时参数 `fixed-delay-ms=5000`、`pending-age-ms=0`，以缩短本地验证等待时间。生产环境应恢复默认周期，并把演练中的订单号、指标和日志 traceId 保存到变更记录。
