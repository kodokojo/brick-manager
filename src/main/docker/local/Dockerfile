FROM java:8-jre

ENV microservice.name brick

ADD kodokojo.jar /project/kodokojo.jar

EXPOSE 80

ENTRYPOINT ["java", "-jar", "/project/kodokojo.jar"]
