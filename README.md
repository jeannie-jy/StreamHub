# StreamHub

基于 Spring Boot 微服务架构的高并发直播互动与虚拟礼物平台。

项目计划见 [PLAN.md](PLAN.md)。当前已完成 Phase 0、Phase 1、Phase 2 和 Phase 3，正在进入微服务化与多节点实时网关建设。

## 本地启动

环境要求：Java 17+、Maven 3.9+、Docker Desktop。

```powershell
docker compose up -d
mvn clean verify
```

启动三个服务：

```powershell
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl live-service spring-boot:run
```

基础检查接口：

- `GET http://localhost:8081/api/v1/auth/ping`
- `GET http://localhost:8082/api/v1/users/ping`
- `GET http://localhost:8083/api/v1/live/ping`
- `GET http://localhost:8083/actuator/health`

默认端口和本地凭据可通过环境变量覆盖，示例见 [.env.example](.env.example)。

## Phase 1 MVP 示例

注册并登录：

```powershell
$body = @{ username = 'demo001'; nickname = 'Demo'; password = 'password123' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8081/api/v1/auth/register -Method Post -ContentType 'application/json' -Body $body
```

创建并开始直播间：

```powershell
$headers = @{ 'X-User-Id' = '1' }
$roomBody = @{ title = 'Demo Room'; category = 'tech' } | ConvertTo-Json
$room = Invoke-RestMethod http://localhost:8083/api/v1/live/rooms -Method Post -Headers $headers -ContentType 'application/json' -Body $roomBody
$roomId = $room.data.id
Invoke-RestMethod "http://localhost:8083/api/v1/live/rooms/$roomId/start" -Method Post -Headers $headers
```

开播响应会返回 SRS RTMP 推流地址和 HTTP-FLV 播放地址。当前 MVP 使用 `X-User-Id` 作为本地联调鉴权入口，注册/登录返回的 Token 已保存到 `auth_session`，统一 Token 校验将在后续网关化阶段接入。

WebSocket 弹幕地址：

```text
ws://localhost:8083/ws/chat?roomId={roomId}&userId={userId}
```

发送弹幕：

```json
{"type":"CHAT","clientMessageId":"client-001","content":"hello"}
```

发送心跳：

```json
{"type":"HEARTBEAT"}
```

## 礼物与钱包接口

查询礼物目录和余额：

```powershell
Invoke-RestMethod http://localhost:8083/api/v1/gifts
Invoke-RestMethod http://localhost:8083/api/v1/wallet -Headers @{ 'X-User-Id' = '2' }
```

模拟充值并送礼：

```powershell
$headers = @{ 'X-User-Id' = '2' }
$recharge = @{ bizNo = 'recharge-001'; amount = 1000 } | ConvertTo-Json
Invoke-RestMethod http://localhost:8083/api/v1/wallet/recharge -Method Post -Headers $headers -ContentType 'application/json' -Body $recharge

$gift = @{ giftCode = 'rose'; quantity = 1; clientOrderNo = 'gift-order-001' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8083/api/v1/live/rooms/1/gifts -Method Post -Headers $headers -ContentType 'application/json' -Body $gift
Invoke-RestMethod http://localhost:8083/api/v1/gift-orders/gift-order-001
```

订单先返回 `PENDING`，RocketMQ 消费成功后变为 `SUCCESS`；余额不足会变为 `FAILED`。重复使用同一个充值业务号或 `clientOrderNo` 会返回已有结果，不会重复扣款。贡献榜和主播收益榜分别通过 `/api/v1/live/rooms/{roomId}/gift-rank` 与 `/api/v1/live/rooms/{roomId}/gift-income-rank` 查询。

## 秒杀活动接口

活动使用 UTC 的 ISO-8601 时间，创建后由主播启动，启动时会把库存预热到 Redis：

```powershell
$headers = @{ 'X-User-Id' = '1' }
$start = [DateTime]::UtcNow.AddMinutes(-1).ToString('o')
$end = [DateTime]::UtcNow.AddHours(1).ToString('o')
$activity = @{ name = 'Flash Sale'; stock = 100; unitPrice = 0; startsAt = $start; endsAt = $end } | ConvertTo-Json
$created = Invoke-RestMethod http://localhost:8083/api/v1/live/rooms/1/activities -Method Post -Headers $headers -ContentType 'application/json' -Body $activity
$activityId = $created.data.id
Invoke-RestMethod "http://localhost:8083/api/v1/activities/$activityId/start" -Method Post -Headers $headers
```

用户抢购时，Redis Lua 脚本原子完成库存扣减和一人一次校验，RocketMQ 异步更新订单；重复请求由 Redis 和 MySQL 双重幂等保护：

```powershell
$order = @{ clientOrderNo = 'activity-order-001' } | ConvertTo-Json
Invoke-RestMethod "http://localhost:8083/api/v1/activities/$activityId/seckill" -Method Post -Headers @{ 'X-User-Id' = '2' } -ContentType 'application/json' -Body $order
Invoke-RestMethod http://localhost:8083/api/v1/activity-orders/activity-order-001
```
