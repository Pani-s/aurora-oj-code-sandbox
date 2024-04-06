# Docker 镜像构建
# @author pani
#
FROM openjdk:8
ADD aurora-code-sandbox-0.0.1-SNAPSHOT.jar aurora-code-sandbox-0.0.1-SNAPSHOT.jar
ENTRYPOINT java -Xmx512m -jar -Duser.timezone=GMT+08  aurora-code-sandbox-0.0.1-SNAPSHOT.jar