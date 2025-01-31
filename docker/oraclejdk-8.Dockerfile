FROM frekele/ant:latest

# Install make and gcc (cc respectively)
RUN apt-get update && \
    DEBIAN_FRONTEND='noninteractive' apt-get install -yq make gcc && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*