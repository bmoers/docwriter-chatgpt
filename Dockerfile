FROM eclipse-temurin:17-jdk

VOLUME [ "/data" ]

WORKDIR /work

COPY ./target/*.jar /work/app.jar

ENTRYPOINT ["java", "-jar", "/work/app.jar"]

CMD ["--maxFileToChange=1", "--classDoc=true", "--publicMethodDoc=true", "--nonPublicMethodDoc=true"]
