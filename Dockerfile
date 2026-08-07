# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw
RUN ./mvnw package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/autonomous-ai-persona-1.0.0.jar app.jar
RUN mkdir -p /app/data
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]
