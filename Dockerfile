FROM eclipse-temurin:25-jdk-jammy as build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN ./mvnw -B -Dmaven.test.skip=true clean package spring-boot:repackage

FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
COPY --from=build /app/target/ollama-mock-0.0.1-SNAPSHOT.jar ./ollama-mock.jar
EXPOSE 11434
ENTRYPOINT ["java", "-jar", "ollama-mock.jar"]
