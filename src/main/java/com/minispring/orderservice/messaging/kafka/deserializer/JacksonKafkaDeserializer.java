package com.minispring.orderservice.messaging.kafka.deserializer;

import com.minispring.orderservice.messaging.event.PaymentCreatedEvent;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class JacksonKafkaDeserializer implements Deserializer<PaymentCreatedEvent> {

    private final ObjectMapper objectMapper;

    public JacksonKafkaDeserializer() {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    @Override
    public PaymentCreatedEvent deserialize(String s, byte[] bytes) {
        return deserialize(s, null, bytes);
    }

    @Override
    public PaymentCreatedEvent deserialize(String topic, Headers headers, byte[] data) {
        if (data == null || data.length == 0) {
            log.debug("Received empty payload for topic [{}]", topic);
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(data);

            if (node.isString()) {
                String unescapedJson = node.asString();
                return objectMapper.readValue(unescapedJson, PaymentCreatedEvent.class);
            }

            return objectMapper.treeToValue(node, PaymentCreatedEvent.class);

        } catch (JacksonException e) {
            String rawPayload = new String(data, StandardCharsets.UTF_8);
            log.error("Failed to parse JSON from topic [{}]. Raw payload: {}", topic, rawPayload, e);
            throw new SerializationException("Error deserializing JSON message for topic: " + topic, e);
        } catch (Exception e) {
            log.error("Unexpected error during deserialization from topic [{}]", topic, e);
            throw new SerializationException("Unexpected deserialization error", e);
        }
    }
}
