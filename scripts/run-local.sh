#!/bin/zsh

set -e

# Force Java 21 for this project
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

# Load local environment variables
set -a
source .env
set +a

# Start Spring Boot
./mvnw spring-boot:run