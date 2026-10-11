package com.recargapay.wallet.config;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Money;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

	@Bean
	public JsonMapperBuilderCustomizer moneyJson() {
		SimpleModule moneyModule = new SimpleModule();
		moneyModule.addDeserializer(Money.class, new ValueDeserializer<Money>() {
			@Override
			public Money deserialize(JsonParser parser, DeserializationContext context) {
				return Money.of(parser.getDecimalValue().toPlainString());
			}
		});
		moneyModule.addSerializer(Money.class, new ValueSerializer<Money>() {
			@Override
			public void serialize(Money money, JsonGenerator generator, SerializationContext context) {
				generator.writeNumber(money.toString());
			}
		});
		moneyModule.addSerializer(Balance.class, new ValueSerializer<Balance>() {
			@Override
			public void serialize(Balance balance, JsonGenerator generator, SerializationContext context) {
				generator.writeNumber(balance.toString());
			}
		});
		return builder -> builder.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
				.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).addModule(moneyModule);
	}
}
