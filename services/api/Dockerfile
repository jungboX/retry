FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/api/pom.xml ./pom.xml
COPY services/api/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/retry-api-0.1.0.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
