# My Labs - Redis as Resilience Layer

> Part 1 of "Building Resilient Java Applications" series

[![dev.to](https://img.shields.io/badge/Read%20Article-dev.to-0A0A0A?style=flat-square&logo=dev.to)](https://dev.to/theoscaargomes/fallback-e-degradacao-graciosa-com-redis-e-circuit-breaker-dn0)

## 📖 Sobre

Este projeto demonstra como implementar **degradação graciosa** e **fallback chain** usando Redis como camada de resiliência, combinado com Circuit Breaker pattern (Resilience4j).

**Problema:** APIs externas falham. Sistemas quebram. Usuários frustrados.

**Solução:** Uma arquitetura resiliente que garante disponibilidade mesmo quando tudo dá errado.

### Cenários Cobertos

1. ✅ **API primária offline** → fallback para API secundária
2. ✅ **Ambas APIs offline** → retorna cache stale (dados expirados mas válidos)
3. ✅ **Rate limit atingido** → circuit breaker abre, usa cache
4. ✅ **Timeout excessivo** → aborta e usa cache
5. ✅ **Cache vazio + APIs down** → erro gracioso (não quebra o sistema)

## 📚 Artigo Relacionado

Leia o artigo completo no dev.to: [Fallback e Degradação Graciosa com Redis e Circuit Breaker](https://dev.to/theoscaargomes/fallback-e-degradacao-graciosa-com-redis-e-circuit-breaker-dn0)

## 🚀 Quick Start

### Pré-requisitos

- Docker & Docker Compose
- Make (opcional, facilita comandos)
- Java 21+ e Maven (apenas se quiser rodar fora do Docker)

### Setup em 3 passos

```bash
# 1. Clone o repositório
git clone https://github.com/yourusername/my-labs-redis-cache.git
cd my-labs-redis-cache

# 2. Suba a stack
make up

# 3. Teste!
make test-happy
```

### Comandos Disponíveis

```bash
make help           # Lista todos os comandos
make up             # Sobe Redis + App
make down           # Para tudo
make logs           # Mostra logs em tempo real
make health         # Verifica status e circuit breakers
make test-happy     # Testa cenário de sucesso
make test-degraded  # Testa cenários de falha
make clean          # Remove tudo
```

## 🛠️ Tech Stack

- **Java 21** - Linguagem
- **Spring Boot 3.2.1** - Framework
- **Redis 7** - Cache distribuído
- **Resilience4j** - Circuit Breaker, Retry, Time Limiter
- **Docker** - Containerização
- **Maven** - Build

## 📋 Endpoints da API

### Buscar dados meteorológicos
```bash
GET http://localhost:8080/api/weather/{city}

# Exemplo
curl http://localhost:8080/api/weather/London | jq
```

**Resposta de sucesso:**
```json
{
  "city": "London",
  "temperature": 18.5,
  "description": "Partly Cloudy",
  "humidity": 72,
  "windSpeed": 18.3,
  "source": "OpenWeather",
  "timestamp": "2026-01-29T01:30:00Z",
  "fromCache": false,
  "staleData": false
}
```

**Resposta com cache:**
```json
{
  "city": "London",
  "temperature": 18.5,
  "description": "Partly Cloudy",
  "humidity": 72,
  "windSpeed": 18.3,
  "source": "OpenWeather",
  "timestamp": "2026-01-29T01:25:00Z",
  "fromCache": true,
  "staleData": false
}
```

**Resposta com cache stale (APIs falharam):**
```json
{
  "city": "London",
  "temperature": 18.5,
  "description": "Partly Cloudy",
  "humidity": 72,
  "windSpeed": 18.3,
  "source": "OpenWeather",
  "timestamp": "2026-01-29T01:15:00Z",
  "fromCache": true,
  "staleData": true
}
```

### Health Check
```bash
GET http://localhost:8080/api/weather/health

# Exemplo
curl http://localhost:8080/api/weather/health | jq
```

**Resposta:**
```json
{
  "primaryApi": {
    "state": "CLOSED",
    "failureRate": "10.0%",
    "slowCallRate": "0.0%"
  },
  "secondaryApi": {
    "state": "CLOSED",
    "failureRate": "5.0%",
    "slowCallRate": "0.0%"
  }
}
```

## 🧪 Testando Cenários de Resiliência

### Cenário 1: Happy Path (Tudo Funcionando)

```bash
make test-happy
```

**O que acontece:**
1. Primeira chamada: cache miss → chama API primária → salva no cache
2. Segunda chamada: cache hit → retorna em ~5ms

**Logs esperados:**
```
[CACHE MISS] No fresh data for city: London
[PRIMARY API] Calling OpenWeather for city: London
[SUCCESS] ✓ Primary API - 100ms
[CACHE] Saved data for city: London (TTL: 5min)
---
[CACHE HIT] Fresh data for city: London
[SUCCESS] ✓ Cache hit (fresh data) - 0ms
```

### Cenário 2: Primary API Falha → Fallback Secondary

```bash
# Simula falha da API primária
export PRIMARY_FAIL_RATE=1.0
export SECONDARY_FAIL_RATE=0.0

curl http://localhost:8080/api/weather/Paris | jq
```

**Logs esperados:**
```
[PRIMARY API] Calling OpenWeather for city: Paris
[CIRCUIT BREAKER] Primary API failed: OpenWeather API unavailable
[FALLBACK] Primary API failed, trying secondary API...
[SECONDARY API] Calling WeatherAPI for city: Paris
[SUCCESS] ✓ Secondary API (fallback) - 150ms
```

### Cenário 3: Ambas APIs Falham → Cache Stale

```bash
# Simula falha de ambas as APIs
export PRIMARY_FAIL_RATE=1.0
export SECONDARY_FAIL_RATE=1.0

# Primeira chamada para popular cache
curl http://localhost:8080/api/weather/Berlin | jq

# Aguarda TTL expirar (5 minutos) ou limpa cache
# Depois chama novamente

curl http://localhost:8080/api/weather/Berlin | jq
```

**Logs esperados:**
```
[CIRCUIT BREAKER] Primary API circuit breaker activated
[FALLBACK] Primary API failed, trying secondary API...
[CIRCUIT BREAKER] Secondary API circuit breaker activated
[FALLBACK] Secondary API failed, trying stale cache...
[CACHE STALE] Returning expired data for city: Berlin
[DEGRADED] ⚠ Using stale cache data
```

### Cenário 4: Tudo Falha + Sem Cache → Erro Gracioso

```bash
# Nova cidade sem cache
curl http://localhost:8080/api/weather/Tokyo | jq
```

**Logs esperados:**
```
[CACHE MISS] No fresh data for city: Tokyo
[CIRCUIT BREAKER] Primary API circuit breaker activated
[FALLBACK] Primary API failed, trying secondary API...
[CIRCUIT BREAKER] Secondary API circuit breaker activated
[FALLBACK] Secondary API failed, trying stale cache...
[CACHE EMPTY] No stale data available for city: Tokyo
[ERROR] ✗ All sources failed, returning graceful error
```

**Resposta:**
```json
{
  "city": "Tokyo",
  "description": "Error: All weather services are currently unavailable. Please try again later.",
  "timestamp": "2026-01-29T01:45:00Z",
  "fromCache": false,
  "staleData": false
}
```

## ⚙️ Configuração

### Ajustando Taxa de Falhas das APIs

Edite `.env` ou defina variáveis de ambiente:

```bash
# Cenário realista (falhas ocasionais)
PRIMARY_FAIL_RATE=0.3      # 30% de falhas
SECONDARY_FAIL_RATE=0.2    # 20% de falhas

# Happy path
PRIMARY_FAIL_RATE=0.0
SECONDARY_FAIL_RATE=0.0

# Worst case
PRIMARY_FAIL_RATE=1.0
SECONDARY_FAIL_RATE=1.0
```

### Ajustando Circuit Breaker

Edite `src/main/resources/application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10           # Janela de análise
        failureRateThreshold: 50        # Abre se 50% falharem
        waitDurationInOpenState: 10s    # Tempo em OPEN
```

### Ajustando TTL do Cache

Edite `CacheService.java`:

```java
private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
```

## 📊 Monitoramento

### Spring Boot Actuator

```bash
# Health geral
curl http://localhost:8080/actuator/health

# Métricas dos circuit breakers
curl http://localhost:8080/actuator/circuitbreakers

# Eventos dos circuit breakers
curl http://localhost:8080/actuator/circuitbreakerevents
```

### Redis Monitor

```bash
# Ver comandos em tempo real
make redis-monitor

# Acessar Redis CLI
make redis-cli
```

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────┐
│          Weather Dashboard              │
├─────────────────────────────────────────┤
│  Controller → Service → CacheService    │
└──────────────┬──────────────────────────┘
               │
      ┌────────┴────────┐
      ↓                 ↓
[Resilience4j]    [Redis Cache]
(CB + Retry)      (TTL: 5min)
      │
      ↓
┌──────────────────────┐
│   External APIs      │
│ - OpenWeather (1°)   │
│ - WeatherAPI  (2°)   │
└──────────────────────┘
```

**Fallback Chain:**
```
Request 
  → Cache Fresh? 
    → Yes: Return (5ms)
    → No: Try Primary API
      → Success: Save + Return (100ms)
      → Fail: Try Secondary API
        → Success: Save + Return (150ms)
        → Fail: Try Cache Stale
          → Found: Return stale (8ms) ⚠️
          → Empty: Graceful Error ❌
```

## 🔍 Estrutura do Projeto

```
my-labs-redis-cache/
├── src/main/java/com/mylabs/resilientcache/
│   ├── Application.java
│   ├── controller/
│   │   └── WeatherController.java
│   ├── service/
│   │   ├── WeatherService.java      # Lógica de fallback
│   │   └── CacheService.java        # Gestão Redis
│   ├── client/
│   │   ├── WeatherClient.java
│   │   ├── OpenWeatherClient.java   # API primária
│   │   └── WeatherApiClient.java    # API secundária
│   ├── config/
│   │   └── RedisConfig.java
│   └── model/
│       └── WeatherResponse.java
├── src/main/resources/
│   └── application.yml               # Config Resilience4j
├── docker-compose.yml
├── Dockerfile
├── Makefile
├── .env.example
└── README.md
```

## 🎯 Trade-offs e Considerações

### Vantagens
✅ **Alta disponibilidade**: Sistema continua funcionando mesmo com falhas  
✅ **Performance**: Cache reduz latência de ms para µs  
✅ **Custo**: Reduz chamadas para APIs pagas  
✅ **UX**: Usuário sempre recebe resposta (mesmo que stale)

### Desvantagens
⚠️ **Complexidade**: Mais código para manter  
⚠️ **Stale data**: Dados podem estar desatualizados  
⚠️ **Debug**: Mais difícil rastrear problemas  
⚠️ **Infra**: Dependência adicional (Redis)

### Quando Usar
- ✅ Dados que mudam com frequência moderada (clima, cotações, etc)
- ✅ APIs externas instáveis ou com rate limit
- ✅ Necessidade de alta disponibilidade
- ✅ Tolerância a dados levemente desatualizados

### Quando NÃO Usar
- ❌ Dados críticos que não podem estar stale (transações financeiras)
- ❌ APIs muito estáveis e rápidas
- ❌ Dados que mudam em tempo real
- ❌ Sistemas simples sem necessidade de alta disponibilidade

## 📝 Próximos Passos

Este é o **Artigo #1** da série "Building Resilient Java Applications".

**Próximo artigo:** Integration Testing with Testcontainers  
→ Como testar toda essa resiliência de forma automatizada

## 🤝 Contribuindo

Issues e Pull Requests são bem-vindos!

## 📄 Licença

MIT License - sinta-se livre para usar em seus projetos.

---

**Autor:** Oscar Gomes  
**Blog:** [dev.to/theoscaargomes](https://dev.to/theoscaargomes)  
**Série:** Building Resilient Java Applications (#1)
