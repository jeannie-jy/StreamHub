# StreamHub 项目面试准备稿

依据：当前仓库代码、README、performance-baseline.md 和 fault-drills.md。个人职责按项目作者独立负责全栈开发描述。历史测试数字来自仓库已有记录，本次没有重新压测；不将本地演练描述为线上事故。技术方案比较是当前设计取舍分析，不代表曾经实际搭建并压测所有候选方案。

## 1. 业务背景：给谁用、解决什么问题、核心业务链路

**可直接口述：**

StreamHub 是我独立开发的直播互动与虚拟礼物平台，主要面向观众、主播和运营人员。观众可以浏览直播间、观看直播、发弹幕、关注主播、送礼和参与限量活动；主播可以创建和管理直播间、查看收益和互动情况；运营人员可以进行内容治理、处理异常消息和查询审计记录。

它解决的是直播场景中视频播放、实时互动和虚拟交易协同的问题。我的设计把音视频和业务互动分开：SRS 负责推流和播放，Java 服务负责身份、房间、弹幕、礼物、钱包及活动，避免视频流量直接占用业务服务资源。

从用户体验看，核心链路是“登录—进入直播间—观看与互动—送礼—查询订单与余额”；从技术实现看，我重点做的是送礼交易链路：接收送礼请求、可靠创建订单、异步扣减虚拟金币和增加主播收益，最后更新榜单并通知直播间。

**边界：** 当前应介绍为独立开发项目。仓库不能证明已有真实商业客户、线上日活或营收。钱包充值是项目内接口，未见接入第三方真实支付、提现或金融清结算。

## 2. 系统范围：模块、外部依赖、数据流向

| 模块 | 实际职责 |
| --- | --- |
| frontend | Vue 3、TypeScript、Pinia；登录、首页、直播间、主播中心、个人中心、运营后台；播放、重连、订单状态展示 |
| gateway-service | HTTP 入口、统一鉴权、路由、Redis 分布式限流、Resilience4j 熔断 |
| realtime-gateway | WebSocket 接入、握手鉴权与限流、转发到 Live |
| auth-service | 注册登录、Opaque Token 会话、刷新会话、一次性 WebSocket Ticket |
| user-service | 用户资料、状态、关注关系 |
| live-service | 直播间、弹幕、在线状态、礼物、钱包、榜单、活动、运营治理、Outbox 和对账 |
| 公共与基础设施模块 | 公共响应和异常、TraceId、共享网关能力、Flyway 迁移、Docker 编排、监控配置 |

**可直接口述：**

系统有 Auth、User、Live 三个业务服务，以及 HTTP 网关和实时网关。礼物、钱包、弹幕和活动目前都是 Live 内部的业务模块，没有分别拆成独立微服务。

外部基础设施主要依赖 MySQL、Redis、RocketMQ、Nacos 和 SRS。MySQL 保存用户、会话、房间、历史弹幕、订单、钱包流水、Outbox 和审计等持久数据；Redis 保存在线状态、活动预扣库存、榜单、限流状态和短期 Ticket，并通过 Pub/Sub 做跨节点广播；RocketMQ 处理异步订单、消费重试和延迟关闭；Nacos 提供服务发现和相关配置能力。Prometheus、Grafana 用于观测运行状态。

用户产生的业务数据通过网关进入服务，再按职责进入 MySQL、Redis 和 MQ；互动事件经 WebSocket 返回浏览器，订单可通过 HTTP 查询。音视频由 OBS 等推流端发送到 SRS，再由播放器通过 WebRTC 或 HTTP-FLV 拉取，不经过 Java 业务服务。CDN 是未来扩展方向，不能说当前已完成 CDN 生产部署。

## 3. 个人职责：全栈独立开发，参与到什么程度

**可直接口述：**

这个项目由我独立完成全栈开发，我负责从需求拆解、架构设计、数据库建模、接口设计到前后端实现和联调，也负责部署编排、监控配置、测试脚本与故障演练。

后端方面，我实现了认证与权限、用户和关注、直播间管理、WebSocket 弹幕与在线状态、虚拟礼物与钱包、活动秒杀和运营治理。重点设计了客户端业务号幂等、钱包行锁与唯一流水、订单和 Outbox 同事务、消息重试与死信处理，以及 Redis 派生数据对账。

前端方面，我完成了 Vue 3 页面、路由和状态管理、登录会话处理、直播播放器、WebSocket 连接与心跳重连、历史弹幕补偿，以及礼物、活动、订单和运营页面的交互。

工程方面，我配置了 Flyway、Docker Compose、Nginx、Prometheus 和 Grafana，编写或维护了后端测试、Redis 集成测试、前端单元测试、E2E 与 k6 脚本，并整理本地压测和故障恢复记录。

**追问“最能体现你个人贡献的部分”：** 重点讲送礼一致性、多节点在线状态和 Outbox 故障补发三个设计，能够继续解释代码、数据表、异常路径和验证方法。测试是否通过应以对应运行记录为准；拥有测试文件不等于当前所有测试均通过。

## 4. 核心链路：请求经过哪些服务、Redis、数据库和 MQ

### 4.1 送礼链路：建议作为主讲

```text
Vue 礼物面板
  → API Gateway
  → Auth 校验 Bearer Token（MySQL 会话）
  → Live 校验房间、礼物和 clientOrderNo
  → MySQL 同一事务：PENDING gift_order + mq_outbox
  → HTTP 返回当前订单状态（新订单通常为 PENDING）

Outbox 定时投递器
  → RocketMQ
  → 礼物消费者
  → MySQL 同一事务：锁订单、锁钱包、检查余额、更新双方余额、写唯一流水、更新订单
  → 提交后更新 Redis 榜单
  → Redis Pub/Sub → 各 Live 节点本地 WebSocket → 用户
```

**可直接口述：**

观众点击送礼后，前端携带 Token 和 clientOrderNo 请求 HTTP 网关。网关调用 Auth 校验身份，并覆盖客户端传入的身份字段，再转发到 Live。Live 校验房间和礼物，在同一个 MySQL 事务里创建待处理订单和 Outbox 事件，因此即使 MQ 暂时不可用，订单对应的待发送事件也不会漏记。

后台投递器把事件发送到 RocketMQ。消费者先锁定订单，只有 PENDING 才继续处理；随后按用户 ID 从小到大锁定双方钱包，降低交叉送礼时的死锁风险。余额检查、观众扣款、主播入账、唯一流水和订单成功状态都在同一个 MySQL 事务里完成。余额不足则把订单标记为失败。

事务提交后再更新 Redis 榜单并广播礼物事件。账务以 MySQL 为准，榜单可以从成功订单重建，实时动画不承诺故障期间可靠补发。重复请求和重复消费分别通过订单业务号、订单锁与状态检查、流水唯一约束控制。

**关键追问：**

- Outbox 发成功但未标记 SENT 就宕机：恢复后可能重发，所以消费者仍必须幂等。
- 返回 PENDING 不代表扣款成功：前端通过订单查询和事件通知获取后续状态。
- 为什么不把 Redis 更新放进账务事务：MySQL 本地事务无法回滚 Redis，派生状态采用重建与补偿。
- 异步的代价：引入积压、重试和运维成本；默认 Outbox 轮询周期为 500ms，请求响应延迟与订单完成延迟必须分别统计。

### 4.2 弹幕链路

前端先通过 HTTP 获取短时一次性 Ticket，再携 Ticket 连接 Realtime Gateway。网关调用 Auth 消费 Ticket，注入可信 userId，转发到 Live；代码也支持适用客户端通过 Bearer Header 握手。Live 校验房间与用户，注册本地连接，更新 Redis 在线状态。

发送弹幕时，Live 检查禁言、敏感词、内容和发送频率，并通过 User 服务校验用户状态，然后先写 MySQL，再发布 Redis Pub/Sub 房间事件。每个 Live 节点只向自身连接广播。重连后通过 HTTP 历史接口按 afterId 从 MySQL 补消息。这个链路不经过 RocketMQ。

在线状态用房间级用户 ZSet 和用户级连接 ZSet，区分同一个用户的多个标签页和节点连接；连接 member 带节点和启动标识。关闭一条连接时，只有没有其他有效连接才移除在线用户。客户端 20 秒心跳、45 秒无 ACK 重连；服务端 75 秒无心跳关闭，每 15 秒扫描；Redis 在线判定窗口默认 90 秒，相关 Key TTL 为 180 秒。

### 4.3 活动秒杀链路

网关鉴权 → Live 从 MySQL 校验活动状态、时间和已有订单 → Redis Lua 原子检查用户占位、扣库存、登记用户 → MySQL 同事务创建 PENDING 订单和 CREATE/CLOSE Outbox → 返回订单 → 后台投递 RocketMQ → CREATE 条件更新为 SUCCESS，延迟 CLOSE 只关闭仍为 PENDING 的订单并尝试回补库存。

数据库事务失败时，通过事务完成回调尝试回补 Redis 预占；极端异常还需对账修复。活动库存和用户集合使用相同 Redis Hash Tag，支持多 Key Lua 落在相同 Slot；这不代表当前部署了 Redis Cluster。

**必须讲清：** 当前秒杀在入口已经访问并写入 MySQL，MQ 主要异步化后续处理，不能说“所有请求只过 Redis、消费者才首次建单”。当前 CREATE 直接推进订单成功，未接入真实支付或钱包扣款，延迟 CLOSE 也不能描述为完整的支付超时取消链路。

## 5. 技术选型：理由、替代方案和代价

以下是基于当前实现的设计比较，只有确实做过的对比实验才适合说“我实测比较过”。

| 选型 | 为什么适合 | 可以比较的方案 | 代价与边界 |
| --- | --- | --- | --- |
| SRS 与 Java 业务分离 | 复用媒体协议和分发能力，让业务服务专注互动与交易 | 自研媒体服务、云直播 | 需运维媒体服务、处理公网地址和协议兼容；生产观看扩展仍需媒体容量与 CDN 验证 |
| HTTP 与实时网关分离 | 长连接和普通请求有不同的连接、超时与容量特点 | 共用网关 | 多一套部署和路由配置；实时网关代理连接，Live 仍持有业务 WebSocket 会话 |
| Redis Pub/Sub + MySQL 历史 | 在线房间广播轻量、延迟低，历史单独补偿 | Redis Streams、RocketMQ | Pub/Sub 无持久化和离线投递，断线补偿必不可少 |
| Redis Lua | 将库存判断、扣减和用户占位原子执行 | 多条 Redis 命令、数据库条件更新、分布式锁 | 脚本不能无限复杂；依赖 Redis；与 MySQL 仍有一致性窗口 |
| RocketMQ + Outbox | 支持削峰、重试和延迟处理；订单与待发送事件同事务落库 | 同步处理、直接发 MQ、RocketMQ 事务消息 | Outbox 扫表和投递延迟、重复发送、积压与死信治理；没有实现端到端 exactly-once |
| MySQL 行锁 + 唯一约束 | 余额、流水、订单可以在本地事务内共同提交 | 乐观锁、Redis 余额 | 热门主播钱包可能成为锁竞争热点，需要测量后再考虑分账或聚合方案 |
| Opaque Token + 一次性 Ticket | 服务端校验会话；Ticket 适配浏览器 WebSocket 的 Header 限制并缩短凭证复用窗口 | JWT、本身携长效 Token 的 URL | 每次鉴权依赖 Auth 和会话查询；Ticket 依赖 Redis |
| Gateway Redis 限流 + Resilience4j | 复用现有 Redis，分别处理限流与 HTTP 熔断 | Sentinel | 需要自己管理规则和告警；当前没有默认引入 Sentinel |
| Vue 3 + TypeScript + Pinia | 组件化、类型约束和集中状态便于独立全栈开发 | React 等 | 长连接生命周期、状态同步、播放协议回退仍需自行处理 |

**追问“独立开发为什么还用微服务”：** 我按身份、用户、直播业务及入口连接类型划分边界，主要用于实践分布式治理和故障处理。以当前实际规模，模块化单体也能满足需求；微服务增加了部署、网络调用、观测和一致性成本，需要结合目标选择。当前没有把 Live 内每个模块继续拆成服务。

## 6. 难点与故障：具体问题、定位、修复、验证

### 案例一：NameServer 可访问，生产者仍无法连接 Broker

这是仓库 2026-09-23 的本地演练记录，不是线上事故。

- 问题：Compose 中 Broker 向 NameServer 注册的是 127.0.0.1:10911。容器内生产者取得这个地址后，访问的是自身回环地址，无法连接真正的 Broker。
- 定位路径：区分“能否访问 NameServer”和“NameServer 返回的 Broker 是否可达”，结合生产者异常与集群路由检查，而不是只检查 9876 端口。
- 修复：brokerIP1 改为 Compose DNS 名 rocketmq-broker。
- 验证：记录确认路由为 rocketmq-broker:10911；恢复 Broker 后，测试 Outbox 经 9 次尝试变为 SENT，礼物订单最终 SUCCESS，观众余额 5→4、主播余额 998→999。
- 经验：服务发现返回的地址必须对实际调用方可达。若应用运行在宿主机，还需要解决宿主机对该 DNS 名的解析和连接问题。

### 案例二：MQ 中断时，如何避免订单存在而消息永久丢失

- 难点：数据库事务不能覆盖 MQ；直接“写订单再发消息”存在中间宕机窗口。
- 设计：订单和 Outbox 同事务提交；投递器条件竞争事件、失败退避重试，消费者通过订单状态和业务唯一键保持幂等。
- 演练：停止 Broker 后创建订单，核对订单和 Outbox 均为 PENDING；恢复 Broker 后检查 SENT、SUCCESS、双方余额和钱包流水。
- 已有记录：2026-09-23 的上述礼物订单自动补发成功；同日永久非法消息与 Broker DLQ 消息均持久化，运营忽略后 NEW→IGNORED，并记录审计。
- 边界：这是故障演练验证结果。没有证据就不讲成“线上发生资金事故后紧急修复”。

### 案例三：多节点在线人数与 Redis 派生数据恢复

在线人数的难点是用户不等于连接：同一用户可以在不同节点和标签页连接，关闭一条不应使用户离线。采用两层 ZSet、Lua、实例启动标识、心跳回收与 TTL 处理正常关闭和进程异常退出。仓库包含相关单元测试及真实 Redis 集成测试，当前面试稿未重新执行测试。

派生数据恢复有已记录演练：2026-09-10 将活动库存改成 999 并删除榜单，约 8 秒后对账恢复库存为 1、占位用户 9 人及贡献榜。演练把对账周期临时改为 5 秒，不能把 8 秒说成默认恢复 SLA。

**继续追问时的实际限制：** 对账任务的 Redis 锁只协调多个对账执行者，不会自动阻止正常下单。当前重建库存和用户集合包含多步操作，需要进一步验证与在线预扣并发时的竞争；因此现有 9 用户测试只证明该次场景结果，不能推导任意规模下绝不超卖。Redis 完全不可用的降级设计也应与已执行演练区分。

## 7. 项目指标：有哪些可靠记录，哪些还缺失

来源为 performance-baseline.md，测试版本 e084859，日期 2026-09-10；以下均是历史本地记录。

| 维度 | 已有记录 | 正确解释 |
| --- | --- | --- |
| 秒杀负载 | 20 VU，15 秒，300 次请求，约 19.7 req/s | 脚本每轮 sleep(1)，并非无节制加压，不能视为系统吞吐上限 |
| 秒杀响应 | p95 35.4ms | 直连 Live 内部端口，未包含完整网关鉴权；不能代表异步完成时间 |
| 秒杀正确性 | 初始库存 10，剩余 1，9 个成功用户 | 测试仅使用 9 个用户，覆盖有限的一人一单与库存核对 |
| 秒杀错误口径 | 自定义异常状态率 0%；k6 内置失败率 2% | 记录将后者归为业务拒绝；不可笼统说所有请求零失败 |
| WebSocket | 20 VU，15 秒，9593 次会话，28779 项检查通过 | 脚本收到心跳 ACK 即关闭，测的是反复握手和心跳，不是 9593 个同时在线长连接 |
| WebSocket 时延 | 会话 p95 49.79ms；连接 p95 24.53ms | 不能说成端到端弹幕投递 p95；测试直连 Live 内部端口 |
| 数据恢复 | Redis 派生数据约 8 秒修复 | 特定故障注入和 5 秒对账参数下的结果 |
| MQ 补发 | 9 次尝试后 SENT，订单 SUCCESS，双方余额正确变化 | 未记录完整恢复耗时，不能补写恢复 SLA |

**可直接口述：**

我目前有可重复脚本和本地验证记录。秒杀用 20 个虚拟用户运行 15 秒，共 300 次请求，约 19.7 req/s，接口 p95 为 35.4ms。9 个测试用户各形成一笔成功订单，库存从 10 降为 1。WebSocket 同样是 20 VU、15 秒，验证了连接、CONNECTED 和心跳 ACK，也记录了双节点广播验证。

这些成绩用于功能和一致性基线，尚不足以说明生产容量。秒杀脚本有限速，WebSocket 是短会话测试，并且两者直连 Live 内部端口。我不会据此宣称万级长连接、百万并发或生产级零错误率。

**仍需补充的指标：** 固定硬件和容器 CPU/内存、JVM 堆及 GC 参数；数据库数据量；完整网关鉴权下的阶梯压测；真实长连接在线数与消息扇出；端到端弹幕延迟；订单从受理到终态的延迟；CPU、堆、GC、数据库连接池与慢 SQL、MQ 积压及恢复耗时；优化前后相同条件下的对照结果。未有记录的指标不填估计数字。

## 一分钟介绍

StreamHub 是我独立完成全栈开发的直播互动与虚拟礼物项目，面向观众、主播和运营人员，覆盖观看、弹幕、关注、送礼、钱包和活动。我使用 Vue 3 与 TypeScript 开发前端，后端采用 Spring Boot，将 HTTP 网关和实时网关分开，SRS 负责媒体推流与播放。

我重点实现了三条链路：弹幕先落 MySQL，再通过 Redis Pub/Sub 做多节点广播，断线后按消息 ID 补偿；送礼使用订单与 Outbox 同事务、RocketMQ 异步处理、钱包行锁和唯一流水保证业务幂等；活动使用 Redis Lua 预扣库存、MySQL 待处理订单和异步状态流转。

除了功能开发，我也配置了部署、监控和测试，已有本地压测及 MQ 中断、派生数据损坏等演练记录。目前我能证明的是具体环境下的功能和一致性结果，生产容量和真实支付仍需要进一步验证与建设。

## 代码与记录索引

- 礼物交易：live-service/src/main/java/com/streamhub/live/GiftService.java、GiftOrderRepository.java、WalletRepository.java。
- 秒杀预扣与状态流转：live-service/src/main/java/com/streamhub/live/ActivityService.java、ActivityOrderRepository.java。
- 可靠投递：live-service/src/main/java/com/streamhub/live/MqOutboxRepository.java、MqOutboxDispatcher.java。
- 多节点弹幕与在线状态：live-service/src/main/java/com/streamhub/live/RoomWebSocketHandler.java、RoomBroadcastService.java、OnlinePresenceService.java。
- 鉴权：auth-service/src/main/java/com/streamhub/auth/AuthController.java；streamhub-gateway-support/src/main/java/com/streamhub/gateway/support/TokenAuthenticationFilter.java。
- 演练与指标：docs/performance-baseline.md、docs/fault-drills.md、load-tests/k6/seckill.js、load-tests/k6/websocket.js。
- 注意：旧 interview-guide.md 和 sequences.md 部分描述早于 Outbox 实现；涉及订单创建时机、MQ 投递、WebSocket Ticket 时，以当前代码核对后的口径为准。
