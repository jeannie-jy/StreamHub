# StreamHub 压测脚本

脚本使用 [k6](https://k6.io/)；压测前先启动 Docker 依赖和五个应用，并准备一个处于 ACTIVE 状态的活动。

秒杀接口脚本：

```powershell
k6 run load-tests/k6/seckill.js `
  -e BASE_URL=http://localhost:8088 `
  -e ACTIVITY_ID=1 `
  -e ACCESS_TOKEN=$token `
  -e VUS=100 `
  -e DURATION=60s
```

WebSocket 连接脚本：

```powershell
k6 run load-tests/k6/websocket.js `
  -e WS_URL=ws://localhost:8090 `
  -e ROOM_ID=1 `
  -e ACCESS_TOKEN=$token `
  -e VUS=100 `
  -e DURATION=60s
```

记录结果时同时保存 k6 输出和 Prometheus 指标，至少记录测试时间、VUS、持续时间、请求成功率、p95 延迟、WebSocket 握手成功率、广播丢弃计数和数据库/Redis/MQ 资源占用。脚本中的 400、409、429 是业务拒绝或保护结果，不能单独当作系统错误；压测报告需要另外统计业务成功数、库存剩余值和订单最终状态。
