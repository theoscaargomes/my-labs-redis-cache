#!/bin/bash

# Script de teste interativo para demonstrar resiliência
# Uso: ./test-scenarios.sh

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

API_URL="http://localhost:8080"

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}  My Labs - Redis Resilience${NC}"
echo -e "${BLUE}  Interactive Test Script${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Função para fazer requisição e mostrar resultado
test_request() {
    local city=$1
    local scenario=$2
    
    echo -e "${YELLOW}Testing: ${scenario}${NC}"
    echo "City: $city"
    echo ""
    
    response=$(curl -s "$API_URL/api/weather/$city")
    
    echo "Response:"
    echo "$response" | jq '.'
    echo ""
    
    # Extrai informações chave
    from_cache=$(echo "$response" | jq -r '.fromCache')
    stale=$(echo "$response" | jq -r '.staleData')
    source=$(echo "$response" | jq -r '.source')
    
    if [ "$from_cache" = "true" ]; then
        if [ "$stale" = "true" ]; then
            echo -e "${RED}⚠ STALE CACHE DATA${NC}"
        else
            echo -e "${GREEN}✓ CACHE HIT (fresh)${NC}"
        fi
    else
        echo -e "${GREEN}✓ API CALL: $source${NC}"
    fi
    
    echo ""
    echo "---"
    echo ""
}

# Função para verificar health
check_health() {
    echo -e "${BLUE}Checking Circuit Breakers Health...${NC}"
    curl -s "$API_URL/api/weather/health" | jq '.'
    echo ""
    echo "---"
    echo ""
}

# Menu interativo
show_menu() {
    echo -e "${BLUE}Select test scenario:${NC}"
    echo ""
    echo "1) Happy Path - Cache Miss + Cache Hit"
    echo "2) Primary API Fails - Uses Secondary"
    echo "3) Both APIs Fail - Uses Stale Cache"
    echo "4) All Fails - Graceful Error"
    echo "5) Check Health & Circuit Breakers"
    echo "6) Run All Scenarios"
    echo "0) Exit"
    echo ""
    echo -n "Choice: "
}

# Cenário 1: Happy Path
scenario_happy() {
    echo -e "${GREEN}=== SCENARIO 1: Happy Path ===${NC}"
    echo ""
    
    echo "First call (cache miss):"
    test_request "London" "Cache Miss → Primary API"
    
    echo "Second call (cache hit):"
    test_request "London" "Cache Hit"
}

# Cenário 2: Primary fails
scenario_primary_fails() {
    echo -e "${YELLOW}=== SCENARIO 2: Primary Fails ===${NC}"
    echo ""
    echo "Simulating primary API failure..."
    echo ""
    
    test_request "Paris" "Primary Fail → Secondary API"
}

# Cenário 3: Both fail
scenario_both_fail() {
    echo -e "${RED}=== SCENARIO 3: Both APIs Fail ===${NC}"
    echo ""
    echo "First, populate cache..."
    test_request "Berlin" "Populate Cache"
    
    echo "Waiting 10 seconds..."
    sleep 10
    
    echo "Now with high failure rates (should use stale cache)..."
    test_request "Berlin" "Both Fail → Stale Cache"
}

# Cenário 4: Everything fails
scenario_all_fail() {
    echo -e "${RED}=== SCENARIO 4: Complete Failure ===${NC}"
    echo ""
    echo "Testing new city without cache..."
    test_request "Tokyo" "No Cache + Both APIs Fail → Graceful Error"
}

# Loop principal
while true; do
    show_menu
    read -r choice
    echo ""
    
    case $choice in
        1)
            scenario_happy
            ;;
        2)
            scenario_primary_fails
            ;;
        3)
            scenario_both_fail
            ;;
        4)
            scenario_all_fail
            ;;
        5)
            check_health
            ;;
        6)
            scenario_happy
            sleep 3
            scenario_primary_fails
            sleep 3
            check_health
            ;;
        0)
            echo -e "${GREEN}Goodbye!${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}Invalid option${NC}"
            ;;
    esac
    
    echo ""
    echo -e "${BLUE}Press Enter to continue...${NC}"
    read -r
    clear
done
