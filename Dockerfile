# Multi-stage Dockerfile: build with Maven, run with a small JRE image
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
# copy maven wrapper and pom first for better caching
COPY pom.xml mvnw* ./
COPY .mvn .mvn
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY --from=build /workspace/${JAR_FILE} app.jar
ENV JAVA_OPTS="-Xmx512m"
# Render provides $PORT; application.properties also reads server.port=${PORT:8080}
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
