FROM eclipse-temurin:21-jdk-alpine AS commons-build
WORKDIR /commons
COPY --from=commons . .
RUN apk add --no-cache maven && \
    mvn -B -q clean install -DskipTests

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY --from=commons-build /root/.m2 /root/.m2

COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && \
    mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]