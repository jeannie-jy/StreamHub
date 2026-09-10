# StreamHub 时序图

## WebSocket 弹幕和跨节点广播

~~~mermaid
sequenceDiagram
    participant C1 as 客户端 A
    participant G1 as Realtime Gateway A
    participant L1 as Live 节点 A
    participant DB as MySQL
    participant R as Redis Pub/Sub
    participant L2 as Live 节点 B
    participant G2 as Realtime Gateway B
    participant C2 as 客户端 B

    C1->>G1: WebSocket /ws/chat
    G1->>Auth: introspect Bearer Token
    Auth-->>G1: userId
    G1->>L1: 代理连接并覆盖 userId
    L1-->>C1: CONNECTED
    C1->>L1: CHAT(clientMessageId, content)
    L1->>DB: 保存 chat_message
    L1->>R: 发布 RoomEvent
    R-->>L1: 本节点监听回调
    R-->>L2: 其他节点监听回调
    L1-->>G1: 广播 CHAT
    L2-->>G2: 广播 CHAT
    G1-->>C1: CHAT
    G2-->>C2: CHAT
    C2->>DB: 断线重连后按 afterId 补偿
~~~

普通弹幕先落 MySQL，再尝试实时广播。单用户 300ms 发送保护和房间秒级广播保护只影响实时广播压力，历史记录仍可补偿。

## 虚拟礼物

~~~mermaid
sequenceDiagram
    participant C as 客户端
    participant API as API Gateway
    participant L as Live
    participant MQ as RocketMQ
    participant DB as MySQL
    participant R as Redis

    C->>API: POST /live/rooms/{roomId}/gifts
    API->>Auth: introspect Token
    API->>L: 可信 userId + clientOrderNo
    L->>DB: 插入 PENDING gift_order
    L->>MQ: 发送 orderNo
    L-->>C: PENDING
    MQ->>L: 消费订单
    L->>DB: 唯一流水扣款
    L->>DB: 写主播收益和 SUCCESS
    L->>R: 更新贡献榜和收益榜
    L-->>C: WebSocket GIFT
~~~

同一个订单号重复消费时，订单状态和钱包流水唯一键使处理变成幂等操作。

## 活动秒杀

~~~mermaid
sequenceDiagram
    participant C as 客户端
    participant API as API Gateway
    participant L as Live
    participant R as Redis
    participant MQ as RocketMQ
    participant DB as MySQL

    C->>API: POST /activities/{id}/seckill
    API->>L: 可信 userId + clientOrderNo
    L->>R: Lua 校验状态、抢库存、一人一单
    alt 库存和资格通过
        R-->>L: remaining stock
        L->>MQ: CREATE|orderNo
        L-->>C: PENDING
        MQ->>L: 创建订单
        L->>DB: 唯一索引插入 activity_order
        L->>MQ: 延迟 CLOSE|orderNo
    else 被拒绝
        R-->>L: SOLD_OUT 或 DUPLICATE
        L-->>C: 业务错误
    end
    MQ->>L: CLOSE|orderNo
    L->>DB: 超时订单取消
    L->>R: 回补未支付库存
~~~
