// A load generator for the wallet service, written to give the Grafana
// dashboards real traffic: it creates and funds a few dozen wallets, then for a
// few minutes sends a modest mix of deposits, withdrawals, transfers and
// balance reads, plus a small share of idempotent replays, reused idempotency
// keys, withdrawals beyond the balance and requests for an unknown wallet.
//
// Money is never computed through a floating-point number: every amount is
// integer cents formatted into text such as '12.34', and that text goes into
// the request body unquoted, so the server receives a JSON number with two
// decimals.
//
// Run it with the k6 service of the compose file (see the README's Monitoring
// section). Duration, request rate and wallet count come from environment
// variables; every request's allowed answers are listed explicitly, so only a
// truly unexpected status fails a check.

import exec from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const runDuration = __ENV.LOAD_DURATION || '3m';
const requestsPerSecond = Number(__ENV.LOAD_RATE || '20');
const walletCount = Number(__ENV.WALLETS || '30');
const openingBalanceCents = 50000;

// Every answer the script ever expects. Anything outside this list counts as a
// failed request in the k6 summary, alongside a failed check below.
http.setResponseCallback(http.expectedStatuses(200, 201, 404, 422, 503));

export const options = {
	scenarios: {
		walletTraffic: {
			executor: 'constant-arrival-rate',
			rate: requestsPerSecond,
			timeUnit: '1s',
			duration: runDuration,
			preAllocatedVUs: 10,
			maxVUs: 40,
		},
	},
	thresholds: {
		checks: ['rate==1'],
		http_req_failed: ['rate==0'],
	},
};

// Setup: wait for the service, then create and fund the wallets the run trades
// with. It runs once, before any load.

export function setup() {
	waitUntilTheServiceIsReady();
	const runTag = `run-${Date.now()}`;
	const wallets = [];
	for (let index = 0; index < walletCount; index += 1) {
		const wallet = createWallet(`${runTag}-user-${index}`);
		fundWallet(wallet, `${runTag}-opening-deposit-${index}`);
		wallets.push(wallet);
	}
	return { runTag: runTag, wallets: wallets };
}

// One iteration is one action from the traffic mix. The wallets and the
// idempotency key are derived from the iteration number, so they never repeat.

export default function (data) {
	const iteration = exec.scenario.iterationInTest;
	const wallet = data.wallets[iteration % data.wallets.length];
	const counterparty = theCounterpartyOf(data.wallets, iteration);
	const key = `${data.runTag}-${iteration}`;
	pickFromTheTrafficMix()(wallet, counterparty, key);
}

// The traffic mix. Shares are percentages and add up to 100: mostly ordinary
// operations and reads, a small share of the four special cases.

const trafficMix = [
	{ share: 20, run: readTheBalance },
	{ share: 20, run: makeADeposit },
	{ share: 15, run: makeAWithdrawal },
	{ share: 15, run: makeATransfer },
	{ share: 8, run: repeatARequestWithItsKey },
	{ share: 8, run: reuseAKeyWithADifferentAmount },
	{ share: 7, run: withdrawMoreThanTheBalance },
	{ share: 7, run: askForAnUnknownWallet },
];

function pickFromTheTrafficMix() {
	const draw = Math.random() * 100;
	let sharedOut = 0;
	for (const entry of trafficMix) {
		sharedOut += entry.share;
		if (draw < sharedOut) {
			return entry.run;
		}
	}
	return trafficMix[0].run;
}

function theCounterpartyOf(wallets, iteration) {
	// Half the list away from the sender, so the two can never be the same wallet.
	return wallets[(iteration + wallets.length / 2) % wallets.length];
}

// Ordinary traffic. A withdrawal or transfer can be legitimately rejected with
// INSUFFICIENT_FUNDS (422) if the sender has run low, and any money-moving
// request can be answered 503 WALLET_BUSY under contention: safe to retry with
// the same key, and no money moved.

function readTheBalance(wallet, counterparty, key) {
	expectStatus(getBalance(wallet), 'the current balance is returned', [200]);
}

function makeADeposit(wallet, counterparty, key) {
	expectStatus(postDeposit(wallet, key, amountText(smallAmountCents())), 'the deposit is applied, or the wallet is busy',
		[201, 503]);
}

function makeAWithdrawal(wallet, counterparty, key) {
	expectStatus(postWithdrawal(wallet, key, amountText(smallAmountCents())),
		'the withdrawal is applied, rejected for insufficient funds, or the wallet is busy', [201, 422, 503]);
}

function makeATransfer(wallet, counterparty, key) {
	expectStatus(postTransfer(wallet, counterparty, key, amountText(smallAmountCents())),
		'the transfer is applied, rejected for insufficient funds, or the wallet is busy', [201, 422, 503]);
}

// The special cases.

function repeatARequestWithItsKey(wallet, counterparty, key) {
	// The same request twice under one key: the second must replay the first
	// answer, move money once, and count in wallet_idempotent_replays_total.
	const amount = amountText(smallAmountCents());
	expectStatus(postDeposit(wallet, key, amount), 'the deposit is applied, or the wallet is busy', [201, 503]);
	expectStatus(postDeposit(wallet, key, amount), 'the repeat with the same key replays the original answer', [201, 503]);
}

function reuseAKeyWithADifferentAmount(wallet, counterparty, key) {
	// The same key with a different amount must be rejected 422
	// IDEMPOTENCY_KEY_REUSED. If the first attempt never landed (503 moves no
	// money and stores nothing), the second is a fresh request and may be
	// applied; a contention answer is also possible.
	const first = postDeposit(wallet, key, amountText(smallAmountCents()));
	const reuse = postDeposit(wallet, key, amountText(smallAmountCents()));
	expectStatus(first, 'the first deposit is applied, or the wallet is busy', [201, 503]);
	expectStatus(reuse, 'the reused key is rejected, or the first attempt never landed', [201, 422, 503]);
}

function withdrawMoreThanTheBalance(wallet, counterparty, key) {
	// No wallet in this run ever holds a fraction of this, so the only expected
	// answers are the 422 rejection and, under contention, 503.
	expectStatus(postWithdrawal(wallet, key, amountText(100000000)), 'the withdrawal beyond the balance is rejected', [422, 503]);
}

function askForAnUnknownWallet(wallet, counterparty, key) {
	const unknownWallet = { walletId: '00000000-0000-0000-0000-000000000000' };
	expectStatus(getBalance(unknownWallet), 'the unknown wallet is not found', [404]);
}

// The HTTP calls, one per endpoint, with their checks.

function getBalance(wallet) {
	return http.get(`${baseUrl}/api/v1/wallets/${wallet.walletId}/balance`);
}

function postDeposit(wallet, key, amount) {
	return http.post(`${baseUrl}/api/v1/wallets/${wallet.walletId}/deposits`, `{"amount":${amount}}`, {
		headers: moneyMovingHeaders(key),
	});
}

function postWithdrawal(wallet, key, amount) {
	return http.post(`${baseUrl}/api/v1/wallets/${wallet.walletId}/withdrawals`, `{"amount":${amount}}`, {
		headers: moneyMovingHeaders(key),
	});
}

function postTransfer(from, to, key, amount) {
	return http.post(`${baseUrl}/api/v1/transfers`,
		`{"fromWalletId":"${from.walletId}","toWalletId":"${to.walletId}","amount":${amount}}`, {
			headers: moneyMovingHeaders(key),
		});
}

function createWallet(userId) {
	const response = http.post(`${baseUrl}/api/v1/wallets`, `{"userId":"${userId}"}`, {
		headers: { 'Content-Type': 'application/json' },
	});
	expectStatus(response, 'the wallet is created', [201]);
	return response.json();
}

function fundWallet(wallet, key) {
	expectStatus(postDeposit(wallet, key, amountText(openingBalanceCents)), 'the opening deposit is applied', [201]);
}

function waitUntilTheServiceIsReady() {
	const giveUpAt = Date.now() + 120000;
	while (Date.now() < giveUpAt) {
		if (http.get(`${baseUrl}/actuator/health/readiness`).status === 200) {
			return;
		}
		sleep(1);
	}
	throw new Error(`the wallet service did not become ready at ${baseUrl}`);
}

function moneyMovingHeaders(key) {
	return { 'Content-Type': 'application/json', 'Idempotency-Key': key };
}

function expectStatus(response, whatMayHappen, allowedStatuses) {
	check(response, { [whatMayHappen]: (answer) => allowedStatuses.includes(answer.status) });
}

// Money helpers: amounts are integer cents until the last moment, then become
// text with two decimals, placed into the body unquoted so the server receives
// a JSON number.

function smallAmountCents() {
	return 100 + Math.floor(Math.random() * 900);
}

function amountText(cents) {
	const wholePart = Math.floor(cents / 100);
	const centsPart = cents % 100;
	return `${wholePart}.${String(centsPart).padStart(2, '0')}`;
}
