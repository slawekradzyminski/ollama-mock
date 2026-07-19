# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app
ARG REVISION

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "${REVISION}" ]; then set -- "-Drevision=${REVISION}"; else set --; fi && \
    ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true "$@" clean package

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN groupadd --system --gid 10001 ollama && \
    useradd --system --uid 10001 --gid ollama --no-create-home --shell /usr/sbin/nologin ollama
COPY --from=build --chown=10001:10001 /app/target/ollama-mock.jar ./ollama-mock.jar
USER 10001:10001
EXPOSE 11434
ENTRYPOINT ["java", "-jar", "ollama-mock.jar"]
