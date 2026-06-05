.PHONY: start stop \
       gateway member auth

BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m

COMPOSE_FILE := infrastructure/docker/docker-compose.yml

start:
	@echo "$(BLUE)🚀 Starting all services...$(NC)"
	@chmod +x scripts/start.sh
	@./scripts/start.sh

stop:
	@echo "$(YELLOW)⏸️ Stopping all services...$(NC)"
	@docker-compose -f $(COMPOSE_FILE) down

gateway:
	@echo "$(BLUE)🚀 Starting Gateway Service...$(NC)"
	@./gradlew :services:gateway-service:test
	@./gradlew :services:gateway-service:docker
	@docker-compose -f $(COMPOSE_FILE) up -d gateway-service

member:
	@echo "$(BLUE)🚀 Starting Member Service...$(NC)"
	@./gradlew :services:member-service:test
	@./gradlew :services:member-service:docker
	@docker-compose -f $(COMPOSE_FILE) up -d member-service

auth:
	@echo "$(BLUE)🚀 Starting Auth Service...$(NC)"
	@./gradlew :services:auth-service:test
	@./gradlew :services:auth-service:docker
	@docker-compose -f $(COMPOSE_FILE) up -d auth-service
