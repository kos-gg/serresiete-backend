# Build stage
FROM gradle:7-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar --no-daemon

# Final image stage
FROM openjdk:17.0.2-jdk-slim-bullseye
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/
USER 1001
ENTRYPOINT ["java", "-jar", "/app/raiderio-ladder-backend-all.jar"]