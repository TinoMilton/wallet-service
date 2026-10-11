package com.recargapay.wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document springdoc serves: the API's title, and the one place the
 * shared error responses are described. Every endpoint's error is an RFC 7807
 * problem detail with a stable {@code code} (techspec section 6), so the
 * controllers reference these responses instead of repeating them.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	/**
	 * The balance endpoint is one operation that serves both the current balance
	 * and the balance at a past instant, so its at parameter is optional. The flag
	 * in the document must come from the contract, not from the binding of the
	 * historical half of the pair, where the parameter is always present: springdoc
	 * derives required from the binding annotation and no annotation can unset it,
	 * so the served document is corrected here. The at parameter exists on one
	 * endpoint only, so its name identifies it. Springdoc also calls the customizer
	 * with the parameters it did not model, such as request bodies, hence the null
	 * check.
	 */
	@Bean
	ParameterCustomizer thePastInstantIsOptional() {
		return (parameter, boundTo) -> {
			if (parameter != null && "at".equals(parameter.getName())) {
				parameter.setRequired(false);
			}
			return parameter;
		};
	}

	@Bean
	OpenAPI walletServiceApi() {
		return new OpenAPI()
				.info(new Info().title("Wallet Service")
						.description("Creates wallets and moves money: deposit, withdraw, transfer, current balance "
								+ "and balance at a past instant. Money-moving requests require an Idempotency-Key "
								+ "header; repeating a request with the same key returns the original result.")
						.version("1.0"))
				.components(new Components().addSchemas("ProblemDetail", problemDetailSchema())
						.addResponses("InvalidWalletBody", problemDetailResponse(
								"The body is missing, malformed or fails validation; problem+json with code INVALID_REQUEST."))
						.addResponses("InvalidTimestamp",
								problemDetailResponse(
										"The at parameter is malformed, has no offset or is in the future; "
												+ "problem+json with code INVALID_TIMESTAMP."))
						.addResponses("InvalidMoneyRequest", problemDetailResponse(
								"The request is invalid; problem+json with code INVALID_REQUEST, INVALID_AMOUNT "
										+ "or MISSING_IDEMPOTENCY_KEY."))
						.addResponses("InvalidTransferRequest", problemDetailResponse(
								"The request is invalid; problem+json with code INVALID_REQUEST, INVALID_AMOUNT, "
										+ "MISSING_IDEMPOTENCY_KEY or SAME_WALLET_TRANSFER."))
						.addResponses("WalletNotFound",
								problemDetailResponse(
										"The wallet does not exist; problem+json with code WALLET_NOT_FOUND."))
						.addResponses("WalletAlreadyExists",
								problemDetailResponse(
										"The user already has a wallet; problem+json with code WALLET_ALREADY_EXISTS."))
						.addResponses("RejectedOperation",
								problemDetailResponse(
										"The operation was rejected; problem+json with code INSUFFICIENT_FUNDS or "
												+ "IDEMPOTENCY_KEY_REUSED."))
						.addResponses("WalletBusy", problemDetailResponse(
								"The wallet lock could not be obtained in time; problem+json with code WALLET_BUSY. "
										+ "The same request can be retried with the same Idempotency-Key.")));
	}

	/**
	 * The shape of every error this API returns: RFC 7807, with the stable code the
	 * caller switches on.
	 */
	private static Schema<?> problemDetailSchema() {
		return new Schema<>().type("object").addProperty("type", new Schema<>().type("string"))
				.addProperty("title", new Schema<>().type("string"))
				.addProperty("status", new Schema<>().type("integer"))
				.addProperty("detail", new Schema<>().type("string"))
				.addProperty("code", new Schema<>().type("string"));
	}

	private static ApiResponse problemDetailResponse(String description) {
		return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
				new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
	}
}
