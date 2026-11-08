# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy@sha256:e4f029273ed9e4444697df30bb6098dbe51477291598aebc54dd0b48d95d6ae7 AS build
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
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

FROM eclipse-temurin:25-jre-jammy@sha256:659b528cca74a0edc5c25315ec5819f35bce40bbcec645c4c534987e4c6be7c1
# OpenJDK CVE-2026-91403 mitigation; pinned artifact provenance is in security/java/.
COPY security/java/blocked.certs ${JAVA_HOME}/lib/security/blocked.certs
RUN echo "2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10  ${JAVA_HOME}/lib/security/blocked.certs" | sha256sum --check -
WORKDIR /app
RUN groupadd --system --gid 10001 ollama && \
    useradd --system --uid 10001 --gid ollama --no-create-home --shell /usr/sbin/nologin ollama
COPY --from=build --chown=10001:10001 /app/target/ollama-mock.jar ./ollama-mock.jar
USER 10001:10001
EXPOSE 11434
ENTRYPOINT ["java", "-jar", "ollama-mock.jar"]
