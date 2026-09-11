# StreamHub

基于 Spring Boot 微服务架构的高并发直播互动与虚拟礼物平台。

项目计划见 [PLAN.md](PLAN.md)。当前已完成 Phase 0、Phase 1、Phase 2 和 Phase 3，Phase 4 已完成主要代码交付，Phase 5 已加入监控、压测脚本和工程化文档，前端产品化页面与运营治理接口已接入。

## 本地启动

环境要求：Java 17+、Maven 3.9+、Docker Desktop。

```powershell
docker compose up -d
mvn clean verify
```

`docker compose up -d` 会同时启动基础设施和 Web 容器，Web 默认入口为 `http://localhost:8089`。当前 Java 微服务仍可按下方命令直接运行，Web 容器通过 `host.docker.internal` 代理本机的 API Gateway、Realtime Gateway 和 SRS；部署到同一容器网络时只需把 `frontend/nginx.conf` 中的 upstream 替换为对应服务名。

前端开发和检查：

```powershell
cd frontend
npm ci
npm run dev
npm run typecheck
npm run lint
npm test
npm run test:e2e
npm run build
```

启动五个服务：

```powershell
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl live-service spring-boot:run
mvn -pl gateway-service spring-boot:run
mvn -pl realtime-gateway spring-boot:run
```

网关默认监听 `8088`，通过 Nacos 发现三个业务服务。统一入口示例：

```powershell
Invoke-RestMethod http://localhost:8088/api/v1/auth/ping
Invoke-RestMethod http://localhost:8088/api/v1/users/ping
Invoke-RestMethod http://localhost:8088/api/v1/live/ping
Invoke-RestMethod http://localhost:8088/api/v1/gifts
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
$registered = Invoke-RestMethod http://localhost:8088/api/v1/auth/register -Method Post -ContentType 'application/json' -Body $body
$token = $registered.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
```

创建并开始直播间：

```powershell
$roomBody = @{ title = 'Demo Room'; category = 'tech' } | ConvertTo-Json
$room = Invoke-RestMethod http://localhost:8088/api/v1/live/rooms -Method Post -Headers $headers -ContentType 'application/json' -Body $roomBody
$roomId = $room.data.id
Invoke-RestMethod "http://localhost:8088/api/v1/live/rooms/$roomId/start" -Method Post -Headers $headers
```

开播响应会返回 SRS RTMP 推流地址、WebRTC 播放地址和 HTTP-FLV 播放地址。前端优先使用 WebRTC，浏览器不支持或信令失败时自动切换 HTTP-FLV。经 Gateway 访问写接口时使用 `Authorization: Bearer {accessToken}`；Gateway 调用 Auth 服务校验 Token 后覆盖传入的 `X-User-Id` 和 `X-User-Role`。直接访问业务服务进行本地排查时仍支持 `X-User-Id`。

直播服务创建房间时会通过 OpenFeign 调用 User 服务校验主播用户，服务发现由 Nacos 提供，连接超时和读取超时可通过 `USER_SERVICE_CONNECT_TIMEOUT_MS` 与 `USER_SERVICE_READ_TIMEOUT_MS` 调整。

WebSocket 弹幕地址（独立实时网关）：

```text
ws://localhost:8090/ws/chat?roomId={roomId}&ticket={one-time-ticket}
```

连接 Realtime Gateway 时由前端先调用 `/api/v1/auth/ws-ticket` 取得 30 秒有效的一次性 Ticket；Gateway 消费 Ticket 后才把可信用户 ID 注入下游。直接访问直播服务进行排查时，也可以使用 `ws://localhost:8083/ws/chat`。API Gateway `8088` 专注 HTTP，Realtime Gateway `8090` 专注 WebSocket 长连接。Nacos 地址、配置中心开关和配置分组见 [.env.example](.env.example) 中的 `NACOS_SERVER_ADDR`、`NACOS_DISCOVERY_ENABLED`、`NACOS_CONFIG_ENABLED` 与 `NACOS_CONFIG_GROUP`。

直播服务实例使用 Redis Pub/Sub 的 `STREAMHUB_REALTIME_CHANNEL` 广播聊天、礼物和活动事件；每个实例只向自己持有的 WebSocket 连接发送消息。通过 `STREAMHUB_NODE_ID` 设置实例标识，便于日志和多节点排查。单实例单房间连接数和普通弹幕广播速率分别由 `STREAMHUB_MAX_SESSIONS_PER_ROOM` 与 `STREAMHUB_MAX_CHAT_EVENTS_PER_SECOND` 限制，触发速率保护的弹幕仍保存在 MySQL，可通过历史接口补偿；广播计数可从 `/actuator/metrics` 观察。

监控和压测：Prometheus 默认端口为 `9090`，Grafana 默认端口为 `3000`，面板配置见 `infra/grafana`；k6 命令和结果记录模板见 [load-tests/README.md](load-tests/README.md) 与 [docs/performance-baseline.md](docs/performance-baseline.md)。

架构、API、时序图、数据一致性、故障演练和面试讲解见 [docs](docs/)。

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
