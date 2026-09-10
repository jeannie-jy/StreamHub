# StreamHub 架构说明

## 运行边界

视频流和业务互动分开承载：

~~~text
OBS/手机客户端
    │ RTMP/WebRTC
    ▼
SRS
    │ HTTP-FLV/HLS/WebRTC
    ▼
播放器或 CDN

浏览器 HTTP ──> API Gateway:8088 ──> Auth/User/Live
浏览器 WebSocket ──> Realtime Gateway:8090 ──> Live WebSocket
                                      │
                                      ├─ Redis Pub/Sub：跨节点实时广播
                                      ├─ Redis：在线状态、榜单、库存和限流
                                      ├─ RocketMQ：礼物和活动异步处理
                                      └─ MySQL：业务事实、流水和历史消息
~~~

Java 服务只负责控制面、互动消息和交易业务。观看规模的扩展依赖媒体服务器、CDN 和边缘节点，不能用业务服务的 QPS 直接代表视频观看规模。

## 服务职责

| 服务 | 默认端口 | 主要职责 |
| --- | ---: | --- |
| Auth | 8081 | 注册、登录、Opaque Token 会话和 Token introspection |
| User | 8082 | 用户资料、关注关系 |
| Live | 8083 | 直播间、弹幕、在线人数、礼物、钱包和活动 |
| API Gateway | 8088 | HTTP 路由、服务发现、统一 Token 鉴权 |
| Realtime Gateway | 8090 | WebSocket 长连接代理和 Token 鉴权 |
| SRS | 1935/1985/8080 | RTMP 推流、HTTP-FLV/HLS 播放和管理 |

目前 Live 服务包含 Chat、Gift/Wallet 和 Activity 领域代码，网关已经按边界路由，后续可以在不改变外部 API 的情况下继续拆分。

## 关键 Redis Key

| Key | 类型 | 用途 |
| --- | --- | --- |
| live:room:{roomId}:online | ZSet | 用户心跳时间和在线人数 |
| live:room:{roomId}:gift:contributors | ZSet | 房间贡献榜 |
| live:room:{roomId}:gift:income | ZSet | 主播收益榜 |
| live:activity:{activityId}:stock | String | 活动库存 |
| live:activity:{activityId}:users | Set | 一人一单校验 |
| live:room:{roomId}:broadcast:rate | String | 普通弹幕秒级广播保护 |
| configured channel | Pub/Sub | Chat、Gift、Activity 实时事件 |

Redis Pub/Sub 只负责在线广播。断线客户端必须用 MySQL 历史弹幕接口补偿，不能把 Pub/Sub 当作唯一消息存储。

## 观测指标

所有服务暴露 /actuator/prometheus。Live 服务额外记录：

- streamhub_realtime_broadcast_published_total
- streamhub_realtime_broadcast_dropped_total，标签 type=chat
- streamhub_realtime_broadcast_local_fallback_total

Prometheus 和 Grafana 的本地编排位于 docker-compose.yml，配置位于 infra/prometheus 和 infra/grafana。
