FROM eclipse-temurin:25-jre
RUN groupadd --system deviceops && useradd --system --gid deviceops --home-dir /app deviceops
WORKDIR /app
COPY device-ops-server/target/device-ops-server.jar /app/device-ops-server.jar
RUN mkdir -p /app/data && chown -R deviceops:deviceops /app
USER deviceops
EXPOSE 48081
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/device-ops-server.jar"]
