# Product Requirements Document — Wallet Service

Source: `specs/Wallet_Service_Assignment.pdf`

## Overview

The Wallet Service manages users' money on the platform. It lets a user hold a balance, add money to it, take money out of it and send money to another user. It also answers what a wallet's balance is now and what it was at any earlier moment.

It is for the platform's other services, which call it whenever a product needs to move or check a user's money, and for the finance and audit teams, who need to explain any balance from its history.

It is valuable because most platform operations depend on it. If it is unavailable, or if a balance cannot be trusted, the platform cannot operate.

## Objectives

- **Correctness:** money is never created, lost or spent twice, including under concurrent requests and client retries.
- **Traceability:** every balance, current or past, can be explained by a complete, unchangeable record of the operations behind it.
- **Availability:** the service is designed so that no single application instance is required for it to keep working.
- **Reviewability:** the code and tests can be read and understood by a person, and every business rule is proven by its own test.

Measures of success:

- 100% of the business rules listed in `techspec.md` section 9.2 have a passing dedicated test.
- Line coverage is at least 90% and branch coverage at least 85%.
- The audit check (cached balance, last ledger balance and sum of entries all equal) holds for every wallet after concurrent load.
- A reviewer can start the service and run the tests with one command each.

## User Stories

Primary persona: **an upstream platform service** acting for a user.

- As a platform service, I want to create a wallet for a user so that the user can hold money.
- As a platform service, I want to read a wallet's current balance so that I can show it or check it before an action.
- As a platform service, I want to deposit money into a wallet so that the user's balance grows by that amount.
- As a platform service, I want to withdraw money from a wallet so that the user can take it out, and I want the request refused if the wallet does not hold enough.
- As a platform service, I want to transfer money between two wallets so that one user can pay another, with both sides changing together or not at all.
- As a platform service, I want to retry a request safely after a timeout so that a network failure never moves money twice.

Secondary persona: **an auditor or finance analyst**.

- As an auditor, I want the balance of a wallet at a specific past moment so that I can verify statements and investigate disputes.
- As an auditor, I want the full list of operations on a wallet so that I can explain how any balance came about.
- As an auditor, I want assurance that recorded operations cannot be altered so that the history can be trusted.

Edge cases the product must handle:

- Two withdrawals arrive at the same moment for the same money.
- Two users transfer to each other at the same moment.
- A request is repeated, with or without changes, under the same retry key.
- A balance is requested for a moment before the wallet existed, or in the future.
- An amount is zero, negative, or has more precision than the currency allows.

## Core Features

### 1. Create Wallet

Creates a wallet for a user. It matters because nothing else can happen without one.

- FR-1.1 A wallet can be created for a user identifier.
- FR-1.2 A new wallet has a balance of zero.
- FR-1.3 A user can have only one wallet; a second attempt is refused.

### 2. Retrieve Balance

Returns what a wallet holds now.

- FR-2.1 The current balance of a wallet can be retrieved.
- FR-2.2 A request for a wallet that does not exist is refused.

### 3. Retrieve Historical Balance

Returns what a wallet held at a past moment. It matters for audits, disputes and statements.

- FR-3.1 The balance at a given past instant can be retrieved.
- FR-3.2 The result is the balance after the last operation at or before that instant.
- FR-3.3 A request for an instant before the wallet existed, or in the future, is refused.

### 4. Deposit Funds

Adds money to a wallet.

- FR-4.1 A deposit increases the balance by exactly the deposited amount.
- FR-4.2 The amount must be positive and have at most two decimal places.

### 5. Withdraw Funds

Takes money out of a wallet.

- FR-5.1 A withdrawal decreases the balance by exactly the withdrawn amount.
- FR-5.2 A withdrawal greater than the balance is refused and changes nothing.

### 6. Transfer Funds

Moves money from one wallet to another.

- FR-6.1 A transfer decreases the sender's balance and increases the receiver's by the same amount.
- FR-6.2 Both changes happen together or not at all.
- FR-6.3 A transfer greater than the sender's balance is refused and changes nothing.
- FR-6.4 A transfer from a wallet to itself is refused.

### 7. Safe Retries

Lets a caller repeat a request without moving money twice.

- FR-7.1 Every deposit, withdrawal and transfer carries a caller-generated retry key.
- FR-7.2 Repeating a request with the same key returns the original result.
- FR-7.3 Reusing a key for a different request is refused.

### 8. Audit Trail

Keeps a complete, unchangeable record of every operation.

- FR-8.1 Every operation is recorded with its amount, its time and the resulting balance.
- FR-8.2 Recorded operations cannot be changed or deleted.
- FR-8.3 The operations on a wallet can be listed.
- FR-8.4 Every successful operation is announced as an event for other systems.

## User Experience

The service has no user interface. Its users are other services and the engineers who integrate them.

- **Predictable API:** resource-oriented HTTP endpoints with JSON bodies and consistent naming.
- **Clear outcomes:** a caller learns immediately whether an operation succeeded.
- **Clear errors:** every refusal has a stable machine-readable code and a human-readable explanation.
- **Self-describing contract:** interactive API documentation is served by the running service.
- **Quick start:** one command starts the service and its database locally.
- **Accessibility:** not applicable, as there is no interface.

## High-level Technical Constraints

- The service is implemented in Java.
- It is mission-critical: downtime impairs most platform operations.
- All operations must be fully traceable, to allow wallet balances to be audited.
- Monetary amounts must be exact; no floating-point arithmetic and no silent rounding.
- Balances and operation history are financial data and must not be alterable after the fact.
- It is delivered as a GitHub repository with instructions to install, test and run it, a written explanation of the design choices, and a written explanation of trade-offs made for time.
- All documentation is in English.
- The intended effort is six to eight hours, and the time actually spent is reported.

## Out of Scope

- User management, authentication and authorisation; callers are assumed to be trusted services.
- More than one wallet per user.
- More than one currency, and currency conversion.
- Overdraft, credit or negative balances.
- Integration with banks, cards or payment providers; a deposit is a plain top-up.
- Fees, limits, fraud checks and regulatory reporting.
- Reversals, refunds and cancelled operations.
- Closing or deleting a wallet.
- A user interface.
- A demonstrated high-availability deployment; it is described, not built.
