# Stage 1: build
# NOTE: this Dockerfile lives at the repo ROOT but builds the BACKEND (Spring Boot)
# which lives in backend/. We deliberately point COPY at backend/ paths because
# Railway (and most CI platforms) look for Dockerfile at the repo root.
# For the FRONTEND, use a separate Railway service with custom dockerfilePath
# pointing to frontend/Dockerfile (see RUNBOOK.md for the deploy step).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -DskipTests clean package

# Stage 2: runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
