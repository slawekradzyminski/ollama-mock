#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION="$1"

docker buildx create --name ollama-mock-builder --use || true
docker buildx inspect --bootstrap

docker buildx build --platform linux/amd64,linux/arm64 \
  -t slawekradzyminski/ollama-mock:$VERSION \
  --push \
  .

echo "Multi-architecture image built and pushed: slawekradzyminski/ollama-mock:$VERSION"
echo "Verify via: docker buildx imagetools inspect slawekradzyminski/ollama-mock:$VERSION"
