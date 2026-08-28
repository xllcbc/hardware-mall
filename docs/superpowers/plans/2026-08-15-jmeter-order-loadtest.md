# JMeter CLI Order Load Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe, reusable CLI-only JMeter harness for 100-200 concurrent order-create requests.

**Architecture:** Shell scripts own download, prerequisite checks, SQL instructions, and JMeter execution. A dependency-free Python script generates HS256 JWTs and CSV rows from the backend secret without storing the secret. The JMX reads all runtime values from JMeter properties and CSV data.

**Tech Stack:** Apache JMeter 5.6.3, Java 8+, Bash, Python 3, MySQL, Spring Boot JWT API.

---

### Task 1: Add data preparation and token generation

**Files:**
- Create: `scripts/loadtest/prepare-data.sql`
- Create: `scripts/loadtest/generate-users.py`
- Create: `scripts/loadtest/users.csv.example`

- [ ] Add SQL that inserts a prefixed test user and address set, resets a selected SKU stock, and documents the required `@sku_id`, `@logistics_id`, and user range variables. The SQL must not contain passwords or JWT secrets.
- [ ] Add Python CLI arguments for `--secret`, `--user-start`, `--count`, `--address-start`, `--sku-id`, `--logistics-id`, `--output`, and `--expiration-seconds`; use HMAC-SHA256 JWT signing with claims `userId` and `role: 1`.
- [ ] Make token generation fail with a clear error if the secret is absent or shorter than 32 bytes, and write a CSV header plus one row per user.
- [ ] Add an example CSV showing the required columns without real tokens.
- [ ] Verify with `python3 -m py_compile scripts/loadtest/generate-users.py` and a temporary known-secret generation run.

### Task 2: Add the JMeter test plan

**Files:**
- Create: `scripts/loadtest/order-create.jmx`

- [ ] Define JMeter properties for `host`, `port`, `threads`, `ramp_up`, `duration`, `sku_id`, `logistics_id`, `quantity`, and `users_csv` with conservative defaults.
- [ ] Configure CSV Data Set Config with `token,user_id,address_id,sku_id,logistics_id,quantity` columns and stop threads at EOF.
- [ ] Configure an HTTP POST to `/api/user/order/create` with `Authorization: Bearer ${token}`, JSON content type, and the order request body.
- [ ] Assert HTTP 200 and application JSON success code `200`; do not add listeners that retain every response in memory during load.
- [ ] Verify the JMX is well-formed XML and uses only standard JMeter components.

### Task 3: Add CLI download and execution wrappers

**Files:**
- Create: `scripts/loadtest/download-jmeter.sh`
- Create: `scripts/loadtest/run-order-loadtest.sh`
- Create: `scripts/loadtest/README.md`

- [ ] Download Apache JMeter 5.6.3 from the official CDN with `curl`, verify the published SHA-512 checksum, and extract into a local cache outside the repository by default.
- [ ] Make the runner validate Java, JMeter, JMX, and CSV prerequisites; read `JWT_SECRET` from `JWT_SECRET` or `hardware-mall-backend/.env` without printing it.
- [ ] Run `jmeter -n -t ... -l ... -e -o ...` with timestamped result directories and command-line overrides for 100/200 threads.
- [ ] Document MySQL/Redis/backend startup, SQL preparation, token generation, example commands, stock reset, and the warning to use a test database only.
- [ ] Verify all shell scripts with `bash -n` and run the downloader/version check.

### Task 4: Verify and report

**Files:**
- Verify: `scripts/loadtest/**`

- [ ] Run static checks for shell, Python, XML, and JMeter startup.
- [ ] If the backend is running and test IDs are supplied, execute a small one-thread smoke test before any 100-200 thread run.
- [ ] Record any unavailable live dependency (MySQL/backend/test IDs) without claiming a live load test passed.
