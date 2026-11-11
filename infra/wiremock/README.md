# Simulated bank (WireMock)

Stands in for the bank's transfer API (`POST /v1/transfers`) locally and in integration tests.
Every request must send `Idempotency-Key: payment-<id>`; anything else gets a 404.

| Destination account | Response | Payment ends up |
|---|---|---|
| `0000000000` | 200 `DECLINED` (`ACCOUNT_CLOSED`) | `FAILED` |
| `9999999999` | 503 | `PROCESSING`, event in `payment-events-dlt` after retries |
| `4000000000` | 400 | `PROCESSING`, event in `payment-events-dlt` immediately |
| anything else | 200 `ACCEPTED` with a transfer ID | `COMPLETED` |

Requests it received: `curl localhost:8089/__admin/requests`
