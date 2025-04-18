package ru.kosolap.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MarshallingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import ru.kosolap.json.CrackHashManagerRequest;
import ru.kosolap.json.CrackHashWorkerResponse;
import java.util.HashMap;
import java.util.Map;



@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String TASK_EXCHANGE = "task_exchange";
    public static final String ROUTING_KEY = "task_routing_key";
    public static final String TASK_QUEUE = "task_queue";
    public static final String PROGRESS_QUEUE = "progress_queue";
    public static final String RESULT_QUEUE = "result_queue";

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(TASK_EXCHANGE);
    }

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
    public Binding binding() {
        return BindingBuilder.bind(taskQueue()).to(directExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
    Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
    marshaller.setClassesToBeBound(
        CrackHashManagerRequest.class,
        CrackHashWorkerResponse.class
    );
    Map<String, Object> props = new HashMap<>();
    props.put(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, true);
    props.put(javax.xml.bind.Marshaller.JAXB_SCHEMA_LOCATION, 
            "http://ccfit.nsu.ru/schema/crack-hash-request");
    marshaller.setMarshallerProperties(props);
    return marshaller;
    }


    @Bean
    public MarshallingMessageConverter messageConverter(Jaxb2Marshaller marshaller) {
        MarshallingMessageConverter converter = new MarshallingMessageConverter(marshaller, marshaller);
        converter.setContentType("application/xml"); 
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, 
                                       MarshallingMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setChannelTransacted(true);
        rabbitTemplate.setReplyTimeout(60000);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MarshallingMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setMissingQueuesFatal(false); 
        factory.setAutoStartup(true);
        factory.setPrefetchCount(1);
        return factory;
    }

    

}

