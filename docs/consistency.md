# 数据一致性和幂等说明

## 一致性边界

MySQL 是业务事实来源，Redis 是热点状态和派生数据，RocketMQ 是异步处理和削峰通道，Redis Pub/Sub 是在线广播通道。四者承担的可靠性不同：

| 数据 | 来源 | 丢失后的处理 |
| --- | --- | --- |
| 钱包余额和流水 | MySQL | 以唯一业务号重放或人工对账 |
| 礼物订单 | MySQL + Outbox + RocketMQ | 订单与 Outbox 同事务提交，投递和消费均可重试 |
| 活动订单 | MySQL + Outbox + RocketMQ | CREATE/CLOSE 可靠投递，失败时回补 Redis 库存 |
| 活动预扣库存 | Redis | 以订单状态和活动库存对账并回补 |
| 榜单 | Redis ZSet | 从成功订单重建 |
| 在线人数 | Redis 两层 ZSet | 连接心跳过期清理，Redis 故障时退化为本节点去重用户数 |
| 实时弹幕 | Redis Pub/Sub | 从 MySQL afterId 补偿 |

## 礼物处理

1. API 层使用 clientOrderNo 查询重复订单；新请求在同一个 MySQL 事务中插入 PENDING 订单和 Outbox 事件。
2. 消费者以订单号读取订单，钱包扣款和流水写入使用唯一 biz_no。
3. 已处理的订单直接返回已有结果，重复 MQ 投递不会再次扣款。
4. 扣款成功后写主播收益和 SUCCESS；失败则写 FAILED，余额不足不重试。
5. 成功订单更新 Redis 榜单并广播 GIFT。榜单属于派生数据，异常时可从成功订单重建。

## 活动订单处理

1. Lua 脚本在 Redis 中原子完成活动状态、库存和用户占位。
2. API 在订单事务中写入 CREATE 与延迟 CLOSE Outbox 事件，后台投递器再发送 RocketMQ；消费者使用 MySQL 唯一键和条件状态更新保持幂等。
3. CREATE 消费失败返回 RocketMQ 重试；超过重试上限后，需要按订单和 Redis 预扣记录执行补偿。
4. 延迟消息发送 CLOSE|orderNo。订单仍为待处理或待支付时关闭并回补库存，已成功的订单不回补。
5. 对账时比较活动总库存、Redis 剩余库存、成功/关闭订单数量和异常占位用户。

当前实现已经提供事务 Outbox、生产重试、消费重试、延迟关闭、死信持久化、人工重放/忽略和定时对账。Outbox 使用事件唯一键避免重复创建，多实例投递器通过条件更新竞争事件；发送结果不确定时允许重复投递，由订单状态和业务唯一键消除副作用。消费重试耗尽后，RocketMQ 死信由独立消费者写入 `mq_dead_letter`，运营操作与审计日志在同一事务中提交。

Prometheus 暴露 Consumer Group lag、Outbox 待投递数/失败数/最老年龄、待处理死信数、消费结果和观察到的重试次数。告警规则位于 `infra/prometheus/alerts.yml`；生产环境还需在 Prometheus/Alertmanager 中配置实际通知接收器。

活动库存和用户集合使用 `activity:{activityId}:stock` 与 `activity:{activityId}:users`。花括号是实际 Redis Hash Tag，保证多 Key Lua 在 Redis Cluster 下位于同一个 Slot。Redis 内部原子性不能替代 Redis 与 MySQL 之间的对账补偿。

## 在线状态

1. 房间级 `live:room:{roomId}:online` ZSet 以 userId 为 member，统计去重在线用户。
2. 用户级 `live:room:{roomId}:user:<userId>:connections` ZSet 以 `nodeId:bootId:connectionId` 为 member，跟踪多节点、多标签页连接。
3. 心跳 Lua 原子刷新连接和房间用户时间戳，并为两个 Key 刷新过期时间。
4. 离线 Lua 只删除当前连接；该用户没有其他有效连接时，才从房间在线 ZSet 删除。
5. 客户端、服务端和 Redis 分别以 45 秒、75 秒和 90 秒检测失联，避免 TCP 半开连接长期制造假在线。
6. Redis 不可用时，Live 节点仍保留本地连接并按 userId 去重计数；此时只能得到本节点人数，Redis 恢复后由后续心跳重建全局状态。

## 弹幕补偿

弹幕先写 chat_message，再通过 Redis Pub/Sub 广播。Redis 故障时，广播服务记录 local fallback；客户端不会把 Pub/Sub 当作历史存储。客户端保存最后一个消息 ID，重连后请求：

~~~text
GET /api/v1/live/rooms/{roomId}/messages?afterId={lastMessageId}&limit=100
~~~

如果某个房间广播速率触顶，服务仍保存消息并丢弃本次实时广播，以便通过历史接口恢复。
