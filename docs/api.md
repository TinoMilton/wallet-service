# API

[← Back to the README](../README.md)

Base path `/api/v1`. Bodies are JSON. Money-moving endpoints require an `Idempotency-Key` header. Errors are RFC 7807 `application/problem+json` with a stable `code`.

| # | Method and path | Purpose | Success | Errors |
|---|---|---|---|---|
| 1 | `POST /wallets` | Create a wallet for a user | 201 | 400, 409 |
| 2 | `GET /wallets/{walletId}/balance` | Current balance | 200 | 404 |
| 3 | `GET /wallets/{walletId}/balance?at={ISO-8601 instant}` | Balance at a past instant | 200 | 400, 404 |
| 4 | `GET /wallets/{walletId}/entries?page=&size=` | The wallet's ledger, newest first, paged | 200 | 400, 404 |
| 5 | `POST /wallets/{walletId}/deposits` | Deposit | 201 | 400, 404, 422, 503 |
| 6 | `POST /wallets/{walletId}/withdrawals` | Withdraw | 201 | 400, 404, 422, 503 |
| 7 | `POST /transfers` | Transfer between two wallets | 201 | 400, 404, 422, 503 |

One example per endpoint, with the responses copied from the verification run against the Docker stack. Run them in order; each later command uses the ids the earlier ones created.

```bash
BASE=http://localhost:8080/api/v1
```

**1. Create a wallet.** A new wallet starts with a balance of zero. A user can have only one wallet; a second attempt returns 409.

```bash
curl -s -X POST $BASE/wallets -H 'Content-Type: application/json' -d '{"userId":"user-a"}'
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","userId":"user-a","balance":0.00,"createdAt":"2026-10-11T18:30:02.236792886Z"}
```
```bash
# the walletId from the response above; this is the one from the verification run
WALLET_ID=bf41c8ee-8fc4-486d-afe0-616bfd9c6710
```

**2. Current balance.**

```bash
curl -s $BASE/wallets/$WALLET_ID/balance
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","balance":35.00,"asOf":"2026-10-11T18:30:07.600566638Z"}
```

**3. Historical balance.** The instant must carry an offset and lie in the past. The result is the balance after the last ledger entry at or before it. Use any `createdAt` from an earlier response.

```bash
curl -s "$BASE/wallets/$WALLET_ID/balance?at=2026-10-11T18:30:02.789409136Z"
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","balance":75.00,"asOf":"2026-10-11T18:30:02.789409136Z"}
```

**4. Ledger entries.** The wallet's audit trail, newest first. `page` starts at 0; `size` defaults to 20 and is capped at 100; the response reports the size the server applied.

```bash
curl -s "$BASE/wallets/$WALLET_ID/entries?page=0&size=10"
```
```json
{"walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","page":0,"size":10,"totalEntries":3,"entries":[
  {"seq":3,"operationId":"002e8a43-eb04-44c1-a10a-17079221fe7a","type":"TRANSFER","amountSigned":-40.00,"balanceAfter":35.00,"createdAt":"2026-10-11T18:30:07.570988Z"},
  {"seq":2,"operationId":"e66553c4-b23b-434e-bd70-61fc78d743d3","type":"WITHDRAWAL","amountSigned":-25.00,"balanceAfter":75.00,"createdAt":"2026-10-11T18:30:02.789409Z"},
  {"seq":1,"operationId":"2c7c9836-3387-4725-af0e-e1627f83e6d7","type":"DEPOSIT","amountSigned":100.00,"balanceAfter":100.00,"createdAt":"2026-10-11T18:30:02.749634Z"}]}
```

**5. Deposit.** The `Idempotency-Key` header is required. Repeating the same request with the same key returns the original result and moves money once.

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/deposits -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-key-1' -d '{"amount":100.00}'
```
```json
{"operationId":"2c7c9836-3387-4725-af0e-e1627f83e6d7","type":"DEPOSIT","walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","amount":100.00,"balance":100.00,"createdAt":"2026-10-11T18:30:02.749634469Z"}
```

**6. Withdraw.** A withdrawal greater than the balance is rejected (422) and changes nothing.

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/withdrawals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: withdrawal-key-1' -d '{"amount":25.00}'
```
```json
{"operationId":"e66553c4-b23b-434e-bd70-61fc78d743d3","type":"WITHDRAWAL","walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","amount":25.00,"balance":75.00,"createdAt":"2026-10-11T18:30:02.789409136Z"}
```

**7. Transfer.** Both wallets change in one transaction or neither does. The response returns the sender's resulting balance, because the caller acts for the sender. The ids below are the ones this run created; replace them with your own sender and receiver.

```bash
curl -s -X POST $BASE/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: transfer-key-1' -d '{"amount":40.00,"fromWalletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","toWalletId":"5ff78ebd-aac5-4a78-a0aa-56c2facae66f"}'
```
```json
{"operationId":"002e8a43-eb04-44c1-a10a-17079221fe7a","type":"TRANSFER","fromWalletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","toWalletId":"5ff78ebd-aac5-4a78-a0aa-56c2facae66f","amount":40.00,"fromBalance":35.00,"createdAt":"2026-10-11T18:30:07.570988472Z"}
```

Two behaviours worth seeing once. A withdrawal beyond the balance is rejected with a problem detail:

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/withdrawals -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: withdrawal-key-2' -d '{"amount":9999.00}'
```
```json
{"detail":"Wallet has insufficient funds","instance":"/api/v1/wallets/bf41c8ee-8fc4-486d-afe0-616bfd9c6710/withdrawals","status":422,"title":"Unprocessable Content","code":"INSUFFICIENT_FUNDS"}
```

Repeating the deposit of step 5 with the same key returns the original result, with the original operation id and balance, and credits nothing again:

```bash
curl -s -X POST $BASE/wallets/$WALLET_ID/deposits -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-key-1' -d '{"amount":100.00}'
```
```json
{"operationId":"2c7c9836-3387-4725-af0e-e1627f83e6d7","type":"DEPOSIT","walletId":"bf41c8ee-8fc4-486d-afe0-616bfd9c6710","amount":100.00,"balance":100.00,"createdAt":"2026-10-11T18:30:02.749634Z"}
```

## Error codes

Every error is an RFC 7807 problem detail with a stable `code`, produced in one place, `GlobalExceptionHandler`.

| Status | `code` | When |
|---|---|---|
| 400 | `INVALID_AMOUNT` | Amount is zero, negative, or has more than two decimal places |
| 400 | `MISSING_IDEMPOTENCY_KEY` | Header absent or blank on a money-moving endpoint |
| 400 | `INVALID_TIMESTAMP` | `at` is malformed, has no offset, or is in the future |
| 400 | `SAME_WALLET_TRANSFER` | Source and destination are the same wallet |
| 400 | `INVALID_REQUEST` | Body is missing, malformed, or fails validation (for example a blank `userId`, an `Idempotency-Key` longer than 128 characters, or an unparsable or negative `page`/`size`) |
| 404 | `WALLET_NOT_FOUND` | Unknown wallet id, or `at` is before the wallet was created |
| 404 | `ROUTE_NOT_FOUND` | No route matches the request |
| 405 | `METHOD_NOT_ALLOWED` | The route does not accept the request's method |
| 409 | `WALLET_ALREADY_EXISTS` | The user already has a wallet |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The media type of the request body is not supported |
| 422 | `INSUFFICIENT_FUNDS` | Withdrawal or transfer exceeds the balance |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Key already used with a different request |
| 503 | `WALLET_BUSY` | Lock wait exceeded the timeout; safe to retry with the same key |
| 500 | `INTERNAL_ERROR` | Unexpected failure; no internal detail is exposed |
