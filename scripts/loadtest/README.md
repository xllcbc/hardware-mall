# CLI Order Load Test

CLI-only load test for `POST /api/user/order/create` using Apache JMeter 5.6.3
in non-GUI mode. Every request is authenticated with a single test user's JWT
and every successful request creates one order and decreases SKU stock, so run
against a local/test database only.

## Prerequisites

- Java 8+ (on WSL, the Windows `java.exe` is supported by the runner)
- Python 3 (only used by the runner to parse login responses)
- MySQL, Redis, and the Spring Boot backend running

## Run

The first run downloads JMeter 5.6.3 from the Apache CDN and verifies its
SHA-512 checksum. If `JWT_TOKEN` is not set, the runner logs in once with a
`test_` code and creates a load-test address automatically; it also discovers a
SKU and logistics row when `SKU_ID`/`LOGISTICS_ID` are not provided.

Default 100 threads:

```bash
bash scripts/loadtest/run-order-loadtest.sh
```

200 threads:

```bash
THREADS=200 RAMP_UP=30 DURATION=60 \
  bash scripts/loadtest/run-order-loadtest.sh
```

Optional overrides:

```bash
HOST=127.0.0.1 PORT=8080 \
JWT_TOKEN=<token> ADDRESS_ID=<id> SKU_ID=<id> LOGISTICS_ID=<id> QUANTITY=1 \
THREADS=200 RAMP_UP=30 DURATION=60 \
  bash scripts/loadtest/run-order-loadtest.sh
```

## Stock

Each order subtracts `QUANTITY` from the selected SKU. Before a meaningful run,
ensure the SKU has enough stock, or reset it:

```sql
UPDATE sku SET stock = 10000 WHERE id = <sku_id> AND status = 1;
```

Then restart nothing; the order service re-syncs stock cache after each commit.

## Results

Results are written to `scripts/loadtest/results/<timestamp>/`, including
`results.jtl` and an HTML dashboard under `report/`. These directories are
git-ignored.
