# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Копируем pom и скачиваем зависимости (кэширование слоя)
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем исходники и собираем
COPY src ./src
RUN mvn package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/reports-api-*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]