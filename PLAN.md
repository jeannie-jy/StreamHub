# StreamHub 项目计划

## 1. 项目定位

StreamHub 是一个面向高并发场景的直播互动与虚拟礼物平台，重点展示直播间、弹幕、在线人数、虚拟礼物、排行榜、活动秒杀和后台运营能力。

项目目标不是复刻完整的抖音或斗鱼，而是构建一套边界清晰、可以本地运行、能够压测并解释关键设计取舍的工程项目。

简历定位建议：

> 设计面向百万级观看规模的直播互动与虚拟礼物平台，通过媒体服务器/CDN 承载音视频分发，Java 微服务负责直播间、互动、礼物和活动业务。

## 2. 目标与非目标

### 目标

- 完成主播创建直播间、OBS 推流、浏览器播放和观众互动的完整闭环。
- 支持弹幕、在线人数、关注、虚拟金币、送礼和贡献榜。
- 实现一个可压测的直播间秒杀或红包雨场景。
- 通过 Redis、RocketMQ、MySQL、WebSocket/Netty 解决缓存、削峰、广播、幂等和最终一致性问题。
- 使用 Docker Compose 一键启动本地依赖和服务。
- 提供架构图、时序图、接口文档、压测报告和故障补偿说明。

### 非目标

- 自研音视频编码、转码、CDN 或媒体服务器。
- 第一阶段实现真实支付、提现、实名认证和复杂风控。
- 一开始拆分大量微服务或同时引入多个 MQ。
- 宣称单台 Spring Boot 服务承载百万并发。

## 3. 技术决策

| 领域 | 选型 | 用途 |
| --- | --- | --- |
| 语言与基础框架 | Java 17、Spring Boot 3.x | 业务服务 |
| 微服务 | Spring Cloud Alibaba、Spring Cloud Gateway | 服务治理与统一入口 |
| 注册/配置 | Nacos | 服务发现、配置管理 |
| 数据库 | MySQL 8、MyBatis-Plus | 核心业务数据、消息历史、账户流水 |
| 缓存与分布式能力 | Redis 7、Lua、Redisson | 热点数据、在线状态、库存、限流、幂等、排行榜 |
| 消息队列 | RocketMQ | 异步解耦、顺序消息、延迟消息、重试与最终一致性 |
| 实时通信 | 第一阶段 Spring WebSocket；第二阶段 Netty 网关 | 弹幕、礼物动画、房间广播 |
| 媒体流 | SRS | RTMP 推流、HTTP-FLV/HLS/WebRTC 播放 |
| 文件存储 | MinIO | 头像、封面和回放文件 |
| 网关/部署 | Nginx、Docker Compose | 入口代理和本地编排 |
| 监控 | Prometheus、Grafana、Spring Boot Actuator | 指标、健康检查和运行观测 |
| 压测 | k6 或 JMeter | 接口、WebSocket、秒杀场景压测 |

只使用 RocketMQ 作为 MQ，避免重复引入 Kafka、RabbitMQ 和 RocketMQ。

## 4. 总体架构

```text
OBS/手机端
    │ RTMP/WebRTC
    ▼
SRS 媒体服务器
    │ HTTP-FLV/HLS/WebRTC
    ▼
浏览器/客户端

客户端 HTTP/WebSocket
    │
    ▼
Nginx
    ├── API Gateway
    │     ├── Auth/User Service
    │     ├── Live Room Service
    │     ├── Gift/Wallet Service
    │     ├── Activity/Seckill Service
    │     └── Statistics/Operation Service
    │
    └── WebSocket/Netty Gateway
          ├── Redis：实时状态、跨节点广播、限流
          ├── RocketMQ：可靠异步消息、顺序、重试、延迟
          └── MySQL：业务数据、消息历史、账户流水
```

视频分发和业务互动分开设计：SRS/CDN 负责音视频观看规模，Java 服务负责控制面和互动业务，WebSocket 网关负责长连接接入与房间广播。

## 5. 服务边界

第一阶段使用模块化单体，包结构提前按未来服务边界组织；第二阶段再拆分进程，避免在业务尚未稳定时增加部署和调试成本。

### Auth/User

- 注册、登录、刷新 Token。
- 用户资料、头像、关注关系。
- 主播身份和基础权限。

### Live Room

- 创建、编辑、开始、结束直播间。
- 房间状态、分类、封面和推流密钥管理。
- 生成播放地址，维护房间热度和基础统计。

### Chat/Realtime

- WebSocket 连接、鉴权、加入/离开房间。
- 弹幕发送、敏感词过滤、单用户/单房间限流。
- 消息 ACK、重连后的历史消息补偿。
- 多节点广播和房间分片预留。

### Gift/Wallet

- 虚拟金币余额、充值模拟、送礼订单和账户流水。
- 礼物幂等、扣款、主播收益、礼物动画事件。
- 贡献榜和主播收益榜。

### Activity/Seckill

- 活动、商品/奖品、库存和活动时间窗口。
- Redis Lua 原子扣减，RocketMQ 异步创建订单。
- 一人一单、重复消费、超时关闭、库存回补和对账。

### Statistics/Operation

- 在线人数、观看次数、礼物金额、订单和活动指标。
- 房间/用户/礼物的基础后台查询。
- 黑名单、敏感词和活动配置的运营入口。

## 6. 关键数据设计

### MySQL 核心表

- `user`、`user_follow`
- `live_room`、`live_session`
- `chat_message`
- `gift`、`wallet_account`、`wallet_ledger`
- `gift_order`
- `activity`、`activity_item`、`activity_order`
- `outbox_event` 或消费记录表，用于可靠投递和补偿

核心约束：

- 所有订单和账户流水使用全局唯一业务号。
- `wallet_ledger.biz_no` 唯一，保证重复消费不会重复扣款。
- `activity_order(activity_id, user_id)` 唯一，保证一人一单。
- 关键状态迁移使用状态条件更新，禁止无条件覆盖。
- 时间字段统一使用 UTC 存储，接口层按用户时区展示。

### Redis Key 约定

```text
live:room:{roomId}
live:room:{roomId}:online              # ZSet，member=userId，score=最后心跳时间
live:room:{roomId}:popularity
live:room:{roomId}:rank:contributors   # ZSet
live:room:{roomId}:rank:income         # ZSet
live:gift:nonce:{token}
activity:{activityId}:stock
activity:{activityId}:users
rate:ws:user:{userId}
rate:ws:room:{roomId}
```

Redis 只承担热点状态、原子校验和实时广播辅助职责。Redis Pub/Sub 不作为历史消息或账户数据的唯一存储，断线补偿从 MySQL 或可靠消息链路获取。

## 7. 核心业务流程

### 直播和观看

1. 主播创建直播间并获取推流地址。
2. OBS 向 SRS 推送 RTMP 流。
3. 服务端根据房间状态生成 HTTP-FLV/HLS/WebRTC 播放地址。
4. 观众进入房间，建立 WebSocket 连接并上报心跳。
5. 定时任务清理超过 TTL 的心跳，计算在线人数。

### 弹幕

1. 客户端通过 WebSocket 发送带客户端消息 ID 的弹幕。
2. 网关完成鉴权、敏感词过滤、限流和幂等校验。
3. 消息按 `roomId` 选择 RocketMQ 顺序队列进行削峰。
4. 消费者写入消息历史，并通过 Redis Pub/Sub 广播到各 WebSocket 节点。
5. 客户端 ACK；重连时按消息游标拉取缺失消息。

### 送礼

1. 客户端提交幂等 Token 和送礼请求。
2. Redis Lua 校验礼物、活动状态和请求频率。
3. RocketMQ 异步处理订单和账户流水。
4. 消费者以业务号执行幂等扣款、入账和收益更新。
5. 更新 Redis 榜单并广播礼物动画事件。
6. 失败消息进入重试/死信队列，补偿任务根据订单状态和流水对账。

### 秒杀/红包雨

1. 活动开始前将库存预热到 Redis。
2. Gateway 限流，Redis Lua 原子校验时间、资格、重复参与和库存。
3. 校验成功后写入 RocketMQ，快速返回排队结果。
4. 订单服务异步落库，使用唯一索引防止重复订单。
5. 延迟消息关闭超时订单并回补库存。
6. 定时对账 MySQL、Redis 和 MQ 结果，记录异常并支持人工补偿。

## 8. 分阶段开发计划

### Phase 0：工程基线

交付：

- Maven 多模块骨架和统一父 POM。
- 统一异常、响应体、日志、参数校验和 Trace ID。
- Docker Compose 启动 MySQL、Redis、RocketMQ、Nacos、SRS、MinIO。
- 数据库迁移脚本和本地配置模板。
- README、架构图、开发运行说明。

验收：新环境执行一条命令后，依赖服务健康检查通过，应用可以启动并连接所有依赖。

### Phase 1：模块化单体 MVP

交付：

- 登录注册、用户资料和关注主播。
- 创建/开始/结束直播间。
- OBS 推流到 SRS，浏览器播放。
- 单节点 WebSocket 弹幕。
- 在线人数心跳和基础房间页。

验收：主播可以推流，观众可以观看、加入房间、发送弹幕并看到在线人数变化；服务重启后核心数据仍可恢复。

### Phase 2：礼物和实时互动

交付：

- 虚拟金币和模拟充值。
- 礼物目录、送礼订单、钱包流水。
- Redis 榜单和礼物动画广播。
- 弹幕持久化、ACK、断线重连补偿。
- 统一幂等和消费记录机制。

验收：重复提交和重复消费不会重复扣款；重连后可以补齐指定游标之后的历史消息；礼物榜与流水可对账。

### Phase 3：高并发活动

交付：

- 秒杀或红包雨活动配置。
- Redis Lua 防超卖和一人一单。
- RocketMQ 削峰、重试、死信和延迟关闭。
- 订单状态机、库存回补和定时对账。
- 限流、降级和活动监控指标。

验收：压测下库存不为负、同一用户最多成功一次、重复消息不产生重复订单；异常消费可重试并能通过补偿恢复。

### Phase 4：微服务化和多节点实时网关

交付：

- Gateway、Auth/User、Live Room、Chat、Gift/Wallet、Activity 服务拆分。
- Nacos 服务发现和配置中心。
- OpenFeign 服务调用和超时/重试策略。
- WebSocket/Netty Gateway 独立部署。
- Redis Pub/Sub 跨节点广播，热门房间分片和广播保护。

验收：单个业务服务可独立部署；关闭一个 WebSocket 节点不影响其他节点；跨节点房间消息可达，广播延迟和丢失情况可观测。

### Phase 5：工程化、压测和故障演练

交付：

- Prometheus/Grafana 仪表盘。
- JMeter/k6 脚本和基线压测报告。
- 慢 SQL、索引、连接池、线程池和 JVM 参数记录。
- MQ 重复投递、消费失败、Redis 故障、服务重启等演练记录。
- API 文档、时序图、数据一致性说明和面试讲解文档。

验收：每个关键场景都有可重复的压测命令、指标结果和故障恢复步骤。

## 9. 第一批实现任务

按以下顺序开始编码：

1. 建立 Maven 多模块工程、Java 17 和 Spring Boot 3.x 基线。
2. 添加 `common`、`auth-service`、`user-service`、`live-service` 的初始模块。
3. 编写 Docker Compose 和本地环境配置。
4. 建立用户、直播间、直播场次的数据库表及迁移脚本。
5. 实现登录、创建直播间、开始/结束直播间接口。
6. 接入 SRS，返回推流地址和播放地址。
7. 实现第一版 Spring WebSocket 房间连接、弹幕和心跳。
8. 补齐单元测试、接口测试和 README 的本地运行步骤。

第一批任务完成后再拆分 Chat、Gift 和 Activity 服务，避免基础领域模型未稳定就进入分布式故障排查。

## 10. 质量和验证要求

- 所有核心写接口具备参数校验、鉴权、幂等策略和错误码。
- 关键状态变化写审计日志，日志包含 traceId、userId、roomId 和业务号。
- 数据库迁移脚本可重复执行或明确版本顺序，禁止手工改表作为唯一流程。
- 先验证业务正确性，再验证并发性能；压测结果必须记录测试条件、并发模型和瓶颈。
- 对礼物扣款、活动订单、库存回补、消息补偿至少编写有业务价值的集成测试。
- 任何“百万级”描述都必须注明观看规模、视频分发链路、互动连接数和业务请求量的具体边界。

## 11. 当前状态

- [x] 项目方向和技术路线确定
- [x] 项目计划建立
- [x] Phase 0：工程基线
- [x] Phase 1：模块化单体 MVP
- [x] Phase 2：礼物和实时互动
- [x] Phase 3：高并发活动
- [ ] Phase 4：微服务化和多节点实时网关
- [ ] Phase 5：工程化、压测和故障演练

Phase 0 已完成：工程骨架、公共组件、本地依赖编排、数据库基线和基础启动检查均已验证。

Phase 1 已完成：认证注册/登录、用户资料和关注、直播间生命周期、SRS 地址生成、单节点 WebSocket 弹幕、Redis 在线人数和弹幕历史补偿接口均已实现，并完成注册、登录、关注、建房、开播、弹幕、历史查询和关播闭环验证。

Phase 2 已完成：虚拟金币充值、礼物目录、RocketMQ 异步送礼订单、赠送方扣款、主播收益入账、唯一业务号幂等、余额不足失败、Redis 贡献榜/收益榜和 WebSocket 礼物事件均已实现，并完成重复充值、重复订单、异步成功、余额不足及实时事件验证。

Phase 3 已完成：活动创建/启动、Redis Lua 库存与一人一单原子预扣、RocketMQ 异步订单、延迟关闭消息、MySQL 唯一约束、库存回补和活动订单事件均已实现，并完成双库存抢购、重复参与、库存耗尽、积压消息重启恢复和新消息即时消费验证。

Phase 4 已开始：新增独立 API Gateway，接入 Spring Cloud LoadBalancer、Nacos 服务发现和 Nacos Config；认证、用户、直播、礼物、钱包和活动 HTTP 接口以及 WebSocket 入口均可通过 `8088` 访问，Gateway 会通过 Auth 服务校验 Bearer Token 并注入可信用户身份；直播服务已抽出房间会话注册表，并用 Redis Pub/Sub 完成两实例跨节点弹幕广播验证。下一步拆出实时网关并补齐 WebSocket 鉴权、广播保护和服务间调用。
