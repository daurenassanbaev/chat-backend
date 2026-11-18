package org.messenger.testsupport;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

public final class KafkaRecordReader implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaRecordReader() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaRedisIntegrationTest.KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumer = new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer());
    }

    public String awaitValue(String topic, Predicate<String> predicate, Duration timeout) {
        consumer.subscribe(List.of(topic));
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                if (predicate.test(record.value())) {
                    return record.value();
                }
            }
        }
        throw new AssertionError("No matching record received from topic " + topic + " within " + timeout);
    }

    @Override
    public void close() {
        consumer.close(Duration.ofSeconds(2));
    }
}
