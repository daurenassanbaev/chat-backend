FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :chat-gateway:bootJar

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/chat-gateway/build/libs/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]