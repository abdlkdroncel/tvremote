FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

RUN chmod +x mvnw \
    && ./mvnw -q -B -DskipTests package \
    && cp target/tvremote-*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN mkdir -p /root/.android

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080
VOLUME ["/root/.android"]

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
