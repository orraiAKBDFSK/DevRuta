# Etapa 1: compilar con el wrapper de Maven del proyecto (misma versión que en local y en CI)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh ./mvnw -q -B dependency:go-offline
COPY src src
RUN sh ./mvnw -q -B -DskipTests package

# Etapa 2: imagen mínima de ejecución, sin herramientas de compilación y sin root
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
