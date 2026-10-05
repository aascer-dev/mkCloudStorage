FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY mkcs-common/pom.xml mkcs-common/pom.xml
COPY mkcs-model/pom.xml mkcs-model/pom.xml
COPY mkcs-server/pom.xml mkcs-server/pom.xml
RUN chmod +x mvnw
RUN ./mvnw -B -ntp dependency:go-offline

COPY mkcs-common mkcs-common
COPY mkcs-model mkcs-model
COPY mkcs-server mkcs-server
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /workspace/mkcs-server/target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
