###
# Image pour la compilation
FROM maven:3.9.11-eclipse-temurin-21 AS build-image
WORKDIR /build/

# On lance la compilation Java
# On débute par une mise en cache docker des dépendances Java
# cf https://www.baeldung.com/ops/docker-cache-maven-dependencies
COPY ./pom.xml /build/best-ppn-api/pom.xml
RUN mvn -f /build/best-ppn-api/pom.xml verify --fail-never
# et la compilation du code Java
COPY ./   /build/

RUN mvn --batch-mode \
        -Dmaven.test.skip=true \
        -Duser.timezone=Europe/Paris \
        -Duser.language=fr \
        package -Passembly


###
# Image pour le module API
#FROM tomcat:9-jdk17 as api-image
#COPY --from=build-image /build/web/target/*.war /usr/local/tomcat/webapps/ROOT.war
#CMD [ "catalina.sh", "run" ]
FROM ossyupiik/java:21.0.8 AS best-ppn-api-image
WORKDIR /

COPY --from=build-image /build/target/best-ppn-api-distribution.tar.gz /
RUN tar xvfz best-ppn-api-distribution.tar.gz
RUN rm -f /best-ppn-api-distribution.tar.gz

ENV TZ=Europe/Paris
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

CMD ["java", "-cp", "/best-ppn-api/lib/*", "fr.abes.bestppn.BestPpnApplication"]




