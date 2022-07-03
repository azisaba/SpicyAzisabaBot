FROM openjdk:17

COPY . .

RUN ./gradlew --no-daemon shadowJar

CMD ["java", "-jar", "build/libs/SpicyAzisabaBot.jar"]
