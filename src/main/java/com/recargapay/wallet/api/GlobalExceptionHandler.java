package com.recargapay.wallet.api;

import com.recargapay.wallet.domain.exception.IdempotencyKeyReusedException;
import com.recargapay.wallet.domain.exception.InsufficientFundsException;
import com.recargapay.wallet.domain.exception.InvalidAmountException;
import com.recargapay.wallet.domain.exception.InvalidIdempotencyKeyException;
import com.recargapay.wallet.domain.exception.InvalidPageRequestException;
import com.recargapay.wallet.domain.exception.InvalidTimestampException;
import com.recargapay.wallet.domain.exception.MissingIdempotencyKeyException;
import com.recargapay.wallet.domain.exception.SameWalletTransferException;
import com.recargapay.wallet.domain.exception.WalletAlreadyExistsException;
import com.recargapay.wallet.domain.exception.WalletBusyException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

	@ExceptionHandler(InsufficientFundsException.class)
	public ProblemDetail insufficientFunds(InsufficientFundsException insufficientFunds) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS", insufficientFunds.getMessage());
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	public ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException reused) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", reused.getMessage());
	}

	@ExceptionHandler(WalletBusyException.class)
	public ProblemDetail walletBusy(WalletBusyException busy) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "WALLET_BUSY", busy.getMessage());
	}

	@ExceptionHandler(MissingIdempotencyKeyException.class)
	public ProblemDetail missingKey(MissingIdempotencyKeyException missingKey) {
		return problem(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", missingKey.getMessage());
	}

	@ExceptionHandler(InvalidAmountException.class)
	public ProblemDetail invalidAmount(InvalidAmountException invalid) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", invalid.getMessage());
	}

	@ExceptionHandler(InvalidIdempotencyKeyException.class)
	public ProblemDetail invalidIdempotencyKey(InvalidIdempotencyKeyException invalid) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", invalid.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail invalidRequest(MethodArgumentNotValidException invalidRequest) {
		FieldError firstError = invalidRequest.getBindingResult().getFieldErrors().getFirst();
		return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", firstError.getDefaultMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail unreadableBody(HttpMessageNotReadableException unreadableBody) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "the request body is missing or malformed");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ProblemDetail unknownRoute(NoResourceFoundException unknownRoute) {
		return problem(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "No route matches the request");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ProblemDetail wrongMethod(HttpRequestMethodNotSupportedException wrongMethod) {
		return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "the route does not accept this method");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException unsupportedMediaType) {
		return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
				"the media type of the request body is not supported");
	}

	@ExceptionHandler(SameWalletTransferException.class)
	public ProblemDetail sameWalletTransfer(SameWalletTransferException sameWallet) {
		return problem(HttpStatus.BAD_REQUEST, "SAME_WALLET_TRANSFER", sameWallet.getMessage());
	}

	@ExceptionHandler(InvalidTimestampException.class)
	public ProblemDetail invalidTimestamp(InvalidTimestampException invalid) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_TIMESTAMP", invalid.getMessage());
	}

	@ExceptionHandler(InvalidPageRequestException.class)
	public ProblemDetail invalidPageRequest(InvalidPageRequestException invalid) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", invalid.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail internalFailure(Exception unexpected) {
		// The failure is reported, never explained: internal detail stays out of the
		// response.
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		return problem;
	}
}
