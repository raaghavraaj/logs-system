# Stage 1: Build the application using Maven
FROM maven:3.8.6-openjdk-17 AS build

WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the jar, skipping tests for faster builds
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the produced jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the internal port the app listens on (adjust if different)
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
