.PHONY: help setup up down logs build clean test-happy test-degraded test-all health

# Cores para output
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
RESET  := \033[0m

help: ## Mostra este menu de ajuda
	@echo "$(GREEN)My Labs - Redis Cache$(RESET)"
	@echo ""
	@echo "Comandos disponíveis:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-15s$(RESET) %s\n", $$1, $$2}'
	@echo ""

setup: ## Cria arquivo .env se não existir
	@if [ ! -f .env ]; then \
		echo "$(GREEN)Criando .env com valores padrão...$(RESET)"; \
		echo "PRIMARY_FAIL_RATE=0.3" > .env; \
		echo "SECONDARY_FAIL_RATE=0.2" >> .env; \
		echo "PRIMARY_DELAY_MS=100" >> .env; \
		echo "SECONDARY_DELAY_MS=150" >> .env; \
		echo "$(GREEN)✓ .env criado$(RESET)"; \
	else \
		echo "$(YELLOW).env já existe$(RESET)"; \
	fi

up: setup ## Sobe toda a stack (Redis + App)
	@echo "$(GREEN)Subindo stack...$(RESET)"
	docker-compose up --build -d
	@echo "$(GREEN)✓ Stack rodando$(RESET)"
	@echo ""
	@echo "Aguardando health check..."
	@sleep 5
	@make health

down: ## Para e remove containers
	@echo "$(YELLOW)Parando stack...$(RESET)"
	docker-compose down
	@echo "$(GREEN)✓ Stack parada$(RESET)"

logs: ## Mostra logs em tempo real
	docker-compose logs -f app

build: ## Rebuild da aplicação
	@echo "$(GREEN)Rebuilding aplicação...$(RESET)"
	docker-compose build --no-cache app
	@echo "$(GREEN)✓ Build concluído$(RESET)"

clean: ## Remove containers, volumes e imagens
	@echo "$(RED)Limpando tudo...$(RESET)"
	docker-compose down -v
	docker rmi my-labs-redis-cache-app 2>/dev/null || true
	@echo "$(GREEN)✓ Cleanup concluído$(RESET)"

health: ## Verifica health da aplicação e circuit breakers
	@echo "$(GREEN)Health Check:$(RESET)"
	@curl -s http://localhost:8080/actuator/health | jq '.' || echo "$(RED)App não está respondendo$(RESET)"
	@echo ""
	@echo "$(GREEN)Circuit Breakers Status:$(RESET)"
	@curl -s http://localhost:8080/api/weather/health | jq '.' || echo "$(RED)Endpoint não disponível$(RESET)"

test-happy: ## Testa cenário de sucesso (todas APIs funcionando)
	@echo "$(GREEN)=== Teste: Cenário de Sucesso ===$(RESET)"
	@echo ""
	@echo "Configurando APIs com baixa taxa de falha..."
	@docker-compose exec app sh -c 'echo "PRIMARY_FAIL_RATE=0.0" > /tmp/config && echo "SECONDARY_FAIL_RATE=0.0" >> /tmp/config'
	@echo ""
	@echo "$(YELLOW)Primeira chamada (cache miss):$(RESET)"
	@curl -s http://localhost:8080/api/weather/London | jq '.'
	@echo ""
	@echo "$(YELLOW)Segunda chamada (cache hit):$(RESET)"
	@curl -s http://localhost:8080/api/weather/London | jq '.'
	@echo ""
	@docker-compose logs --tail=20 app | grep -E "CACHE|PRIMARY|SECONDARY|SUCCESS"

test-degraded: ## Testa cenários de degradação (falhas e fallbacks)
	@echo "$(RED)=== Teste: Cenários de Degradação ===$(RESET)"
	@echo ""
	@echo "$(YELLOW)Cenário 1: Primary API falha → usa Secondary$(RESET)"
	@export PRIMARY_FAIL_RATE=1.0 SECONDARY_FAIL_RATE=0.0 && \
	curl -s http://localhost:8080/api/weather/Paris | jq '.'
	@echo ""
	@sleep 2
	@echo "$(YELLOW)Cenário 2: Ambas APIs falham → usa cache stale$(RESET)"
	@export PRIMARY_FAIL_RATE=1.0 SECONDARY_FAIL_RATE=1.0 && \
	curl -s http://localhost:8080/api/weather/Paris | jq '.'
	@echo ""
	@sleep 2
	@echo "$(YELLOW)Cenário 3: Tudo falha + sem cache → erro gracioso$(RESET)"
	@export PRIMARY_FAIL_RATE=1.0 SECONDARY_FAIL_RATE=1.0 && \
	curl -s http://localhost:8080/api/weather/Tokyo | jq '.'
	@echo ""
	@docker-compose logs --tail=30 app | grep -E "FALLBACK|DEGRADED|ERROR|STALE"

test-all: test-happy test-degraded ## Executa todos os testes

# Comandos auxiliares
redis-cli: ## Abre Redis CLI
	docker-compose exec redis redis-cli

redis-monitor: ## Monitora comandos do Redis em tempo real
	docker-compose exec redis redis-cli monitor

app-shell: ## Abre shell no container da app
	docker-compose exec app sh
