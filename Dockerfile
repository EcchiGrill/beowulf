    FROM gradle:8.10-jdk17-alpine AS builder

    WORKDIR /home/gradle/project
    
    COPY . .
    
    RUN gradle :beowulf-cli:installDist --no-daemon
    
    FROM eclipse-temurin:17-jre
    
    RUN apt-get update \
        && apt-get install -y --no-install-recommends rar \
        && ln -s /usr/bin/rar /usr/local/bin/rar \
        && rm -rf /var/lib/apt/lists/*
    
    WORKDIR /app
    
    COPY --from=builder /home/gradle/project/beowulf-cli/build/install/beowulf ./beowulf
    
    ENV HOME=/root
    
    ENTRYPOINT ["./beowulf/bin/beowulf"]
    CMD ["help"]
