#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

echo "➡️ Stopping and removing existing containers..."
docker-compose -f infrastructure/docker/docker-compose.yml down

echo "➡️ Running tests..."
./gradlew test

echo "➡️ Starting Docker image build..."
./gradlew docker

echo "➡️ Starting Docker Compose services..."
docker-compose -f infrastructure/docker/docker-compose.yml up -d

echo "✅ Deployment complete!"