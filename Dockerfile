FROM ibmjava:8
RUN mkdir /var/app
WORKDIR /var/app
ADD ./build/libs /var/app/libs
ADD ./build/resources /var/app/resources
EXPOSE  8080
CMD ["java","-jar","libs/Vertx-1.0-SNAPSHOT-fat.jar"]