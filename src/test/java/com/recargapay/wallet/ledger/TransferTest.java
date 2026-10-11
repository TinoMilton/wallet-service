package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.TransferActions.transfer;
import static com.recargapay.wallet.support.TransferActions.transferWithoutKey;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import com.recargapay.wallet.support.TransferAssertions;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransferTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Autowired
	private TransferAssertions transfers;

	@Test
	@DisplayName("BR-08: a transfer debits the sender and credits the receiver equally")
	void transferMovesTheSameAmountBetweenWallets() {
		WalletId sender = aWalletWithBalance("100.00");
		WalletId receiver = aWalletWithBalance("20.00");

		Response transferred = transfer(sender, receiver, "30.25");

		transferred.then().statusCode(201);
		assertThat(transferred.jsonPath().getString("type")).isEqualTo("TRANSFER");
		assertThat(transferred.jsonPath().getUUID("fromWalletId")).isEqualTo(sender.value());
		assertThat(transferred.jsonPath().getUUID("toWalletId")).isEqualTo(receiver.value());
		assertThat(transferred.asString()).contains("\"amount\":30.25", "\"fromBalance\":69.75");
		assertThat(transferred.jsonPath().getMap("$")).doesNotContainKey("toBalance");
		assertBalance(sender, "69.75");
		assertBalance(receiver, "50.25");
		ledger.assertEntry(sender, 2, "-30.25", "69.75");
		ledger.assertEntry(receiver, 2, "30.25", "50.25");
	}

	@Test
	@DisplayName("BR-09: insufficient funds reject a transfer without changing either wallet")
	void insufficientFundsLeaveBothWalletsUnchanged() {
		WalletId sender = aWalletWithBalance("10.00");
		WalletId receiver = aWalletWithBalance("20.00");

		Response rejected = transfer(sender, receiver, "10.01");

		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(sender, "10.00");
		assertBalance(receiver, "20.00");
		ledger.assertLedgerEntryCount(sender, 1);
		ledger.assertLedgerEntryCount(receiver, 1);
	}

	@Test
	@DisplayName("BR-10: a transfer to the same wallet is rejected without an entry")
	void transferToSelfIsRejected() {
		WalletId wallet = aWalletWithBalance("10.00");

		Response rejected = transfer(wallet, wallet, "1.00");

		assertRejected(rejected, 400, "SAME_WALLET_TRANSFER");
		assertBalance(wallet, "10.00");
		ledger.assertLedgerEntryCount(wallet, 1);
	}

	@Test
	@DisplayName("BR-11: a transfer from an unknown sender is rejected")
	void unknownSenderIsRejected() {
		WalletId sender = new WalletId(UUID.randomUUID());
		WalletId receiver = aWalletWithBalance("10.00");

		Response rejected = transfer(sender, receiver, "1.00");

		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
		assertBalance(receiver, "10.00");
		ledger.assertLedgerEntryCount(sender, 0);
		ledger.assertLedgerEntryCount(receiver, 1);
	}

	@Test
	@DisplayName("BR-11: a transfer to an unknown receiver is rejected without debiting the sender")
	void unknownReceiverIsRejected() {
		WalletId sender = aWalletWithBalance("10.00");
		WalletId receiver = new WalletId(UUID.randomUUID());

		Response rejected = transfer(sender, receiver, "1.00");

		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
		assertBalance(sender, "10.00");
		ledger.assertLedgerEntryCount(sender, 1);
		ledger.assertLedgerEntryCount(receiver, 0);
	}

	@Test
	@DisplayName("BR-17: a transfer records two signed entries under one operation")
	void transferRecordsOneOperationWithTwoEntries() {
		WalletId sender = aWalletWithBalance("20.00");
		WalletId receiver = aWallet();

		Response transferred = transfer(sender, receiver, "5.00");

		transferred.then().statusCode(201);
		transfers.assertOneTransferOperationWithTwoEntries(transferred);
		ledger.assertEntry(sender, 2, "-5.00", "15.00");
		ledger.assertEntry(receiver, 1, "5.00", "5.00");
	}

	@Test
	@DisplayName("BR-20: a transfer without an idempotency key is rejected")
	void missingTransferKeyIsRejected() {
		WalletId sender = aWalletWithBalance("10.00");
		WalletId receiver = aWallet();

		Response rejected = transferWithoutKey(sender, receiver, "1.00");

		assertRejected(rejected, 400, "MISSING_IDEMPOTENCY_KEY");
		assertBalance(sender, "10.00");
		assertBalance(receiver, "0.00");
		ledger.assertLedgerEntryCount(sender, 1);
		ledger.assertLedgerEntryCount(receiver, 0);
	}

	@Test
	@DisplayName("BR-11: a malformed sender id is treated as an unknown wallet")
	void malformedSenderIsRejected() {
		WalletId receiver = aWallet();

		Response rejected = transfer("not-a-uuid", receiver.value().toString(), "1.00");

		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
		assertBalance(receiver, "0.00");
		ledger.assertLedgerEntryCount(receiver, 0);
	}

	@Test
	@DisplayName("BR-11: a malformed receiver id is treated as an unknown wallet")
	void malformedReceiverIsRejected() {
		WalletId sender = aWalletWithBalance("10.00");

		Response rejected = transfer(sender.value().toString(), "not-a-uuid", "1.00");

		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
		assertBalance(sender, "10.00");
		ledger.assertLedgerEntryCount(sender, 1);
	}

	@Test
	@DisplayName("BR-03: a transfer with a zero amount is rejected")
	void invalidTransferAmountIsRejected() {
		WalletId sender = aWalletWithBalance("10.00");
		WalletId receiver = aWallet();

		Response rejected = transfer(sender, receiver, "0");

		assertRejected(rejected, 400, "INVALID_AMOUNT");
		assertBalance(sender, "10.00");
		assertBalance(receiver, "0.00");
		ledger.assertLedgerEntryCount(sender, 1);
		ledger.assertLedgerEntryCount(receiver, 0);
	}
}
