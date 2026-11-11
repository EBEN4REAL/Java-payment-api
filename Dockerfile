# syntax=docker/dockerfile:1

# ---- Build: compile and package the jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# The real application.properties holds local secrets and is excluded by .dockerignore.
# The image gets the committed template; secrets come from environment variables at runtime.
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties
# Tests need MySQL, Redis, Kafka and the bank stub, so they run in CI, not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# Split the jar into layers: dependencies change rarely, application classes often.
RUN java -Djarmode=tools -jar target/payment-api-*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime: JRE only, non-root ----
FROM eclipse-temurin:25-jre
# Fixed IDs so Kubernetes can enforce runAsUser/runAsNonRoot.
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER 10001:10001

# 8080: API. 8081: Actuator (health probes, metrics), keep it internal.
EXPOSE 8080 8081

# Size the heap from the container's memory limit, and exit on OutOfMemoryError so the
# orchestrator restarts the container instead of leaving it half-broken.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
