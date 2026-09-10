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

## 验收检查

1. 秒杀完成后查询 MySQL，库存不能为负，同一用户最多一笔成功订单。
2. 对比 Redis 库存、MySQL 订单和 RocketMQ 消费结果，记录差异及补偿结果。
3. WebSocket 测试期间观察 `streamhub_realtime_broadcast_published_total`、`streamhub_realtime_broadcast_dropped_total` 和 HTTP p95。
4. 发生 MQ 重试、Redis 故障或应用重启时，单独记录恢复时间和最终一致性结果。

本次结果使用 Docker Desktop 中的 grafana/k6:0.53.0，秒杀通过 USER_IDS=1,2,3,4,5,6,7,8,9 访问 Live 内部端口 8083，WebSocket 直接访问 8083；业务 Token 鉴权已通过 Gateway 闭环单独验证。
