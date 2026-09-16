package com.dp.plat.lowcode.engine.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * MQ 连接器执行器（批次4-T5）。
 *
 * <p>支持 RabbitMQ 和 Kafka 两种消息中间件，提供 PRODUCE（发送）和 CONSUME（消费）两种操作。</p>
 *
 * <p>config JSON 结构：
 * <pre>{@code
 * {
 *   "mqType": "RABBITMQ|KAFKA",
 *   "operation": "PRODUCE|CONSUME",
 *   "host": "localhost",
 *   "port": 5672,
 *   "username": "guest",
 *   "password": "guest",
 *   // RabbitMQ PRODUCE: exchange, routingKey, message
 *   // RabbitMQ CONSUME: queue, maxMessages, autoAck
 *   // Kafka: bootstrapServers, topic, groupId
 *   // Kafka PRODUCE: message, key
 *   // Kafka CONSUME: maxMessages, timeoutMillis
 *   "timeoutMillis": 5000
 * }
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqConnectorExecutor {

    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public ConnectorResult execute(String configJson, Map<String, Object> params) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            String mqType = ((String) config.getOrDefault("mqType", "RABBITMQ")).toUpperCase();
            String operation = ((String) config.getOrDefault("operation", "PRODUCE")).toUpperCase();

            return switch (mqType) {
                case "RABBITMQ" -> executeRabbitMq(config, operation, params);
                case "KAFKA" -> executeKafka(config, operation, params);
                default -> ConnectorResult.error(400, "不支持的 MQ 类型: " + mqType);
            };
        } catch (Exception e) {
            log.error("MQ 连接器执行失败", e);
            return ConnectorResult.error(500, e.getMessage());
        }
    }

    // ==================== RabbitMQ ====================

    @SuppressWarnings("unchecked")
    private ConnectorResult executeRabbitMq(Map<String, Object> config, String operation,
                                              Map<String, Object> params) {
        String host = (String) config.getOrDefault("host", "localhost");
        int port = ((Number) config.getOrDefault("port", 5672)).intValue();
        String username = (String) config.getOrDefault("username", "guest");
        String password = (String) config.getOrDefault("password", "guest");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setConnectionTimeout(((Number) config.getOrDefault("timeoutMillis", 5000)).intValue());

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            if ("PRODUCE".equals(operation)) {
                String exchange = (String) config.getOrDefault("exchange", "");
                String routingKey = (String) config.getOrDefault("routingKey", "");
                String message = resolveMessage(config, params);
                channel.basicPublish(exchange, routingKey, null,
                        message.getBytes(StandardCharsets.UTF_8));
                log.info("RabbitMQ 消息已发送: exchange={}, routingKey={}", exchange, routingKey);
                return ConnectorResult.ok(Map.of(
                        "operation", "PRODUCE",
                        "exchange", exchange,
                        "routingKey", routingKey,
                        "messageLength", message.length()));
            } else {
                // CONSUME
                String queue = (String) config.get("queue");
                if (queue == null) {
                    return ConnectorResult.error(400, "CONSUME 操作需指定 queue");
                }
                int maxMessages = ((Number) config.getOrDefault("maxMessages", 10)).intValue();
                boolean autoAck = Boolean.TRUE.equals(config.get("autoAck"));
                List<Map<String, Object>> messages = new ArrayList<>();
                for (int i = 0; i < maxMessages; i++) {
                    GetResponse response = channel.basicGet(queue, autoAck);
                    if (response == null) break;
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("body", new String(response.getBody(), StandardCharsets.UTF_8));
                    msg.put("deliveryTag", response.getEnvelope().getDeliveryTag());
                    msg.put("routingKey", response.getEnvelope().getRoutingKey());
                    messages.add(msg);
                }
                return ConnectorResult.ok(Map.of(
                        "operation", "CONSUME",
                        "queue", queue,
                        "messageCount", messages.size(),
                        "messages", messages));
            }
        } catch (Exception e) {
            return ConnectorResult.error(500, "RabbitMQ 操作失败: " + e.getMessage());
        }
    }

    // ==================== Kafka ====================

    @SuppressWarnings("unchecked")
    private ConnectorResult executeKafka(Map<String, Object> config, String operation,
                                           Map<String, Object> params) {
        String bootstrapServers = (String) config.getOrDefault("bootstrapServers", "localhost:9092");
        String topic = (String) config.get("topic");
        if (topic == null) {
            return ConnectorResult.error(400, "Kafka 操作需指定 topic");
        }

        if ("PRODUCE".equals(operation)) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "1");

            String message = resolveMessage(config, params);
            String key = (String) config.get("key");
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);
                producer.send(record).get();
                log.info("Kafka 消息已发送: topic={}, key={}", topic, key);
                return ConnectorResult.ok(Map.of(
                        "operation", "PRODUCE",
                        "topic", topic,
                        "key", key != null ? key : "",
                        "messageLength", message.length()));
            } catch (Exception e) {
                return ConnectorResult.error(500, "Kafka 发送失败: " + e.getMessage());
            }
        } else {
            // CONSUME
            String groupId = (String) config.getOrDefault("groupId", "lowcode-connector");
            int maxMessages = ((Number) config.getOrDefault("maxMessages", 10)).intValue();
            long timeoutMillis = ((Number) config.getOrDefault("timeoutMillis", 5000L)).longValue();

            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(List.of(topic));
                List<Map<String, Object>> messages = new ArrayList<>();
                long deadline = System.currentTimeMillis() + timeoutMillis;
                while (messages.size() < maxMessages && System.currentTimeMillis() < deadline) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        Map<String, Object> msg = new HashMap<>();
                        msg.put("topic", record.topic());
                        msg.put("partition", record.partition());
                        msg.put("offset", record.offset());
                        msg.put("key", record.key());
                        msg.put("value", record.value());
                        messages.add(msg);
                        if (messages.size() >= maxMessages) break;
                    }
                }
                return ConnectorResult.ok(Map.of(
                        "operation", "CONSUME",
                        "topic", topic,
                        "groupId", groupId,
                        "messageCount", messages.size(),
                        "messages", messages));
            } catch (Exception e) {
                return ConnectorResult.error(500, "Kafka 消费失败: " + e.getMessage());
            }
        }
    }

    // ==================== 辅助 ====================

    /**
     * 解析消息内容：优先从 params.message 取，其次从 config.message 取。
     */
    private String resolveMessage(Map<String, Object> config, Map<String, Object> params) {
        Object msg = params != null ? params.get("message") : null;
        if (msg == null) {
            msg = config.get("message");
        }
        if (msg == null) {
            msg = "";
        }
        return msg instanceof String s ? s : msg.toString();
    }
}
