FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/*.jar app.jar

# Output directory for connector files (CSV, JSON)
RUN mkdir -p /app/output

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]


