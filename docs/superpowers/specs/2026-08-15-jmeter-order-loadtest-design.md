# JMeter CLI Order Load Test Design

## Goal

Provide a reproducible, command-line-only JMeter test for
`POST /api/user/order/create` at 100-200 concurrent users.

## Design

- `scripts/loadtest/download-jmeter.sh` downloads and verifies Apache JMeter 5.6.3 into a local cache directory.
- `scripts/loadtest/prepare-data.sql` creates isolated test users and addresses, and resets a selected SKU stock value. It is parameterized by environment variables at invocation time rather than containing credentials.
- `scripts/loadtest/generate-users.py` reads `JWT_SECRET` from the backend `.env` or an explicit environment variable, creates HS256 JWTs matching `JwtUtil`, and writes a CSV containing token and request identifiers.
- `scripts/loadtest/order-create.jmx` uses a CSV Data Set Config and JMeter properties for host, port, thread count, ramp-up, duration, SKU, logistics, and quantity. It sends one order-create request per loop and asserts an HTTP 200 response plus the application success code.
- `scripts/loadtest/run-order-loadtest.sh` validates prerequisites, optionally downloads JMeter, runs non-GUI mode, and writes results under `scripts/loadtest/results/<timestamp>`.

## Data Flow

1. The operator starts MySQL, Redis, and the backend with the same `JWT_SECRET` used for token generation.
2. The operator runs the SQL preparation against the test database and supplies the resulting IDs to the token/CSV generator.
3. JMeter assigns one CSV row to each thread and sends authenticated order-create requests.
4. JMeter produces a raw `.jtl` file and an HTML dashboard.

## Safety and Scope

- No secret is committed to the repository; token generation fails if no secret is available.
- The preparation SQL uses a clearly prefixed test-user namespace and is intended for a local/test database only.
- The default run is conservative: 100 threads, 30-second ramp-up, one request per thread. Higher concurrency is explicitly passed on the command line.
- The test does not call the rate-limited login endpoint during the load phase.

## Verification

- Shell scripts pass `bash -n`.
- Python token generation is checked with `python3 -m py_compile` and a known JWT decode/signature round trip.
- JMeter validates the JMX in non-GUI mode before any live request is made where the installed JMeter version supports it.
