FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ledger && adduser -S ledger -G ledger
WORKDIR /app
COPY --from=build /workspace/target/ledger-*.jar app.jar
USER ledger

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
