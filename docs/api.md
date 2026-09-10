# StreamHub API 说明

## 统一约定

HTTP 响应统一为：

~~~json
{
  "success": true,
  "data": {},
  "code": "OK",
  "message": "success",
  "traceId": "a1b2c3",
  "timestamp": "2026-09-10T15:00:00Z"
}
~~~

写接口和用户私有数据通过 API Gateway 的 8088 访问。需要登录的请求携带：

~~~text
Authorization: Bearer <accessToken>
~~~

网关会调用 Auth 服务校验 Token，并把可信用户写入 X-User-Id。直接访问业务服务排查问题时，可以使用 X-User-Id，这个方式不应暴露到公网。

失败响应使用 COMMON-400、COMMON-401、COMMON-403、COMMON-404 和 COMMON-500。所有响应都带 traceId，日志检索时使用同一个值。

## Auth 服务

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/auth/ping | 否 | 存活检查 |
| POST | /api/v1/auth/register | 否 | 注册，用户名 3 到 64 个字符，密码至少 8 个字符 |
| POST | /api/v1/auth/login | 否 | 登录并创建 7 天会话 |
| POST | /api/v1/auth/introspect | Bearer | 为网关校验 Token |

注册和登录的成功数据包含 userId、accessToken 和 expiresAt。

## 用户服务

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/users/ping | 否 | 存活检查 |
| GET | /api/v1/users/{userId} | 否 | 查询用户资料 |
| POST | /api/v1/users/{anchorId}/follow | 是 | 关注主播 |
| DELETE | /api/v1/users/{anchorId}/follow | 是 | 取消关注 |
| GET | /api/v1/users/{anchorId}/follow-status | 是 | 查询关注状态 |

## 直播间和弹幕

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/live/ping | 否 | 存活检查 |
| POST | /api/v1/live/rooms | 是 | 创建直播间 |
| GET | /api/v1/live/rooms/{roomId} | 否 | 查询直播间 |
| POST | /api/v1/live/rooms/{roomId}/start | 是 | 开始直播并返回 SRS 地址 |
| POST | /api/v1/live/rooms/{roomId}/end | 是 | 结束直播 |
| GET | /api/v1/live/rooms/{roomId}/messages?afterId=0&limit=50 | 否 | 拉取历史弹幕，limit 最大 100 |

创建直播间请求：

~~~json
{
  "title": "StreamHub Demo",
  "category": "tech"
}
~~~

直播间开始后，响应中的 pushUrl 用于 OBS 推流，flvPlayUrl 用于 HTTP-FLV 播放。

实时网关默认地址为 ws://localhost:8090/ws/chat?roomId={roomId}&userId={userId}。连接成功收到 CONNECTED，客户端定期发送：

~~~json
{"type":"HEARTBEAT"}
~~~

服务端返回 HEARTBEAT_ACK。弹幕请求示例：

~~~json
{"type":"CHAT","clientMessageId":"client-001","content":"hello"}
~~~

成功广播的事件类型为 CHAT、GIFT 和 ACTIVITY_ORDER。clientMessageId 用于房间内弹幕幂等，历史接口用于断线后的补偿拉取。

## 礼物和钱包

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| GET | /api/v1/gifts | 否 | 礼物目录 |
| GET | /api/v1/wallet | 是 | 查询虚拟金币余额 |
| POST | /api/v1/wallet/recharge | 是 | 幂等充值 |
| POST | /api/v1/live/rooms/{roomId}/gifts | 是 | 创建异步送礼订单 |
| GET | /api/v1/gift-orders/{orderNo} | 是 | 查询送礼订单 |
| GET | /api/v1/live/rooms/{roomId}/gift-rank?limit=10 | 否 | 贡献榜 |
| GET | /api/v1/live/rooms/{roomId}/gift-income-rank?limit=10 | 否 | 主播收益榜 |

送礼请求：

~~~json
{
  "giftCode": "rose",
  "quantity": 1,
  "clientOrderNo": "gift-order-001"
}
~~~

接口先返回 PENDING。RocketMQ 消费完成后变为 SUCCESS 或 FAILED。充值 bizNo、送礼 clientOrderNo 和钱包流水 bizNo 都有唯一约束。

## 活动和秒杀

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| POST | /api/v1/live/rooms/{roomId}/activities | 是 | 创建活动 |
| POST | /api/v1/activities/{activityId}/start | 是 | 启动活动并预热 Redis 库存 |
| GET | /api/v1/activities/{activityId} | 否 | 查询活动 |
| POST | /api/v1/activities/{activityId}/seckill | 是 | 一人一单抢购 |
| GET | /api/v1/activity-orders/{orderNo} | 是 | 查询活动订单 |

活动时间使用 UTC ISO-8601。抢购请求：

~~~json
{"clientOrderNo":"activity-order-001"}
~~~

Redis Lua 脚本同时完成活动状态、库存和一人一单校验；RocketMQ 异步创建订单，延迟消息关闭超时订单。MySQL 唯一索引是最终幂等边界。
