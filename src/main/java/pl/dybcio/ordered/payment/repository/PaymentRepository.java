package pl.dybcio.ordered.payment.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByOrderId(Long orderId);

  List<Payment> findByStatusAndRetryCountLessThan(PaymentStatus status, int maxRetryCount);

  List<Payment> findByStatusAndRetryCountGreaterThanEqual(PaymentStatus status, int maxRetryCount);
}
