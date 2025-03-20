package bumblebee.xchangepass.global.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue transactionQueue() {
        return new Queue("transaction-status-queue", true);
    }
}