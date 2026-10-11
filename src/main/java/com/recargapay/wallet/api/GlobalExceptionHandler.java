package com.recargapay.wallet.api;

import com.recargapay.wallet.domain.exception.WalletAlreadyExistsException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The one place that turns failures into RFC 7807 problem details with a stable
 * {@code code} (techspec section 6). Every error of the API goes through here,
 * so callers can rely on the shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(WalletAlreadyExistsException.class)
	public ProblemDetail walletAlreadyExists(WalletAlreadyExistsException duplicateUser) {
		return problem(HttpStatus.CONFLICT, "WALLET_ALREADY_EXISTS", duplicateUser.getMessage());
	}

	@ExceptionHandler(WalletNotFoundException.class)
	public ProblemDetail walletNotFound(WalletNotFoundException unknownWallet) {
		return problem(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", unknownWallet.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail unreadableWalletId(MethodArgumentTypeMismatchException mismatch) {
		// A wallet id in the URL that is not a valid UUID is an unknown wallet.
		String walletId = String.valueOf(mismatch.getValue());
		return problem(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet " + walletId + " does not exist");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail invalidRequest(MethodArgumentNotValidException invalidRequest) {
		FieldError firstError = invalidRequest.getBindingResult().getFieldErrors().getFirst();
		return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", firstError.getDefaultMessage());
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		return problem;
	}
}
