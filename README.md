# StreamHub

StreamHub 是一个面向高并发场景的直播互动与虚拟礼物平台，采用 Spring Boot 微服务架构实现用户、认证、直播间、弹幕、礼物、钱包和活动等业务能力。

项目的核心设计是将“音视频媒体面”和“业务互动面”拆开：SRS 负责推流与播放，Java 服务负责控制面、实时互动和交易业务。项目重点覆盖了多节点 WebSocket 广播、虚拟礼物幂等、秒杀削峰、故障补偿和可观测性等面试中常见的分布式系统问题。

入口层已经包含统一鉴权、CORS、Redis 分布式限流、HTTP 熔断回退、连接池和超时控制；链路追踪默认使用 `X-Trace-Id`，SkyWalking Java Agent 作为可选生产增强能力。
## 一、项目描述

### 1. 产品能力

- 游客浏览直播间、查看历史弹幕和礼物目录。
- 用户注册、登录、刷新会话、退出登录和个人资料维护。
- 主播创建、开始、结束直播，并管理房间活动。
- 用户发送弹幕、关注主播、收藏直播间、送虚拟礼物和查询订单。
- 支持贡献榜、主播收益榜、在线人数和运营治理能力。
- 支持秒杀活动：活动启动时预热库存，用户一人一单，订单异步处理并支持超时关闭。
- 播放器优先使用 WebRTC，浏览器不支持或信令失败时回退到 HTTP-FLV。

### 2. 系统架构

~~~text
                    ┌──────────────┐
       RTMP 推流 ──>│     SRS      │──> WebRTC / HTTP-FLV / HLS 播放
                    └──────────────┘

浏览器 HTTP ───────> API Gateway:8088 ───> Auth / User / Live
浏览器 WebSocket ──> Realtime Gateway:8090 ──> Live WebSocket
                                             │
                         ┌───────────────────┼───────────────────┐
                         │                   │                   │
                       MySQL              Redis             RocketMQ
                 业务事实、流水、历史    热点状态、库存、榜单    异步订单、削峰、重试
                                             │
                                      Redis Pub/Sub
                                      跨节点实时广播
~~~

API Gateway 负责 HTTP 路由、Token 鉴权、CORS、限流和熔断；Realtime Gateway 负责 WebSocket 握手鉴权与连接层治理。当前限流使用 Redis `RequestRateLimiter`，HTTP 下游使用 Resilience4j CircuitBreaker，未默认引入 Sentinel。

### 3. 服务职责

| 模块 | 默认端口 | 职责 |
| --- | ---: | --- |
| auth-service | 8081 | 注册、登录、Opaque Token 会话、Token 校验、WebSocket Ticket |
| user-service | 8082 | 用户资料、关注关系、用户状态 |
| live-service | 8083 | 直播间、弹幕、在线状态、礼物、钱包、活动和运营接口 |
| gateway-service | 8088 | HTTP 路由、服务发现、Token 鉴权、CORS、限流、熔断和负载均衡 |
| realtime-gateway | 8090 | WebSocket 长连接接入、Ticket 鉴权、握手限流和转发 |
| frontend | 8089 | Vue 单页应用、Nginx 反向代理和多实例服务发现 |
| SRS | 1935/1985/8080 | RTMP 推流、WebRTC/HTTP-FLV/HLS 播放和媒体管理 |

### 4. 项目结构

~~~text
StreamHub/
├── auth-service/              # 认证与会话
├── user-service/              # 用户与关注关系
├── live-service/              # 直播互动、礼物、钱包、活动
├── gateway-service/           # HTTP API Gateway
├── realtime-gateway/          # WebSocket Gateway
├── streamhub-common/          # 公共响应、异常、TraceId 等
├── streamhub-gateway-support/ # 网关鉴权、限流、CORS、TraceId 和客户端配置
├── database-migrations/       # Flyway 数据库迁移
├── frontend/                  # Vue 3 前端
├── infra/                     # RocketMQ、Prometheus、Grafana 配置
├── load-tests/                # k6 压测脚本
├── docker-compose.ha.yml      # 应用层多实例 Compose overlay
└── docs/                      # 架构、时序、一致性、生产化和面试资料
~~~
## 二、所用技术

| 技术方向 | 技术选型 | 使用场景 |
| --- | --- | --- |
| 后端基础 | Java 17、Spring Boot 3.4.5 | 微服务业务开发和运行 |
| 微服务治理 | Spring Cloud 2024.0.2、Spring Cloud Alibaba、Nacos 2.4.3、Spring Cloud LoadBalancer、Caffeine | 服务注册发现、配置管理、实例缓存和负载均衡 |
| 网关 | Spring Cloud Gateway、Redis RequestRateLimiter、Resilience4j | HTTP 路由、统一鉴权、限流、熔断和请求转发 |
| 服务间调用 | OpenFeign、Feign HC5、连接池和超时配置 | Live 服务校验用户、复用 HTTP 连接和服务间访问 |
| 实时通信 | Spring WebSocket、WebSocket Gateway | 弹幕、礼物和活动事件实时下发 |
| 数据库 | MySQL 8.4、Spring JDBC、HikariCP | 业务事实、订单、钱包流水和历史消息 |
| 数据库变更 | Flyway | 按版本执行数据库迁移，当前包含 V1 至 V6 |
| 缓存与高并发 | Redis 7.4、Redis Lua、ZSet、Pub/Sub | 在线状态、榜单、库存、限流和跨节点广播 |
| 异步消息 | Apache RocketMQ 5.3.1 | 礼物/活动订单异步处理、重试和延迟关闭 |
| 音视频 | SRS 6、RTMP、WebRTC、HTTP-FLV、HLS | 推流、播放和 WebRTC 信令 |
| 前端 | Vue 3、TypeScript、Vite 6、Vue Router、Pinia | 页面、路由和状态管理 |
| 前端组件与播放 | Naive UI、mpegts.js、VueUse、vue-i18n | UI、HTTP-FLV 播放、交互和多语言 |
| 工程化 | Docker、Docker Compose、Nginx | 一键编排、镜像构建和前端反向代理 |
| 监控与测试 | Actuator、Micrometer、Prometheus、Grafana、X-Trace-Id、可选 SkyWalking、JUnit、Vitest、Playwright、k6 | 指标、链路、面板、单元测试、E2E 和压测 |
## 三、亮点与难点

### 1. 音视频面与业务面分离

视频流量的主要压力在媒体服务器、CDN 和边缘节点，不应该让 Java 业务服务承担视频分发。项目让 SRS 处理 RTMP 推流和媒体播放，Java 服务只处理直播间控制、弹幕和交易业务；API Gateway 和 Realtime Gateway 也分别承载 HTTP 请求与 WebSocket 长连接，便于独立扩容和故障隔离。

面试时需要明确：项目中的“高并发”主要对应互动连接、业务请求和交易链路，不能直接把业务 QPS 等同于视频观看规模。

### 2. 多节点弹幕广播与断线补偿

WebSocket 连接只保存在当前 Live 节点的内存注册表中。弹幕处理流程如下：

1. 客户端发送弹幕后，Live 节点先写入 MySQL chat_message。
2. 数据落库成功后，通过 Redis Pub/Sub 发布包含 eventId 和 sourceNodeId 的房间事件。
3. 每个 Live 节点订阅事件，只向自己持有的 WebSocket 连接广播，因此可以横向扩容。
4. Redis Pub/Sub 只负责在线广播，不承担历史存储；客户端重连后通过 afterId 从 MySQL 补偿遗漏弹幕。

这样即使实时广播失败，已经落库的弹幕仍然可查询；当房间广播速率触顶时，也只是丢弃实时广播，不丢失历史记录。

### 3. 虚拟礼物的幂等与最终一致性

送礼接口不会同步完成扣款，而是先创建 PENDING 订单并投递 RocketMQ，消费者再执行扣款、收益入账、榜单更新和事件广播。

幂等边界分为三层：

- API 层通过客户端 clientOrderNo 防止重复创建订单。
- 钱包流水使用唯一业务号，重复消费时直接返回已有流水结果，避免重复扣款。
- MySQL 订单、钱包余额和收益流水构成可追溯的业务事实；Redis 榜单和 WebSocket 动画属于成功订单的派生结果，可重建。

面试回答重点是：RocketMQ 提供异步化、削峰和重试，但不会自动提供 Redis 与 MySQL 之间的分布式事务，因此必须依赖唯一约束、状态机和对账补偿。

### 4. 秒杀的原子预扣与异步削峰

秒杀请求在入口只做轻量鉴权，然后使用 Redis Lua 脚本在一次原子操作中完成：活动状态校验、库存扣减和一人一单校验。预扣成功后发送 CREATE 消息，接口立即返回 PENDING，由 RocketMQ 消费者异步创建 MySQL 订单。

订单流程还包含：

- MySQL 唯一索引防止重复消息导致重复订单。
- RocketMQ 延迟消息发送 CLOSE，关闭超时订单并回补库存。
- 定时对账任务比较 Redis 库存、用户占位和 MySQL 订单状态，修复异常数据。
- 对账任务使用 Redis 锁在多实例之间选出一个执行者，避免重复修复。

验证是否超卖时，不能只看接口返回值，还要同时核对 Redis 剩余库存、成功订单数量和成功用户集合，确保库存不为负且每个用户最多一笔成功订单。

### 5. Token 鉴权与 WebSocket Ticket

HTTP 写接口统一经过 API Gateway，并使用 Authorization: Bearer <accessToken>。Gateway 调用 Auth 服务进行 Token introspection 后，覆盖客户端传入的用户身份，避免信任外部伪造的 X-User-Id。

WebSocket 连接先通过 /api/v1/auth/ws-ticket 获取短时有效的一次性 Ticket，再连接 Realtime Gateway。Ticket 被消费后才把可信用户 ID 注入 Live 服务，解决长连接场景下 Token 传递、复用和身份伪造问题。

### 6. 可观测性与故障边界

所有服务通过 Actuator 暴露健康检查和 Prometheus 指标。Gateway 会生成或透传 `X-Trace-Id`，并将其传播到 WebClient、Feign 和业务服务，统一响应中携带 traceId，便于从网关日志追踪到业务服务。Live 服务额外记录实时广播发布数、丢弃数和本地降级数。

Prometheus/Grafana 默认启用，用于指标、容量和告警；SkyWalking Agent 需要额外提供 Agent JAR 和 OAP 地址，通过 `SKYWALKING_AGENT_PATH` 可选启用，详见[生产化部署补强](docs/production-hardening.md)。

项目对组件职责进行了明确划分：

| 组件 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| MySQL | 订单、钱包、流水、历史消息等最终业务事实 | 高并发实时广播 |
| Redis | 热点状态、库存、榜单、限流和在线广播 | 可靠历史消息和最终账务 |
| RocketMQ | 异步削峰、重试和延迟任务 | 自动解决跨存储分布式事务 |
| Redis Pub/Sub | 在线实时广播 | 断线消息可靠投递 |
| API Gateway | 鉴权、CORS、限流、熔断和入口超时 | 替代业务幂等、库存和账务一致性 |

常见面试追问：

| 追问 | 回答要点 |
| --- | --- |
| 为什么 WebSocket 不直接连接 Live 服务？ | 独立网关可以单独扩容连接层，集中处理鉴权和入口治理，业务节点只处理房间事件。 |
| Redis 挂了会怎样？ | 在线状态和实时广播受影响；已落 MySQL 的弹幕可通过历史接口补偿，秒杀预扣失败则返回错误。 |
| MQ 重复投递怎么办？ | 订单号、钱包流水业务号和 MySQL 唯一索引共同构成幂等边界。 |
| 如何防止秒杀超卖？ | Redis Lua 原子预扣负责入口并发控制，MySQL 唯一索引负责最终落库，后台对账负责修复异常。 |
| 如何定位慢请求？ | 用 traceId 关联网关与服务日志，结合 Actuator/Prometheus 的 p95、JVM、连接池、Redis、MQ 和慢 SQL 指标。 |
| 为什么没有默认引入 Sentinel？ | 当前 Redis 已承担分布式限流，Gateway 使用 Redis RequestRateLimiter 和 Resilience4j，避免同时维护两套路由规则；需要 Sentinel Dashboard、集群流控或 Sentinel 生态时再引入。 |
| 能否直接宣称百万并发？ | 不能。需要区分 CDN 观看规模、WebSocket 连接数、业务 QPS、实例数量和实际压测条件。 |
## 四、快速开始

### 1. 环境要求

- Docker Desktop，建议开启 Docker Compose。
- Java 17+
- Maven 3.9+（仅本地启动 Java 服务或执行 Maven 校验时需要）。
- Node.js 22+ 和 npm（仅前端本地开发时需要）。

### 2. 使用 Docker Compose 一键启动

在项目根目录执行：

~~~powershell
Copy-Item .env.example .env
docker compose up -d --build
~~~

Compose 会启动 MySQL、Redis、RocketMQ、Nacos、SRS、五个 Java 服务、前端、Prometheus 和 Grafana。首次启动需要构建镜像并下载 Maven/npm 依赖，耗时可能较长。

基础 Compose 适合单机开发和功能验证。需要验证应用层多实例时，使用 HA overlay：

~~~powershell
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d `
  --scale gateway-service=2 `
  --scale realtime-gateway=2 `
  --scale live-service=2
~~~

该 overlay 只移除业务服务的宿主机端口映射并启用服务发现，不会自动消除 MySQL、Redis、RocketMQ、Nacos 和 SRS 的单点问题；完整说明见[生产化部署补强](docs/production-hardening.md)。

检查容器状态：

~~~powershell
docker compose ps
~~~

查看业务服务日志：

~~~powershell
docker compose logs -f gateway-service live-service realtime-gateway
~~~

启动完成后访问：

| 地址 | 用途 |
| --- | --- |
| http://localhost:8089 | 前端页面 |
| http://localhost:8088 | API Gateway |
| http://localhost:8090 | Realtime Gateway，WebSocket 接入 |
| http://localhost:8848/nacos | Nacos 控制台 |
| http://localhost:9090 | Prometheus |
| http://localhost:3000 | Grafana，默认账号 admin，密码 streamhub-admin |
| localhost:13306 | MySQL 宿主机端口 |
| localhost:6379 | Redis |
| localhost:9876 | RocketMQ NameServer |
| localhost:1935 | SRS RTMP 推流端口 |
| localhost:1985 | SRS HTTP/WebRTC API |
| localhost:8080 | SRS HTTP-FLV/HLS |
| localhost:8000/udp | SRS WebRTC UDP |

端口和本地凭据可在 .env 中覆盖，完整示例见 [.env.example](.env.example)。数据库迁移会在业务服务启动时由 Flyway 自动执行。

如果前后端分域部署，需要将生产前端域名配置到 `STREAMHUB_ALLOWED_ORIGINS`；默认值仅包含本地 Vite 和前端 Nginx 地址。Gateway 的限流、超时、数据库连接池和 Redis 连接池参数也都可以通过 `.env` 覆盖。

### 3. 快速验证服务

~~~powershell
Invoke-RestMethod http://localhost:8088/api/v1/auth/ping
Invoke-RestMethod http://localhost:8088/api/v1/users/ping
Invoke-RestMethod http://localhost:8088/api/v1/live/ping
Invoke-RestMethod http://localhost:8088/api/v1/gifts
~~~

注册并登录一个测试用户：

~~~powershell
$body = @{ username = 'demo001'; nickname = 'Demo'; password = 'password123' } | ConvertTo-Json
$login = Invoke-RestMethod http://localhost:8088/api/v1/auth/register -Method Post -ContentType 'application/json' -Body $body
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
~~~

创建并开始直播间：

~~~powershell
$roomBody = @{ title = 'StreamHub Demo'; category = 'tech' } | ConvertTo-Json
$room = Invoke-RestMethod http://localhost:8088/api/v1/live/rooms -Method Post -Headers $headers -ContentType 'application/json' -Body $roomBody
$roomId = $room.data.id
Invoke-RestMethod "http://localhost:8088/api/v1/live/rooms/$roomId/start" -Method Post -Headers $headers
~~~

开播响应会返回推流地址和播放地址。可以使用 OBS 向返回的 pushUrl 推流，再通过前端页面观看；前端默认优先尝试 WebRTC，失败后回退到 HTTP-FLV。

### 4. 本地开发模式

如果需要调试 Java 或前端代码，可以只用 Docker 启动基础设施：

~~~powershell
docker compose up -d mysql redis rocketmq-namesrv rocketmq-broker nacos srs
~~~

在五个终端分别启动服务：

~~~powershell
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl live-service spring-boot:run
mvn -pl gateway-service spring-boot:run
mvn -pl realtime-gateway spring-boot:run
~~~

再启动前端：

~~~powershell
cd frontend
npm ci
npm run dev
~~~

前端开发地址默认为 http://localhost:5173，Vite 会将 /api、/ws、/live 和 /rtc 代理到本机的网关或 SRS 端口。可通过 VITE_DEV_API_TARGET、VITE_DEV_REALTIME_TARGET、VITE_DEV_MEDIA_TARGET 和 VITE_DEV_RTC_TARGET 覆盖代理地址。

开发环境通过 Vite 代理避免跨域；生产环境建议使用前端 Nginx 的同源 `/api` 和 `/ws` 入口，若必须跨域则配置 `STREAMHUB_ALLOWED_ORIGINS`，不要使用通配来源。

### 5. 测试与构建

前端检查：

~~~powershell
cd frontend
npm run typecheck
npm run lint
npm run test
npm run test:e2e
npm run build
~~~

后端和全工程校验：

~~~powershell
cd ..
mvn -B verify
~~~

### 6. 停止服务

~~~powershell
docker compose down
~~~

Compose 使用命名卷持久化 MySQL、Redis、RocketMQ、Nacos、MinIO、Prometheus 和 Grafana 数据。需要清理数据时再执行 docker compose down -v，该操作会删除这些命名卷中的本地数据。
## 五、接口与文档

- [完整 API 说明](docs/api.md)：认证、用户、直播间、弹幕、礼物、钱包和活动接口。
- [架构说明](docs/architecture.md)：服务职责、媒体边界、Redis Key 和监控指标。
- [关键时序图](docs/sequences.md)：弹幕、虚拟礼物和秒杀链路。
- [一致性与幂等](docs/consistency.md)：订单状态、库存、流水和补偿策略。
- [面试讲解提纲](docs/interview-guide.md)：一分钟介绍、核心亮点和常见追问。
- [故障演练](docs/fault-drills.md)：Redis、RocketMQ、Live 节点和对账任务的演练方式。
- [性能基线](docs/performance-baseline.md)：压测与故障演练结果记录。
- [生产化部署补强](docs/production-hardening.md)：多实例、超时预算、限流熔断和可选 SkyWalking 配置。
- [k6 压测说明](load-tests/README.md)：秒杀和 WebSocket 压测命令。

经过 API Gateway 的用户请求使用：

~~~text
Authorization: Bearer <accessToken>
~~~

WebSocket 客户端先调用 /api/v1/auth/ws-ticket 获取一次性 Ticket，再连接：

~~~text
ws://localhost:8090/ws/chat?roomId=<roomId>&ticket=<ticket>
~~~

断线重连后使用以下接口补偿历史弹幕：

~~~text
GET /api/v1/live/rooms/<roomId>/messages?afterId=<lastMessageId>&limit=100
~~~

直接访问 8081、8082、8083 主要用于本地排查；生产流量应统一经过网关，不应依赖客户端传入的 X-User-Id。
