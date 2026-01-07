###
# Image pour la compilation
FROM maven:3.9.11-eclipse-temurin-21 AS build-image
WORKDIR /build/
# Installation et configuration de la locale FR
RUN apt update && DEBIAN_FRONTEND=noninteractive apt -y install locales
RUN sed -i '/fr_FR.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen
ENV LANG fr_FR.UTF-8
ENV LANGUAGE fr_FR:fr
ENV LC_ALL fr_FR.UTF-8


# On lance la compilation Java
# On débute par une mise en cache docker des dépendances Java
# cf https://www.baeldung.com/ops/docker-cache-maven-dependencies
COPY ./pom.xml /build/best-ppn-api/pom.xml
RUN mvn -f /build/best-ppn-api/pom.xml verify --fail-never
# et la compilation du code Java
COPY ./   /build/

RUN mvn --batch-mode \
        -Dmaven.test.skip=false \
        -Duser.timezone=Europe/Paris \
        -Duser.language=fr \
        package spring-boot:repackage


###
# Image pour le module API
#FROM tomcat:9-jdk17 as api-image
#COPY --from=build-image /build/web/target/*.war /usr/local/tomcat/webapps/ROOT.war
#CMD [ "catalina.sh", "run" ]
FROM ossyupiik/java:21.0.8 AS best-ppn-api-image
WORKDIR /app/

COPY --from=build-image /build/target/*.jar /app/best-ppn-api.jar

ENV TZ=Europe/Paris
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

CMD ["java", "-jar", "/app/best-ppn-api.jar"]



