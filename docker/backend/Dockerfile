# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml LICENSE lombok.config ./
COPY yudao-dependencies ./yudao-dependencies
COPY yudao-framework ./yudao-framework
COPY yudao-module-system ./yudao-module-system
COPY yudao-module-infra ./yudao-module-infra
COPY yudao-module-bpm ./yudao-module-bpm
COPY pms-module-project ./pms-module-project
COPY pms-module-engineering ./pms-module-engineering
COPY pms-module-cutover ./pms-module-cutover
COPY pms-module-service ./pms-module-service
COPY pms-module-asset ./pms-module-asset
COPY pms-module-outsourcing ./pms-module-outsourcing
COPY pms-module-analytics ./pms-module-analytics
COPY pms-module-integration ./pms-module-integration
COPY yudao-server ./yudao-server
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl yudao-server -am package -DskipTests

FROM maven:3.9.11-eclipse-temurin-25

WORKDIR /opt/pms
COPY --from=build /workspace/yudao-server/target/yudao-server.jar ./app.jar
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"
EXPOSE 48080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
