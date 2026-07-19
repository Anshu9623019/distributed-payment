# Distributed Payment Platform — Cleaned Codebase

This is your codebase reorganized with every bug found across review fixed.
Nothing architectural was removed — Saga, ledger, wallet caching, API-key
auth, Stripe top-ups are all still here — but three real gaps are now closed
and several runtime crashes are fixed.

## What actually changed

### 1. Idempotency was rejecting retries instead of serving them
**Before:** `IdempotencyService.acquireLock()` threw `DuplicateResourceException`
on any second request with the same key — including the legitimate retry a
client sends because the first response was lost on the network. That's the
opposite of what idempotency is for.

**Now:** `IdempotencyService` is two-tier — a short-lived Redis lock only
rejects a request that's *genuinely still processing right now*; a
longer-lived Redis cache stores the **final response** and serves it back on
replay. `PaymentService.initiatePayment` checks the cache first, falls back
to the DB (`findByIdempotencyKey`) in case the cache expired, and only then
takes the lock.

### 2. No outbox — the payment/Kafka dual-write problem was still live
**Before:** `PaymentService` did `paymentRepository.save(payment)` then
`kafkaTemplate.send(...)` as two independent operations. If Kafka was
unreachable after the DB commit, the payment sat in `INITIATED` forever with
no event ever published.

**Now:** a real `outbox_events` table (`com.distributed_payment.outbox`)
is written in the **same transaction** as the payment/wallet-debit insert.
A `@Scheduled` `OutboxPublisher` polls `PENDING` rows every second, relays
them to Kafka, and marks them `PUBLISHED` (or `FAILED` after 5 retries).
This is now used both by `PaymentService` (payment-initiated) and
`WalletEventConsumer` (wallet-debited / payment-failed), since the same
dual-write risk existed there too.

### 3. Runtime crashes fixed
- `WalletService`'s `@CacheEvict(key = "#walletId")` referenced a Spring EL
  variable that didn't exist on `credit`/`debit` (their params are
  `ownerId`/`ownerType`) — this would throw `SpelEvaluationException` on
  every call. Replaced with manual `CacheManager` eviction using the real
  wallet ID once it's loaded.
- `MerchantController` did `Long.valueOf(authentication.getName())`, but
  `getName()` returns the user's **email** — guaranteed `NumberFormatException`.
  Fixed by reading the numeric ID off the `User` principal directly
  (`JwtAuthenticationFilter` sets the full `User` entity as principal).
- `wallets.owner_id` was `UNIQUE` alone. Since customer IDs and merchant IDs
  are separate sequences that both start at 1, the first customer and first
  merchant wallet collide. Fixed to a composite `UNIQUE(owner_id, owner_type)`.
- `RegisterRequest` had `@Size(min = 5)` next to a message claiming "at least
  8 characters" — fixed to match (8).
- Dead `hasRole('USER')` check removed from `MerchantController` (`Role` only
  has `CUSTOMER/MERCHANT/ADMIN`, so it could never match).
- `credit`/`debit` now use `findByOwnerIdAndOwnerTypeForUpdate` (`SELECT ...
  FOR UPDATE`) instead of the non-locking read that was defined but unused —
  concurrent transfers on the same wallet now serialize at the DB instead of
  throwing uncaught `OptimisticLockException` inside a Kafka consumer.
- `debit()`'s insufficient-balance case now throws a dedicated
  `InsufficientBalanceException` (422) instead of a generic
  `IllegalArgumentException`, handled by a new `GlobalExceptionHandler`.
- JWT secret moved out of source into `application.yml` / `JWT_SECRET` env var.
- Package names `ladger` → `ledger`, `mercent` → `merchant` (typos).
- Consolidated two different `ResourceNotFoundException` classes
  (`auth.exception` / `customer.exception`) into one shared
  `com.distributed_payment.exception` package, alongside the new
  `InsufficientBalanceException`, `RequestInProgressException`, and
  `GlobalExceptionHandler`.
- Filled in classes that were referenced but never shown/created:
  `CustomerRepository`, `UserRepository`, `PaymentRepository`,
  `CustomerResponse`, `UpdateCustomerRequest`, `EntryType`.

### 4. Added while wiring up the frontend
- **New endpoints**: `GET/POST /wallets/me` & `/wallets/topup`, `GET /payments`
  (history), `GET /merchants/me`, `GET /customers/lookup?email=`. Wallets are
  now auto-created on customer registration and merchant onboarding.
- **Payment ownership bug**: `Payment.senderWalletId`/`receiverWalletId`
  actually stored a Customer/Merchant **id**, not a wallets.id — and with no
  owner-type column, a customer #3 and a merchant #3 were indistinguishable.
  Renamed to `senderOwnerId`/`senderOwnerType`/`receiverOwnerId`/
  `receiverOwnerType`, with a corrected repository query
  (`findForOwner`) and schema. **If you already ran the old schema.sql,
  drop and recreate the `payments` table** — the column names changed.
- **CORS**: added for `http://localhost:5173` (the Vite dev server) in
  `SecurityConfig`.

## Structure
```
src/main/java/com/distributed_payment/
  auth/         registration, login, JWT issuance
  customer/     customer profile CRUD
  merchant/     merchant onboarding, API-key auth, webhook notifications
  wallet/       balance, credit/debit, Redis cache
  ledger/       immutable double-entry bookkeeping
  payment/      payment initiation, Saga consumer, Stripe top-up
  outbox/       transactional outbox entity + scheduled publisher
  security/     JWT + API-key filters, Spring Security config
  exception/    shared exceptions + global handler
  config/       Kafka producer config (String-based payloads)
src/main/resources/
  application.yml
  db/migration/schema.sql
```

## Not yet implemented (from your original roadmap)
Fraud detection, settlement service, notification service (email/SMS),
observability (Micrometer/Prometheus/Grafana), and the React dashboard are
still open — this pass focused on making Phases 1–5 (auth → payment →
saga → ledger → outbox) actually correct and runnable end to end.
