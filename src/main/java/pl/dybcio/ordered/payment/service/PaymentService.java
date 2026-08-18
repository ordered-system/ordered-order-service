package pl.dybcio.ordered.payment.service;

import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.payment.dto.PaymentResult;

public interface PaymentService {
  PaymentResult charge(Order order);
}
