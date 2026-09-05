# Construcción (Build)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Ejecución (Run)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appuser && adduser -S -G appuser appuser
WORKDIR /app
COPY --from=build /app/target/flashcards-*.jar app.jar
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8080
USER appuser
ENTRYPOINT ["/entrypoint.sh"]
