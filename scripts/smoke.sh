#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080}"
SHOW=22222222-2222-2222-2222-222222222222

if [[ -n "${FRONTROW_TOKEN_SECRET:-}" ]]; then
  TOKEN=$(FRONTROW_TOKEN_SECRET="$FRONTROW_TOKEN_SECRET" python3 - <<'PY'
import base64, hashlib, hmac, os, time
def b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
unsigned = b64(b"ada") + "." + b64(str(int(time.time()) + 3600).encode())
sig = hmac.new(os.environ["FRONTROW_TOKEN_SECRET"].encode(), unsigned.encode(), hashlib.sha256).digest()
print(unsigned + "." + b64(sig))
PY
)
else
  TOKEN=$(curl -sf -X POST "$BASE/api/dev/tokens" -H 'Content-Type: application/json' -d '{"userId":"ada"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
fi

curl -sf "$BASE/api/shows" | python3 -c 'import json,sys; assert any(s["name"]=="Opening Night" for s in json.load(sys.stdin))'
SEAT=$(curl -sf "$BASE/api/shows/$SHOW/seats" | python3 -c 'import json,sys; seats=json.load(sys.stdin); print(next(s["seatId"] for s in seats if s["status"]=="AVAILABLE"))')
CODE=$(curl -s -o /tmp/frontrow-book.json -w '%{http_code}' -X POST "$BASE/api/shows/$SHOW/bookings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: smoke-$(date +%s)-ada" \
  -H 'Content-Type: application/json' \
  -d "{\"seatId\":\"$SEAT\"}")
test "$CODE" = "202"
BOOKING=$(python3 -c 'import json; print(json.load(open("/tmp/frontrow-book.json"))["bookingId"])')

for _ in $(seq 1 30); do
  STATUS=$(curl -s -o /tmp/frontrow-get.json -w '%{http_code}' "$BASE/api/bookings/$BOOKING" -H "Authorization: Bearer $TOKEN")
  if [[ "$STATUS" == "200" ]] && python3 -c 'import json,sys; sys.exit(0 if json.load(open("/tmp/frontrow-get.json")).get("status")=="CONFIRMED" else 1)'; then
    break
  fi
  sleep 1
done
python3 -c 'import json,sys; sys.exit(0 if json.load(open("/tmp/frontrow-get.json")).get("status")=="CONFIRMED" else 1)'

OTHER=$(curl -sf -X POST "$BASE/api/dev/tokens" -H 'Content-Type: application/json' -d '{"userId":"grace"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])' || true)
if [[ -z "$OTHER" && -n "${FRONTROW_TOKEN_SECRET:-}" ]]; then
  OTHER=$(FRONTROW_TOKEN_SECRET="$FRONTROW_TOKEN_SECRET" python3 - <<'PY'
import base64, hashlib, hmac, os, time
def b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
unsigned = b64(b"grace") + "." + b64(str(int(time.time()) + 3600).encode())
sig = hmac.new(os.environ["FRONTROW_TOKEN_SECRET"].encode(), unsigned.encode(), hashlib.sha256).digest()
print(unsigned + "." + b64(sig))
PY
)
fi
LOSER=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/shows/$SHOW/bookings" \
  -H "Authorization: Bearer $OTHER" \
  -H "Idempotency-Key: smoke-$(date +%s)-grace" \
  -H 'Content-Type: application/json' \
  -d "{\"seatId\":\"$SEAT\"}")
test "$LOSER" = "409"
curl -sf "$BASE/actuator/prometheus" -o /tmp/frontrow-metrics.txt
grep -q frontrow_payment_outcomes_total /tmp/frontrow-metrics.txt
echo "smoke ok: $BOOKING confirmed"
