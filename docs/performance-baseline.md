# StreamHub 压测基线

这份文档记录可重复的压测条件和结果。每次测试都应固定代码提交、依赖版本、Java 参数、压测机配置和 Docker 资源限制。

## 当前脚本

- 秒杀：`load-tests/k6/seckill.js`
- WebSocket：`load-tests/k6/websocket.js`
- 指标：Prometheus `http://localhost:9090`，Grafana `http://localhost:3000`

## 结果记录

| 日期 | Git commit | 场景 | VUS | 时长 | p95 | 错误率 | 业务成功数 | 备注 |
| --- | --- | --- | ---: | --- | ---: | ---: | ---: | --- |
| 待执行 | - | 秒杀 | - | - | - | - | - | 依赖 Docker 引擎和活动数据 |
| 待执行 | - | WebSocket | - | - | - | - | - | 需要房间和测试用户 |

## 验收检查

1. 秒杀完成后查询 MySQL，库存不能为负，同一用户最多一笔成功订单。
2. 对比 Redis 库存、MySQL 订单和 RocketMQ 消费结果，记录差异及补偿结果。
3. WebSocket 测试期间观察 `streamhub_realtime_broadcast_published_total`、`streamhub_realtime_broadcast_dropped_total` 和 HTTP p95。
4. 发生 MQ 重试、Redis 故障或应用重启时，单独记录恢复时间和最终一致性结果。
