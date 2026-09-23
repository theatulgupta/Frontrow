FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 frontrow
WORKDIR /app
COPY --from=build /src/build/libs/frontrow-*.jar app.jar
USER frontrow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
