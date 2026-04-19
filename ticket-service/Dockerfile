FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY ticket-service/pom.xml.build pom.xml
COPY shared-lib shared-lib
COPY ticket-service ticket-service

RUN mvn -pl ticket-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /workspace/ticket-service/target/ticket-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
