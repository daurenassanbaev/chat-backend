FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :delivery-service:build -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/delivery-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]