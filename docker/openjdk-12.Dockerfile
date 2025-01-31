FROM adoptopenjdk/openjdk12:x86_64-debian-jdk-12.0.2_10

ENV ANT_VERSION=1.10.7
ENV ANT_HOME=/opt/ant

WORKDIR /tmp

RUN apt-get update && \
    apt-get install wget

# https://github.com/frekele/docker-ant/blob/master/Dockerfile
# MIT License
# Copyright (c) 2016-2018 @frekele<Leandro Kersting de Freitas>

# Download, extract apache ant to opt folder and add executables to path
RUN wget --no-check-certificate --no-cookies http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz && \
    wget --no-check-certificate --no-cookies http://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz.sha512 && \
    echo "$(cat apache-ant-${ANT_VERSION}-bin.tar.gz.sha512) apache-ant-${ANT_VERSION}-bin.tar.gz" | sha512sum -c && \
    tar -zvxf apache-ant-${ANT_VERSION}-bin.tar.gz -C /opt/ && \
    ln -s /opt/apache-ant-${ANT_VERSION} /opt/ant && \
    rm -f apache-ant-${ANT_VERSION}-bin.tar.gz && \
    rm -f apache-ant-${ANT_VERSION}-bin.tar.gz.sha512 && \
    update-alternatives --install "/usr/bin/ant" "ant" "/opt/ant/bin/ant" 1 && \
    update-alternatives --set "ant" "/opt/ant/bin/ant"

WORKDIR /root

# Install make and gcc (cc respectively)
RUN DEBIAN_FRONTEND='noninteractive' apt-get install -yq make gcc && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
