# StreamHub 故障演练手册

本文档提供可重复的本地演练步骤。每次演练先记录 Git commit、Java 参数、Docker 资源、测试用户、房间、活动和开始时间，再执行故障注入。

## 公共准备

~~~powershell
docker compose up -d
docker compose ps

# 启动 Java 服务后检查
Invoke-RestMethod http://localhost:8088/api/v1/auth/ping
Invoke-RestMethod http://localhost:8088/api/v1/live/ping
Invoke-RestMethod http://localhost:8083/actuator/health
~~~

准备一名主播和多个测试用户，创建一个库存至少为 100 的活动，并保存：

- Git commit
- activityId、roomId
- 测试用户 ID 和 Token
- Redis 活动库存
- MySQL activity_order 行数
- RocketMQ 控制台中的消费位点

## MQ 重复投递和消费失败

1. 用同一个 clientOrderNo 连续调用秒杀或送礼接口。
2. 查询对应订单，确认只有一条订单记录，重复请求返回相同订单号。
3. 在 RocketMQ 消费期间停止 Live 服务，制造消费积压，再启动服务。
4. 观察订单从 PENDING 变为 SUCCESS 或符合业务规则的 FAILED。
5. 将同一消息重新投递或等待重试，确认钱包流水和订单没有重复写入。

记录：

~~~text
注入时间：
恢复时间：
订单最终状态：
重复消费次数：
钱包流水数量：
是否需要人工补偿：
~~~

## Redis 故障和恢复

1. 连接一个已建立的 WebSocket，发送一条弹幕并记录消息 ID。
2. 执行 docker compose stop redis。
3. 观察应用日志和 /actuator/metrics。弹幕历史应以 MySQL 为准；实时广播可能进入 local fallback。
4. 执行 docker compose start redis，等待连接恢复。
5. 重连 WebSocket，使用 afterId 拉取故障期间的消息，核对是否补偿完整。
6. 对活动抢购单独记录 Redis 错误响应；恢复后重试同一个 clientOrderNo，核对最终只存在一条业务订单。

## 服务重启和 WebSocket 节点摘除

1. 启动两个 Live 实例和两个 Realtime Gateway 实例，使用不同的 STREAMHUB_NODE_ID 和端口。
2. 在两个节点分别建立同一房间连接，互发 CHAT、GIFT 和 ACTIVITY_ORDER 事件。
3. 停止其中一个 Realtime Gateway，确认另一个节点仍能接受连接和广播。
4. 重启被停止的节点，客户端重连并按 afterId 补偿弹幕。
5. 对比 Prometheus 的 published、dropped 和 local fallback 指标。

## 对账 SQL

~~~sql
-- 活动订单状态和业务号
SELECT order_no, user_id, status, created_at, updated_at
FROM activity_order
WHERE activity_id = :activityId
ORDER BY id;

-- 礼物订单和钱包流水
SELECT order_no, sender_id, status, created_at, updated_at
FROM gift_order
WHERE room_id = :roomId
ORDER BY id;

SELECT biz_no, user_id, change_amount, entry_type, reference_id
FROM wallet_ledger
WHERE reference_id IN (:orderNos)
ORDER BY id;
~~~

## 演练结论模板

~~~text
场景：
代码版本：
依赖版本：
故障注入：
影响范围：
恢复动作：
恢复耗时：
最终一致性检查：
遗留问题：
~~~

真实压测和故障演练结果写入 performance-baseline.md。当前仓库只提交了脚本和记录模板，未伪造本机 Docker 引擎不可用时的结果。
