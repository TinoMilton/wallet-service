package com.recargapay.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recargapay.wallet.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MoneyTest {

	@Test
	@DisplayName("BR-03: a zero amount is rejected")
	void zeroIsRejected() {
		String amount = "0.00";

		Throwable failure = catchThrowable(() -> Money.of(amount));

		assertThat(failure).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("BR-03: a negative amount is rejected")
	void negativeIsRejected() {
		String amount = "-1.00";

		Throwable failure = catchThrowable(() -> Money.of(amount));

		assertThat(failure).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("BR-04: an amount with three decimals is rejected rather than rounded")
	void threeDecimalsAreRejected() {
		String amount = "1.001";

		Throwable failure = catchThrowable(() -> Money.of(amount));

		assertThat(failure).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("BR-04: an amount with fewer than two decimals is represented at scale two")
	void normalizesScale() {
		String amount = "10";

		Money money = Money.of(amount);

		assertThat(money.toString()).isEqualTo("10.00");
	}

	@Test
	@DisplayName("BR-04: amounts with different input scales compare by value")
	void comparesByValue() {
		Money whole = Money.of("10");
		Money cents = Money.of("10.00");

		int comparison = whole.compareTo(cents);

		assertThat(comparison).isZero();
	}

	@Test
	@DisplayName("BR-04: JSON amounts are read as exact decimals without rounding")
	void jsonAmountRejectsExtraPrecision() {
		JsonMapper.Builder builder = JsonMapper.builder();
		new JacksonConfig().moneyJson().customize(builder);
		JsonMapper mapper = builder.build();

		Throwable failure = catchThrowable(() -> mapper.readValue("1.001", Money.class));

		assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("two decimal places");
	}

	@Test
	@DisplayName("BR-04: JSON amounts are written as numbers with two decimal places")
	void jsonAmountWritesTwoDecimals() {
		JsonMapper.Builder builder = JsonMapper.builder();
		new JacksonConfig().moneyJson().customize(builder);
		JsonMapper mapper = builder.build();
		Money amount = Money.of("10");

		String json = mapper.writeValueAsString(amount);

		assertThat(json).isEqualTo("10.00");
	}
}
