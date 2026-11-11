.PHONY: start up down build logs ps clean test

APP_PORT = 8084

ifeq ($(OS),Windows_NT)
	SHELL := cmd.exe
	MVN := .\mvnw.cmd
else
	MVN := ./mvnw
endif

info:
	@echo ""
	@echo "  ========================================================"
	@echo "  Aplicacao : http://localhost:$(APP_PORT)"
	@echo "  Swagger   : http://localhost:$(APP_PORT)/swagger-ui.html"
	@echo "  LocalStack: http://localhost:4566 (DynamoDB, SNS, SQS)"
	@echo "  ========================================================"
	@echo ""

start:
	docker compose down -v --rmi local
	@$(MAKE) info
	docker compose up --build

up:
	docker compose up -d

down:
	docker compose down -v

build:
	$(MVN) clean package -DskipTests

logs:
	docker compose logs -f execution-app

ps:
	docker compose ps

clean:
	$(MVN) clean
	docker compose down -v --rmi local

# testes de integração sobem o DynamoDB Local com Testcontainers (precisa do Docker)
test:
	$(MVN) clean verify
