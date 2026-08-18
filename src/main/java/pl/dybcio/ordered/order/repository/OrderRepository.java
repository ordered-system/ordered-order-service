package pl.dybcio.ordered.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
  Page<Order> findByBuyerId(Long buyerId, Pageable pageable);
}
