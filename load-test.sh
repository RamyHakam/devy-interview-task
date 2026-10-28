#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
CUSTOMER="${CUSTOMER:-1}"
FROM="${FROM:-2025-01-01T00:00:00Z}"
TO="${TO:-2027-01-01T00:00:00Z}"
CONCURRENCY="${CONCURRENCY:-50}"
REQUESTS="${REQUESTS:-500}"

python3 - "$BASE_URL" "$CUSTOMER" "$FROM" "$TO" "$CONCURRENCY" "$REQUESTS" <<'PY'
import sys, urllib.request, concurrent.futures, time, statistics
base, customer, start, end, c, n = sys.argv[1:]
url=f"{base}/customers/{customer}/reports/spend?from={start}&to={end}&group_by=team,category,month"
def one(_):
    t=time.perf_counter()
    with urllib.request.urlopen(url, timeout=30) as r: r.read()
    return (time.perf_counter()-t)*1000
with concurrent.futures.ThreadPoolExecutor(max_workers=int(c)) as ex:
    values=list(ex.map(one, range(int(n))))
values.sort()
def pct(p): return values[min(len(values)-1, int(len(values)*p/100))]
print(f"requests={len(values)} concurrency={c}")
print(f"P50={pct(50):.2f}ms P95={pct(95):.2f}ms P99={pct(99):.2f}ms")
PY
