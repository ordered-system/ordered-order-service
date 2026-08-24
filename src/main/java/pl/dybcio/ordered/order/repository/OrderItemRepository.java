package pl.dybcio.ordered.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.dybcio.ordered.order.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

  @Query(
      """
    select count(oi) > 0
    from OrderItem oi
    where oi.order.buyerId = :buyerId
              and oi.productId = :productId
              and oi.order.status = pl.dybcio.ordered.order.entity.OrderStatus.DELIVERED
            """)
  boolean existsPurchaseByBuyerAndProduct(
      @Param("buyerId") Long buyerId, @Param("productId") Long productId);
}
