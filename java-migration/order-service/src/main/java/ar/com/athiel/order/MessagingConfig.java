package ar.com.athiel.order;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    static final String EXCHANGE = "athiel.payments";
    static final String ROUTING_KEY = "payment.approved";

    @Bean
    TopicExchange paymentsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue paymentApprovedQueue() {
        return new Queue("order-service.payment-approved", true);
    }

    @Bean
    Binding paymentApprovedBinding(Queue paymentApprovedQueue, TopicExchange paymentsExchange) {
        return BindingBuilder.bind(paymentApprovedQueue).to(paymentsExchange).with(ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
