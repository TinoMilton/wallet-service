package com.recargapay.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.RequestFingerprint;
import com.recargapay.wallet.domain.WalletId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFingerprintsTest {

	private final RequestFingerprints fingerprints = new RequestFingerprints();
	private final WalletId wallet = new WalletId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

	@Test
	@DisplayName("Equal requests have an equal SHA-256 fingerprint")
	void equalRequestsHaveEqualFingerprints() {
		RequestFingerprint first = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("1.0"));

		RequestFingerprint second = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("1.00"));

		assertThat(second).isEqualTo(first);
		assertThat(first.value()).hasSize(64);
	}

	@Test
	@DisplayName("Changing the operation, wallet or amount changes the fingerprint")
	void differentRequestsHaveDifferentFingerprints() {
		RequestFingerprint original = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("1.00"));

		RequestFingerprint changedType = fingerprints.forRequest(OperationType.WITHDRAWAL, wallet, Money.of("1.00"));
		RequestFingerprint changedWallet = fingerprints.forRequest(OperationType.DEPOSIT,
				new WalletId(UUID.fromString("00000000-0000-0000-0000-000000000002")), Money.of("1.00"));
		RequestFingerprint changedAmount = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("2.00"));

		assertThat(changedType).isNotEqualTo(original);
		assertThat(changedWallet).isNotEqualTo(original);
		assertThat(changedAmount).isNotEqualTo(original);
	}

	@Test
	@DisplayName("Transfer fingerprints include both wallet ids and the normalized amount")
	void transferFingerprintDistinguishesBothWalletsAndAmount() {
		WalletId receiver = new WalletId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
		WalletId another = new WalletId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
		RequestFingerprint original = fingerprints.forRequest(OperationType.TRANSFER, wallet, receiver,
				Money.of("1.0"));

		RequestFingerprint sameRequest = fingerprints.forRequest(OperationType.TRANSFER, wallet, receiver,
				Money.of("1.00"));
		RequestFingerprint changedSender = fingerprints.forRequest(OperationType.TRANSFER, another, receiver,
				Money.of("1.00"));
		RequestFingerprint changedReceiver = fingerprints.forRequest(OperationType.TRANSFER, wallet, another,
				Money.of("1.00"));
		RequestFingerprint changedAmount = fingerprints.forRequest(OperationType.TRANSFER, wallet, receiver,
				Money.of("2.00"));

		assertThat(original).isEqualTo(sameRequest);
		assertThat(original.value()).hasSize(64);
		assertThat(changedSender).isNotEqualTo(original);
		assertThat(changedReceiver).isNotEqualTo(original);
		assertThat(changedAmount).isNotEqualTo(original);
	}
}
