.PHONY: help start test-backend test-integration test-frontend image clean

MVN ?= mvn
MVN_FLAGS ?= --batch-mode --no-transfer-progress -q
IMAGE_NAME ?= peoplemesh:local
DOCKERFILE_JVM ?= src/main/docker/Dockerfile.jvm

help:
	@echo "Available targets:"
	@echo "  start         Run app in dev mode (quarkus:dev)"
	@echo "  test-backend      Run backend unit tests (Surefire)"
	@echo "  test-frontend     Run frontend unit tests (Vitest)"
	@echo "  test-integration  Run integration tests (Failsafe)"
	@echo "  image         Build local JVM container image"
	@echo "  clean         Remove build artifacts"

start:
	@echo "Starting app in dev mode..."
	@$(MVN) quarkus:dev

test-backend:
	@echo "Running backend unit tests..."
	@$(MVN) $(MVN_FLAGS) test

test-frontend:
	@echo "Running frontend tests..."
	@cd src/main/web && npm test

test-integration:
	@echo "Running integration tests..."
	@$(MVN) $(MVN_FLAGS) verify -DskipTests=true -DskipITs=false -Djacoco.haltOnFailure=false; rc=$$?; \
	docker ps -q --filter label=org.testcontainers=true | xargs -r docker rm -f > /dev/null 2>&1; \
	exit $$rc

image:
	@echo "Building JVM artifact and container image..."
	@$(MVN) $(MVN_FLAGS) -DskipTests clean package
	@docker build -f $(DOCKERFILE_JVM) -t $(IMAGE_NAME) .

clean:
	@echo "Cleaning build artifacts..."
	@$(MVN) $(MVN_FLAGS) clean
