# 生产化部署补强

## 多实例部署

基础 Compose 文件保留了本地调试端口。生产或本地多实例验证可使用 overlay：

```powershell
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d `
  --scale gateway-service=2 `
  --scale realtime-gateway=2 `
  --scale live-service=2
```

`docker-compose.ha.yml` 会移除业务服务的宿主机端口映射，让前端 Nginx 通过 Docker DNS 访问服务名。Nginx 已启用 Docker 内置 DNS 的运行时解析，服务扩容后新请求可以重新解析到可用实例；已有 WebSocket 连接仍固定在原实例，节点摘除时由客户端重连。

Live 节点必须使用不同的 `STREAMHUB_NODE_ID`。使用编排平台时应把 Pod/Task 名称注入该变量，不能让所有副本共享同一个节点 ID。

这只是应用层多实例配置，不会自动消除 MySQL、Redis、RocketMQ、Nacos 和 SRS 的单点问题。生产环境仍需要对应的集群或托管高可用方案。

## SkyWalking

镜像支持通过 `SKYWALKING_AGENT_PATH` 注入 Java Agent，不设置时保持原有启动方式：

```powershell
$env:SKYWALKING_AGENT_PATH='/opt/skywalking/skywalking-agent.jar'
docker compose up -d
```

Agent JAR 和 Collector 地址由部署环境提供。通常还需要设置：

- `SW_AGENT_NAME`：与服务名对应，例如 `streamhub-live`；
- `SW_AGENT_COLLECTOR_BACKEND_SERVICES`：SkyWalking OAP 地址；
- 为每个服务注入相同 Agent，但使用不同的服务名。

Prometheus/Grafana 仍保留，用于容量、连接池和业务指标；SkyWalking 用于跨 Gateway、Feign、数据库和 MQ 的调用链排障。

## 超时预算

建议保持调用链从外到内逐步变短：浏览器超时 > Nginx 读取超时 > Gateway 响应超时 > 服务间调用超时 > 数据库/Redis 操作超时。礼物、钱包和秒杀接口不要因为超时而自动重试，必须依靠业务幂等处理重复请求。
