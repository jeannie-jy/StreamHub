# 数据一致性和幂等说明

## 一致性边界

MySQL 是业务事实来源，Redis 是热点状态和派生数据，RocketMQ 是异步处理和削峰通道，Redis Pub/Sub 是在线广播通道。四者承担的可靠性不同：

| 数据 | 来源 | 丢失后的处理 |
| --- | --- | --- |
| 钱包余额和流水 | MySQL | 以唯一业务号重放或人工对账 |
| 礼物订单 | MySQL + RocketMQ | 订单保持 PENDING，消费重试 |
| 活动订单 | MySQL + RocketMQ | CREATE 重试，失败时回补 Redis 库存 |
| 活动预扣库存 | Redis | 以订单状态和活动库存对账并回补 |
| 榜单 | Redis ZSet | 从成功订单重建 |
| 在线人数 | Redis ZSet | 心跳过期清理，客户端重连 |
| 实时弹幕 | Redis Pub/Sub | 从 MySQL afterId 补偿 |

## 礼物处理

1. API 层使用 clientOrderNo 查询重复订单；新请求只插入一条 PENDING 订单。
2. 消费者以订单号读取订单，钱包扣款和流水写入使用唯一 biz_no。
3. 已处理的订单直接返回已有结果，重复 MQ 投递不会再次扣款。
4. 扣款成功后写主播收益和 SUCCESS；失败则写 FAILED，余额不足不重试。
5. 成功订单更新 Redis 榜单并广播 GIFT。榜单属于派生数据，异常时可从成功订单重建。

## 活动订单处理

1. Lua 脚本在 Redis 中原子完成活动状态、库存和用户占位。
2. API 发送 CREATE|orderNo，消费者使用 MySQL 唯一键创建订单。
3. CREATE 消费失败返回 RocketMQ 重试；超过重试上限后，需要按订单和 Redis 预扣记录执行补偿。
4. 延迟消息发送 CLOSE|orderNo。订单仍为待处理或待支付时关闭并回补库存，已成功的订单不回补。
5. 对账时比较活动总库存、Redis 剩余库存、成功/关闭订单数量和异常占位用户。

当前实现已经提供重试、延迟关闭、回补和定时对账任务。对账任务会在多实例之间通过 Redis 锁选出一个执行者，重试过期 PENDING 订单、重建礼物榜并修复活动库存和用户占位。死信队列接入和人工补偿审批仍属于后续增强，演练步骤见 fault-drills.md。

## 弹幕补偿

弹幕先写 chat_message，再通过 Redis Pub/Sub 广播。Redis 故障时，广播服务记录 local fallback；客户端不会把 Pub/Sub 当作历史存储。客户端保存最后一个消息 ID，重连后请求：

~~~text
GET /api/v1/live/rooms/{roomId}/messages?afterId={lastMessageId}&limit=100
~~~

如果某个房间广播速率触顶，服务仍保存消息并丢弃本次实时广播，以便通过历史接口恢复。
