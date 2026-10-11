package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.RequestFingerprint;
import com.recargapay.wallet.domain.WalletId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * The fingerprint of what a money request asks for, as SHA-256: the operation
 * type, the wallet or wallets and the normalized amount. A deposit and a
 * withdrawal of the same amount on the same wallet therefore fingerprint
 * differently, and so do two deposits of different amounts.
 */
@Component
public class RequestFingerprints {

	public RequestFingerprint forRequest(OperationType type, WalletId walletId, Money amount) {
		String request = type.name() + "\n" + walletId.value() + "\n" + amount;
		return sha256(request);
	}

	public RequestFingerprint forRequest(OperationType type, WalletId from, WalletId to, Money amount) {
		String request = type.name() + "\n" + from.value() + "\n" + to.value() + "\n" + amount;
		return sha256(request);
	}

	private RequestFingerprint sha256(String request) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return new RequestFingerprint(
					HexFormat.of().formatHex(sha256.digest(request.getBytes(StandardCharsets.UTF_8))));
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException("SHA-256 is unavailable", unavailable);
		}
	}
}
