.PHONY: help start test-backend test-integration test-frontend image container clean

MVN ?= mvn
MVN_FLAGS ?= --batch-mode --no-transfer-progress -q
IMAGE_NAME ?= peoplemesh:local
DOCKERFILE_JVM ?= src/main/docker/Dockerfile.jvm

help:
	@echo "Available targets:"
	@echo "  start         Run app in dev mode (quarkus:dev)"
	@echo "  test-backend      Run backend unit tests (Surefire)"
	@echo "  test-integration  Run backend integration tests (Failsafe)"
	@echo "  test-frontend     Run frontend tests"
	@echo "  image         Build local JVM container image"
	@echo "  container     Alias for image"
	@echo "  clean         Remove build artifacts"

start:
	@echo "Starting app in dev mode..."
	@$(MVN) quarkus:dev

test-backend:
	@echo "Running backend unit tests..."
	@$(MVN) $(MVN_FLAGS) test

test-integration:
	@echo "Running backend integration tests..."
	@$(MVN) $(MVN_FLAGS) verify -DskipUnitTests=true; rc=$$?; \
	docker ps -q --filter label=org.testcontainers=true | xargs -r docker rm -f > /dev/null 2>&1; \
	exit $$rc

test-frontend:
	@echo "Running frontend tests..."
	@cd src/main/web && npm test

image:
	@echo "Building JVM artifact and container image..."
	@$(MVN) $(MVN_FLAGS) -DskipTests clean package
	@docker build -f $(DOCKERFILE_JVM) -t $(IMAGE_NAME) .

container: image

clean:
	@echo "Cleaning build artifacts..."
	@$(MVN) $(MVN_FLAGS) clean
