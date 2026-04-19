FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY order-service/pom.xml.build pom.xml
COPY shared-lib shared-lib
COPY order-service order-service

RUN mvn -pl order-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /workspace/order-service/target/order-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
