# Lavadero Athiel: Spring Boot port

Java 21 / Spring Boot 3.4 version of the Python (FastAPI) backend that lives one
folder up. The Python code is untouched; this folder is self-contained and both
can run side by side.

## Layout

| Module | Port | Replaces (Python) | Responsibility |
|---|---|---|---|
| `common` | - | - | Shared event/DTO records and the JWT helper |
| `api-gateway` | 8080 | `main.py` routing, CORS, `get_current_user` | Single public entry, JWT check on protected routes |
| `auth-service` | 8081 | `login.py`, `users` table | `POST /login`, `GET /me` |
| `payment-service` | 8082 | `routes/payment/payment_routes.py` | Mercado Pago preference, webhook, idempotency, outbox |
| `device-service` | 8083 | `tuya/tuya_api.py`, `cloud_providers/shelly` | Relay on/off/status behind `DeviceGateway` |
| `order-service` | 8084 | `domain/start_stop_relay.py`, settings, reports, `box_routes.py` | Orders, wash sessions, persisted timer, history |

```
Browser -> api-gateway -> auth-service
                       -> order-service -REST-> payment-service -> Mercado Pago
                       -> payment-service (webhook only)
payment-service --RabbitMQ payment.approved--> order-service -REST-> device-service -> Shelly / Tuya
```

device-service has no gateway route and no published port.

## Same API as the Python service

These paths, parameters and JSON field names are unchanged, so the existing
frontend should keep working through the gateway (it expects the `/api` prefix
to be stripped by Nginx, as with FastAPI's `root_path`):

`POST /login`, `GET /me`, `GET|PUT /settings`, `POST /create_preference`,
`POST /webhook`, `GET /success|/failure|/pending`, `POST /payments/manual`,
`POST /boxes/{box_name}?action=stop`, `GET /boxes/time_left?box_name=`,
`GET /boxes/relay_status?box_name=`, `GET /reports/monthly?year=&month=`.

Errors keep the FastAPI shape `{"detail": "..."}`.
`POST /create_preference` additionally returns `order_id`.

## Behaviour that changed on purpose

| Python | Java | Why |
|---|---|---|
| Any approved payment in the last 5 min starts a wash | The payment must carry an `external_reference` that matches a pending order, and the paid amount must equal the order amount | A payment was never tied to an order |
| Webhook body trusted after `payment.get` | Also verifies Mercado Pago `x-signature` when `MP_WEBHOOK_SECRET` is set (skipped with a warning when empty) | Authenticity check was missing |
| `processed_payments` marked before the relay starts | `payment_records` unique row plus an outbox flag; the event is re-sent until the broker accepts it | An approved payment can no longer be lost between steps |
| Timer = daemon thread + `relay_state.json` | `wash_sessions.expires_at` in the database, polled every second; reconciled on startup | A restart left the relay on |
| `GET /boxes/time_left` could stop the relay | Read-only | A GET had a side effect |
| `POST /boxes/{box}` open to anyone | Needs the admin JWT (list is in `api-gateway/application.yml`) | Anyone could stop the machine |
| Tuya "success" taken as relay on | device-service reads the state back; `confirmed=false` becomes `ACTIVATION_FAILED` | Command accepted is not machine on |
| Second payment while running extends `end_time` | Same: extends the running session (one history row with the summed price) | Preserved |
| Shelly module called the device at import time | No work at import | Bug |

Session states: `ACTIVATING`, `RUNNING`, `STOPPING`, `COMPLETED`,
`ACTIVATION_FAILED`, `SHUTDOWN_FAILED` (retried every 10 s until the device
confirms OFF). Order states: `PENDING_PAYMENT`, `PAID`, `AMOUNT_MISMATCH`,
`CANCELLED`.

## Run

Tests (no Docker, no network; H2 in memory):

```
mvn test
```

Full stack:

```
cp .env.example .env      # fill in the values
docker compose up --build
```

Variables are listed in `.env.example`. Python names were kept where possible
(`MPP_ACCESS_TOKEN` became `MP_ACCESS_TOKEN`; the DEBUG/`P_` prefix switch was
dropped, use one `.env` per environment). `JWT_SECRET_KEY` must be 32+
characters; a shorter Python secret has to be lengthened (existing tokens stop
working, users log in again).

## Data

SQLite is not carried over: each service has its own PostgreSQL database and
creates its tables (`ddl-auto: update`). `wash_history` and `relay_errors` keep
the Python column names, so history can be copied with a one-off
`INSERT ... SELECT` if you want it in the new reports. Existing admin password
hashes are Argon2 and are verified by Spring's `Argon2PasswordEncoder`; copy the
`users` rows to auth_db, or set `ADMIN_USERNAME` / `ADMIN_PASSWORD` to seed one
(the password in `init_scripts/seed.py` is deliberately not copied here).

## Not verified / known limits

- Mercado Pago, Shelly and Tuya calls were not exercised against the real
  services. They are written from the Python code and the public REST docs, and
  covered only by unit tests with fakes. Do one real test payment and one real
  relay cycle before using it with customers.
- `docker-compose.yml` was not run in the environment where this was written.
- Compatibility of old passlib Argon2 hashes with Spring's encoder is expected
  (both use the standard `$argon2id$...` format) but not tested with a real hash.
- The per-device lock that serialises activations is in-process: run a single
  `order-service` instance.
- Only the WASH service exists, on `box1`. Foam and vacuum need new rows in
  `ServiceCatalog` and new entries under `athiel.devices.list`.
- Schema is managed by Hibernate `ddl-auto: update`; move to Flyway before
  production.
- Local control of Shelly (RPC on the LAN), MQTT and Home Assistant are not
  implemented; they would be extra `DeviceGateway` beans.
