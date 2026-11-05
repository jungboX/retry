FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/mcp-server/pom.xml ./pom.xml
COPY services/mcp-server/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/mcp-server-0.1.0.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
