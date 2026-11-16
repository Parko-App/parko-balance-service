package com.parko.balance.service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
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

    public static final String PAYMENT_CONFIRMED_QUEUE = "balance.payment-confirmed.queue";
    public static final String PAYMENT_CONFIRMED_ROUTING_KEY = "balance.payment.confirmed";

    public static final String PAYMENT_FAILED_QUEUE = "balance.payment-failed.queue";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "balance.payment.failed";

    public static final String TOPUP_PREFERENCE_CREATED_QUEUE = "balance.topup-preference-created.queue";
    public static final String TOPUP_PREFERENCE_CREATED_ROUTING_KEY = "balance.topup.preference.created";

    public static final String DEAD_LETTER_EXCHANGE = "balance.dlx.exchange";
    public static final String TOPUP_DLQ = "balance.topup.dlq";
    public static final String CHARGE_DLQ = "balance.charge.dlq";
    public static final String PAYMENT_CONFIRMED_DLQ = "balance.payment-confirmed.dlq";
    public static final String PAYMENT_FAILED_DLQ = "balance.payment-failed.dlq";
    public static final String TOPUP_PREFERENCE_CREATED_DLQ = "balance.topup-preference-created.dlq";

    public static final long MESSAGE_TTL_MS = 86_400_000L;

    @Bean
    public DirectExchange balanceExchange() {
        return new DirectExchange(BALANCE_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue topUpQueue() {
        return QueueBuilder.durable(TOPUP_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TOPUP_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue chargeQueue() {
        return QueueBuilder.durable(CHARGE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CHARGE_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue paymentConfirmedQueue() {
        return QueueBuilder.durable(PAYMENT_CONFIRMED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_CONFIRMED_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_FAILED_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue topUpPreferenceCreatedQueue() {
        return QueueBuilder.durable(TOPUP_PREFERENCE_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TOPUP_PREFERENCE_CREATED_DLQ)
                .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Queue topUpDeadLetterQueue() {
        return new Queue(TOPUP_DLQ, true);
    }

    @Bean
    public Queue chargeDeadLetterQueue() {
        return new Queue(CHARGE_DLQ, true);
    }

    @Bean
    public Queue paymentConfirmedDeadLetterQueue() {
        return new Queue(PAYMENT_CONFIRMED_DLQ, true);
    }

    @Bean
    public Queue topUpPreferenceCreatedDeadLetterQueue() {
        return new Queue(TOPUP_PREFERENCE_CREATED_DLQ, true);
    }

    @Bean
    public Queue paymentFailedDeadLetterQueue() {
        return new Queue(PAYMENT_FAILED_DLQ, true);
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
    public Binding paymentConfirmedBinding(Queue paymentConfirmedQueue, DirectExchange balanceExchange) {
        return BindingBuilder.bind(paymentConfirmedQueue).to(balanceExchange).with(PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, DirectExchange balanceExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(balanceExchange).with(PAYMENT_FAILED_ROUTING_KEY);
    }

    @Bean
    public Binding topUpPreferenceCreatedBinding(Queue topUpPreferenceCreatedQueue, DirectExchange balanceExchange) {
        return BindingBuilder.bind(topUpPreferenceCreatedQueue).to(balanceExchange).with(TOPUP_PREFERENCE_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding topUpDeadLetterBinding(Queue topUpDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(topUpDeadLetterQueue).to(deadLetterExchange).with(TOPUP_DLQ);
    }

    @Bean
    public Binding chargeDeadLetterBinding(Queue chargeDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(chargeDeadLetterQueue).to(deadLetterExchange).with(CHARGE_DLQ);
    }

    @Bean
    public Binding paymentConfirmedDeadLetterBinding(Queue paymentConfirmedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentConfirmedDeadLetterQueue).to(deadLetterExchange).with(PAYMENT_CONFIRMED_DLQ);
    }

    @Bean
    public Binding topUpPreferenceCreatedDeadLetterBinding(Queue topUpPreferenceCreatedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(topUpPreferenceCreatedDeadLetterQueue).to(deadLetterExchange).with(TOPUP_PREFERENCE_CREATED_DLQ);
    }

    @Bean
    public Binding paymentFailedDeadLetterBinding(Queue paymentFailedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentFailedDeadLetterQueue).to(deadLetterExchange).with(PAYMENT_FAILED_DLQ);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
