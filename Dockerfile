FROM openjdk:17-jdk-slim

COPY /target/*.jar saga-product-validation-service-app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "saga-product-validation-service-app.jar"]