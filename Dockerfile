FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY streamhub-common/pom.xml streamhub-common/pom.xml
COPY database-migrations/pom.xml database-migrations/pom.xml
COPY streamhub-gateway-support/pom.xml streamhub-gateway-support/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY live-service/pom.xml live-service/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY realtime-gateway/pom.xml realtime-gateway/pom.xml

COPY streamhub-common/src streamhub-common/src
COPY database-migrations/src database-migrations/src
COPY streamhub-gateway-support/src streamhub-gateway-support/src
COPY auth-service/src auth-service/src
COPY user-service/src user-service/src
COPY live-service/src live-service/src
COPY gateway-service/src gateway-service/src
COPY realtime-gateway/src realtime-gateway/src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar app.jar

RUN addgroup -S streamhub && adduser -S streamhub -G streamhub
USER streamhub

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SKYWALKING_AGENT_PATH=""
ENTRYPOINT ["sh", "-c", "if [ -n \"$SKYWALKING_AGENT_PATH\" ]; then JAVA_OPTS=\"$JAVA_OPTS -javaagent:$SKYWALKING_AGENT_PATH\"; fi; exec java $JAVA_OPTS -jar /app/app.jar"]
