FROM sbtscala/scala-sbt:graalvm-community-21.0.2_1.10.7_3.6.3

WORKDIR /app

COPY . .

RUN ["sbt", "compile"]

EXPOSE 7000

ENTRYPOINT ["sbt", "run"]
