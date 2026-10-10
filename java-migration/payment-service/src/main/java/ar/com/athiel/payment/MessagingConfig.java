package ar.com.athiel.payment;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String EXCHANGE = "athiel.payments";
    public static final String ROUTING_KEY = "payment.approved";

    @Bean
    TopicExchange paymentsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
