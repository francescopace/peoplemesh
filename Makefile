.PHONY: help start test-backend test-frontend container clean

MVN ?= mvn
MVN_FLAGS ?= --batch-mode --no-transfer-progress -q

help:
	@echo "Available targets:"
	@echo "  start         Run app in dev mode (quarkus:dev)"
	@echo "  test-backend  Run backend tests"
	@echo "  test-frontend Run frontend tests"
	@echo "  container     Build local JVM container image"
	@echo "  clean         Remove build artifacts"

start:
	@echo "Starting app in dev mode..."
	@$(MVN) quarkus:dev

test-backend:
	@echo "Running backend tests..."
	@$(MVN) $(MVN_FLAGS) test

test-frontend:
	@echo "Running frontend tests..."
	@cd src/main/web && npm test

container:
	@echo "Building JVM artifact and container image..."
	@$(MVN) $(MVN_FLAGS) -DskipTests package
	@docker build -f src/main/docker/Dockerfile.jvm -t peoplemesh:local .

clean:
	@echo "Cleaning build artifacts..."
	@$(MVN) $(MVN_FLAGS) clean
