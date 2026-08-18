package pl.dybcio.ordered.outbox.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
