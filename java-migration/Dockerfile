# One image recipe for every service:  docker build --build-arg MODULE=order-service -t athiel-order .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
