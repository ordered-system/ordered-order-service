package pl.dybcio.ordered.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
  public void pollAndPublish() {
    List<OutboxEvent> pending =
        outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
    for (OutboxEvent event : pending) {
      publishSingle(event);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void publishSingle(OutboxEvent event) {
    String topic = resolveTopic(event.getEventType());
    try {
      kafkaTemplate
          .send(topic, event.getId().toString(), event.getPayload())
          .get(5, TimeUnit.SECONDS);

      event.setPublishedAt(Instant.now());
      outboxEventRepository.save(event);
    } catch (Exception e) {
      log.error(
          "Failed to publish outbox event {} ({}): {}",
          event.getId(),
          event.getEventType(),
          e.getMessage());
    }
  }

  private String resolveTopic(String eventType) {
    return switch (eventType) {
      case "OrderPlaced" -> KafkaTopics.ORDER_PLACED;
      case "OrderCancelled" -> KafkaTopics.ORDER_CANCELLED;
      case "OrderDelivered" -> KafkaTopics.ORDER_DELIVERED;
      default -> throw new IllegalStateException("Unknown outbox event type: " + eventType);
    };
  }
}
