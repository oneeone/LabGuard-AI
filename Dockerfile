# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/data /app/uploads

# pom.xml 版本变更时同步修改此处
COPY --from=build /workspace/target/labguard-ai-0.1.0-SNAPSHOT.jar /app/app.jar

ENV PORT=8080 \
    VISION_MODE=demo

EXPOSE 8080
VOLUME ["/app/data", "/app/uploads"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
