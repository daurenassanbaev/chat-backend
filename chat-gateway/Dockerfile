FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :chat-gateway:build -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/chat-gateway/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]