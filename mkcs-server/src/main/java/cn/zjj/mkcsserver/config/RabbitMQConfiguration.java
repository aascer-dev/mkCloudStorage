package cn.zjj.mkcsserver.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * @author 34978
 */
@Configuration
public class RabbitMQConfiguration {

    public static final String VERIFICATION_CODE_EXCHANGE = "verification.code.exchange";
    public static final String VERIFICATION_CODE_QUEUE = "verification.code.queue";
    public static final String VERIFICATION_CODE_ROUTING_KEY = "verification.code.send";

    /**
     * 交换机
     */
    @Bean
    public DirectExchange verificationCodeExchange() {
        return ExchangeBuilder.directExchange(VERIFICATION_CODE_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 队列
     */
    @Bean
    public Queue verificationCodeQueue() {
        return QueueBuilder.durable(VERIFICATION_CODE_QUEUE)
                .build();
    }

    /**
     * 绑定
     */
    @Bean
    public Binding verificationCodeBinding(Queue verificationCodeQueue, DirectExchange verificationCodeExchange) {
        return BindingBuilder.bind(verificationCodeQueue)
                .to(verificationCodeExchange)
                .with(VERIFICATION_CODE_ROUTING_KEY);
    }

    /**
     * 消息转换器（JSON）
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    /**
     * 监听器容器工厂配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(1);
        return factory;
    }
}
