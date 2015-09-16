FROM ubuntu:14.10

MAINTAINER Moritz Kaufmann "moritz.kaufmann@tum.de"

# Create user with home directory
RUN useradd -mU sut
RUN useradd -mG sut contest

RUN apt-get update
RUN apt-get -y --force-yes install software-properties-common python-software-properties
RUN sudo add-apt-repository -y ppa:webupd8team/java
RUN sudo apt-get -y --force-yes update
RUN sudo apt-get -y --force-yes install build-essential autoconf automake cmake gcc g++ gccgo
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
RUN sudo apt-get -y --force-yes install oracle-java8-installer oracle-java8-set-default maven ant
RUN sudo apt-get -y --force-yes install curl
RUN curl https://static.rust-lang.org/rustup.sh | sed 's/^RUST_URL.*/RUST_URL="http:\/\/static.rust-lang.org\/dist\/2015-02-10\"/g' | sudo sh
RUN wget https://storage.googleapis.com/golang/go1.4.1.linux-amd64.tar.gz
RUN tar -C /usr/local -xzf go1.4.1.linux-amd64.tar.gz
RUN sudo apt-get -y --force-yes install clang
RUN sudo apt-get -y --force-yes install libjemalloc-dev libboost-all-dev
RUN sudo apt-get -y --force-yes install libtbb-dev


USER contest
