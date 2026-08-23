package com.parko.balance.service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String BALANCE_EXCHANGE = "balance.exchange";

    public static final String TOPUP_QUEUE = "balance.topup.queue";
    public static final String TOPUP_ROUTING_KEY = "balance.topup";

    public static final String CHARGE_QUEUE = "balance.charge.queue";
    public static final String CHARGE_ROUTING_KEY = "balance.charge";

    @Bean
    public DirectExchange balanceExchange() {
        return new DirectExchange(BALANCE_EXCHANGE);
    }

    @Bean
    public Queue topUpQueue() {
        return new Queue(TOPUP_QUEUE, true);
    }

    @Bean
    public Queue chargeQueue() {
        return new Queue(CHARGE_QUEUE, true);
    }

    @Bean
    public Binding topUpBinding(Queue topUpQueue, DirectExchange balanceExchange) {
        return BindingBuilder.bind(topUpQueue).to(balanceExchange).with(TOPUP_ROUTING_KEY);
    }

    @Bean
    public Binding chargeBinding(Queue chargeQueue, DirectExchange balanceExchange) {
        return BindingBuilder.bind(chargeQueue).to(balanceExchange).with(CHARGE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
