FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY vendor ./vendor
COPY src ./src
COPY examples ./examples
RUN mvn -B verify

FROM eclipse-temurin:21-jre
WORKDIR /data
COPY --from=build /src/target/marc2bf.jar /app/marc2bf.jar
ENTRYPOINT ["java", "-jar", "/app/marc2bf.jar"]
