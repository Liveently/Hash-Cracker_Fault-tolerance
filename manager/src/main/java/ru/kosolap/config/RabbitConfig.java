package ru.kosolap.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MarshallingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

@Configuration
public class RabbitConfig {

    public static final String TASK_EXCHANGE = "task_exchange";
    public static final String ROUTING_KEY = "task_routing_key";
    public static final String PROGRESS_ROUTING_KEY = "progress_routing_key";
    public static final String RESULT_ROUTING_KEY = "result_routing_key";

    public static final String TASK_QUEUE = "task_queue";
    public static final String PROGRESS_QUEUE = "progress_queue";
    public static final String RESULT_QUEUE = "result_queue";

    @Bean
    public Queue taskQueue() {
        return new Queue(TASK_QUEUE, true);
    }

    @Bean
    public Queue progressQueue() {
        return new Queue(PROGRESS_QUEUE, true);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue(RESULT_QUEUE, true);
    }

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(TASK_EXCHANGE);
    }

    // Привязка очередей к exchange
    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue()).to(directExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding progressBinding() {
        return BindingBuilder.bind(progressQueue()).to(directExchange()).with(PROGRESS_ROUTING_KEY);
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(resultQueue()).to(directExchange()).with(RESULT_ROUTING_KEY);
    }

    // Конфигурация JAXB и конвертера
    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
            ru.kosolap.json.CrackHashManagerRequest.class,
            ru.kosolap.json.CrackHashWorkerResponse.class // добавим и его
        );
        return marshaller;
    }

    @Bean
    public MarshallingMessageConverter xmlMessageConverter(Jaxb2Marshaller marshaller) {
        MarshallingMessageConverter converter = new MarshallingMessageConverter(marshaller);
        converter.setContentType("application/xml");
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        MarshallingMessageConverter xmlMessageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(xmlMessageConverter);
        return template;
    }
}
