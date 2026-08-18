-include .env
export

.PHONY: help up down restart logs build test test-unit test-integration format format-check clean run

help: ## Show this help message
	@echo Available commands:
	@echo   up               - Start order-service own Postgres in Docker
	@echo   down             - Stop it
	@echo   restart          - Restart it
	@echo   logs             - Tail logs from local infrastructure
	@echo   build            - Compile the project (no tests)
	@echo   test-unit        - Run unit tests only
	@echo   test-integration - Run unit + integration tests
	@echo   test             - Alias for test-integration
	@echo   format           - Auto-format code with Spotless
	@echo   format-check     - Check code formatting without modifying files
	@echo   clean            - Remove build artifacts
	@echo   run              - Run the application locally

up: ## Start order-service's own Postgres in Docker
	docker compose up -d

down: ## Stop it
	docker compose down

restart: down up ## Restart it

logs: ## Tail logs from local infrastructure
	docker compose logs -f

build: ## Compile the project (no tests)
	mvn --batch-mode --no-transfer-progress compile

test-unit: ## Run unit tests only
	mvn --batch-mode --no-transfer-progress test

test-integration: ## Run unit + integration tests
	mvn --batch-mode --no-transfer-progress verify

test: test-integration ## Alias for test-integration

format: ## Auto-format code with Spotless
	mvn --batch-mode --no-transfer-progress spotless:apply

format-check: ## Check code formatting without modifying files
	mvn --batch-mode --no-transfer-progress spotless:check

clean: ## Remove build artifacts
	mvn --batch-mode --no-transfer-progress clean

run: ## Run the application locally
	mvn spring-boot:run