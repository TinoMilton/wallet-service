package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.WalletId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RequestFingerprint {

	public String forRequest(OperationType type, WalletId walletId, Money amount) {
		String request = type.name() + "\n" + walletId.value() + "\n" + amount;
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(request.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException("SHA-256 is unavailable", unavailable);
		}
	}
}
