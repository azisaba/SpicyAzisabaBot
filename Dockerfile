FROM eclipse-temurin:17.0.9_9-jre AS builder

COPY . .

RUN ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:17.0.9_9-jre AS runner

WORKDIR /app

COPY --from=builder build/libs/SpicyAzisabaBot.jar .

CMD ["java", "-jar", "SpicyAzisabaBot.jar"]
