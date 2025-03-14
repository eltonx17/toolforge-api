# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:23-jdk

# Set the working directory in the container
WORKDIR /app

# Copy the project’s jar file into the container at /app
COPY target/toolforge-api-0.0.1-SNAPSHOT.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]