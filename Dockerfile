# Use an official OpenJDK runtime as a parent image
FROM openjdk:24-oraclelinux8

# Set the working directory in the container
WORKDIR /app

# Copy the executable jar file to the container
COPY bedreflyt-api.jar /app/bedreflyt-api.jar

# Copy the external jar file to the container
COPY bedreflyt.jar /app/bedreflyt.jar

# Copy the smol file
COPY src/main/resources/SMOL /app/SMOL

# Expose the port that the application will run on
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app/bedreflyt-api.jar"]