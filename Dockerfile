# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/skillsync-ai-0.0.1-SNAPSHOT.jar app.jar

# Extract the Kafka SSL certificate from the build stage to the filesystem
COPY --from=build /app/src/main/resources/ca.pem /app/ca.pem
ENV KAFKA_TRUST_STORE_LOCATION=file:/app/ca.pem

# Expose the port the app runs on
EXPOSE 8080

# Run the application (with memory limits and Netty fixes for Alpine/Render)
ENTRYPOINT ["java", "-Xmx300m", "-Djava.net.preferIPv4Stack=true", "-Dio.netty.transport.noNative=true", "-jar", "app.jar"]
