# Etapa 1 — build con Maven y JDK 25
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# Etapa 2 — runtime liviano con JRE 25
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/filgrama-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
