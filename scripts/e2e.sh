#!/usr/bin/env bash
set -euo pipefail

CONFIG_URL="${CONFIG_URL:-http://localhost:8081}"
ANALYSIS_URL="${ANALYSIS_URL:-http://localhost:8082}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-60}"

for command in curl jq docker; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Fehlendes Werkzeug: $command" >&2
    exit 1
  fi
done

wait_for_health() {
  local url="$1"
  local name="$2"
  echo "Warte auf $name ..."
  for _ in $(seq 1 40); do
    if curl -fsS "$url/actuator/health/liveness" | jq -e '.status == "UP"' >/dev/null 2>&1; then
      echo "$name ist UP"
      return 0
    fi
    sleep 1
  done
  echo "$name wurde nicht rechtzeitig erreichbar." >&2
  return 1
}

create_configuration() {
  local cooling_system="${1:-STANDARD}"
  curl -fsS -X POST "$CONFIG_URL/api/configurations" \
    -H 'Content-Type: application/json' \
    -d '{
      "oilSystem": "STANDARD",
      "fuelSystem": "PREMIUM",
      "coolingSystem": "'"$cooling_system"'",
      "electricalSystem": "PREMIUM",
      "engineManagementSystem": "ADVANCED"
    }' | jq -r '.configurationId'
}

start_analysis() {
  local configuration_id="$1"
  curl -fsS -X POST "$ANALYSIS_URL/api/analyses" \
    -H 'Content-Type: application/json' \
    -d "{\"configurationId\":\"$configuration_id\"}" | jq -r '.analysisId'
}

get_analysis() {
  local analysis_id="$1"
  curl -fsS "$ANALYSIS_URL/api/analyses/$analysis_id"
}

wait_for_overall_result() {
  local analysis_id="$1"
  local expected="$2"
  for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
    local body
    body="$(get_analysis "$analysis_id")"
    local result
    result="$(jq -r '.overallResult // empty' <<<"$body")"
    if [[ "$result" == "$expected" ]]; then
      echo "$body"
      return 0
    fi
    sleep 1
  done
  echo "Analysis $analysis_id erreichte overallResult=$expected nicht." >&2
  get_analysis "$analysis_id" >&2 || true
  return 1
}

wait_for_algorithm_status() {
  local analysis_id="$1"
  local algorithm="$2"
  local expected="$3"
  for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
    local body
    body="$(get_analysis "$analysis_id")"
    local status
    status="$(jq -r --arg algorithm "$algorithm" '.algorithms[] | select(.algorithm == $algorithm) | .status' <<<"$body")"
    if [[ "$status" == "$expected" ]]; then
      echo "$body"
      return 0
    fi
    sleep 1
  done
  echo "$algorithm erreichte Status $expected nicht." >&2
  get_analysis "$analysis_id" >&2 || true
  return 1
}

# Health darf wegen OPEN mit HTTP 503 antworten; den JSON-Body trotzdem lesen.
wait_for_breaker() {
  local expected="$1"
  for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
    if curl -sS --max-time 3 http://localhost:8083/actuator/health |
      jq -e --arg state "$expected" '.. | objects | select(.state? == $state)' >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "Fluid → Thermal erreichte $expected nicht." >&2
  return 1
}

cleanup() {
  docker compose start thermal-analysis-service >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for_health "$CONFIG_URL" "configuration-service"
wait_for_health "$ANALYSIS_URL" "analysis-management-service"

for port in 8083 8084 8085 8086; do
  wait_for_health "http://localhost:$port" "analysis-service:$port"
done

echo
echo "=== E2E-01 Happy Path ==="
configuration_id="$(create_configuration)"
analysis_id="$(start_analysis "$configuration_id")"
final_body="$(wait_for_overall_result "$analysis_id" "OK")"

ready_count="$(jq '[.algorithms[] | select(.status == "READY" and .result == "OK")] | length' <<<"$final_body")"
if [[ "$ready_count" != "4" ]]; then
  echo "Erwartet wurden vier READY/OK Algorithmen, erhalten: $ready_count" >&2
  exit 1
fi

echo "Happy Path erfolgreich: $analysis_id"

echo
echo "=== E2E-02 Thermal-Ausfall + automatische Circuit-Breaker-Recovery ==="
docker compose stop thermal-analysis-service >/dev/null

configuration_id="$(create_configuration)"
analysis_id="$(start_analysis "$configuration_id")"
failed_body="$(wait_for_algorithm_status "$analysis_id" "THERMAL" "FAILED")"

if [[ "$(jq -r '.overallResult' <<<"$failed_body")" != "FAILED" ]]; then
  echo "Nach Thermal-Ausfall wurde overallResult=FAILED erwartet." >&2
  exit 1
fi

wait_for_breaker OPEN
echo "Thermal-Ausfall wurde korrekt erkannt."

docker compose start thermal-analysis-service >/dev/null
wait_for_health "http://localhost:8084" "thermal-analysis-service"

echo "Thermal ist wieder da. Kein manueller Retry: warte auf HALF_OPEN-Probe und automatisches Resume ..."
recovery_body="$(wait_for_overall_result "$analysis_id" "OK")"

ready_count="$(jq '[.algorithms[] | select(.status == "READY" and .result == "OK")] | length' <<<"$recovery_body")"
if [[ "$ready_count" != "4" ]]; then
  echo "Nach automatischer Recovery wurden vier READY/OK Algorithmen erwartet, erhalten: $ready_count" >&2
  exit 1
fi

wait_for_breaker CLOSED
fluid_starts="$(docker compose logs --no-color fluid-analysis-service | grep -F -c "Starting FLUID analysis $analysis_id" || true)"
if [[ "$fluid_starts" != "1" ]]; then
  echo "Fluid muss genau einmal laufen, beobachtet: $fluid_starts" >&2
  exit 1
fi
echo "Automatische Recovery erfolgreich: $analysis_id"

echo
echo "=== E2E-03 INVALID-Konfigurationsvariante wird fachlich abgelehnt ==="
configuration_id="$(create_configuration INVALID)"
analysis_id="$(start_analysis "$configuration_id")"
invalid_body="$(wait_for_algorithm_status "$analysis_id" "THERMAL" "FAILED")"

if [[ "$(jq -r '.overallResult' <<<"$invalid_body")" != "FAILED" ]]; then
  echo "Für coolingSystem=INVALID wurde overallResult=FAILED erwartet." >&2
  exit 1
fi

fluid_status="$(jq -r '.algorithms[] | select(.algorithm == "FLUID") | .status' <<<"$invalid_body")"
electrical_status="$(jq -r '.algorithms[] | select(.algorithm == "ELECTRICAL") | .status' <<<"$invalid_body")"
engine_status="$(jq -r '.algorithms[] | select(.algorithm == "ENGINE_MANAGEMENT") | .status' <<<"$invalid_body")"
thermal_message="$(jq -r '.algorithms[] | select(.algorithm == "THERMAL") | .message // ""' <<<"$invalid_body")"

if [[ "$fluid_status" != "READY" || "$electrical_status" != "PENDING" || "$engine_status" != "PENDING" ]]; then
  echo "INVALID sollte die Kette bei THERMAL stoppen, ohne nachgelagerte Algorithmen zu starten." >&2
  echo "$invalid_body" >&2
  exit 1
fi

if [[ "$thermal_message" != *"Invalid thermal configuration"* ]]; then
  echo "Erwartete fachliche Fehlermeldung der Thermal-Analyse fehlt." >&2
  echo "$invalid_body" >&2
  exit 1
fi

sleep 5
invalid_body="$(get_analysis "$analysis_id")"
if ! jq -e '.overallResult == "FAILED" and
    ([.algorithms[] | select(.algorithm == "THERMAL" and .status == "FAILED")] | length == 1) and
    ([.algorithms[] | select((.algorithm == "ELECTRICAL" or .algorithm == "ENGINE_MANAGEMENT") and .status == "PENDING")] | length == 2)' <<<"$invalid_body" >/dev/null; then
  echo "Fachliches INVALID darf nicht automatisch fortgesetzt werden." >&2
  exit 1
fi

echo "INVALID wurde korrekt im zuständigen Algorithmus erkannt und nicht weiterverarbeitet: $analysis_id"
echo
echo "Alle End-to-End-Tests erfolgreich."

