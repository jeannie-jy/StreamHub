# StreamHub

基于 Spring Boot 微服务架构的高并发直播互动与虚拟礼物平台。

项目计划见 [PLAN.md](PLAN.md)。当前处于 Phase 0：工程基线搭建。

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
