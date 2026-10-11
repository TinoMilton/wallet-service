package com.recargapay.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.WalletId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

	private final RequestFingerprint fingerprints = new RequestFingerprint();
	private final WalletId wallet = new WalletId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

	@Test
	@DisplayName("Equal requests have an equal SHA-256 fingerprint")
	void equalRequestsHaveEqualFingerprints() {
		String first = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("1.0"));

		String second = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("1.00"));

		assertThat(second).isEqualTo(first).hasSize(64);
	}

	@Test
	@DisplayName("Changing the operation, wallet or amount changes the fingerprint")
	void differentRequestsHaveDifferentFingerprints() {
		String original = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("1.00"));

		String changedType = fingerprints.forRequest(OperationType.WITHDRAWAL, wallet, Money.of("1.00"));
		String changedWallet = fingerprints.forRequest(OperationType.DEPOSIT,
				new WalletId(UUID.fromString("00000000-0000-0000-0000-000000000002")), Money.of("1.00"));
		String changedAmount = fingerprints.forRequest(OperationType.DEPOSIT, wallet, Money.of("2.00"));

		assertThat(changedType).isNotEqualTo(original);
		assertThat(changedWallet).isNotEqualTo(original);
		assertThat(changedAmount).isNotEqualTo(original);
	}
}
