FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY library-service/pom.xml.build pom.xml
COPY shared-lib shared-lib
COPY library-service library-service

RUN mvn -pl library-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /workspace/library-service/target/library-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
