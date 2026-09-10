# StreamHub

基于 Spring Boot 微服务架构的高并发直播互动与虚拟礼物平台。

项目计划见 [PLAN.md](PLAN.md)。当前已完成 Phase 0 和 Phase 1，正在进入礼物与实时互动能力建设。

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
