package pl.dybcio.ordered.common.exception;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.dybcio.ordered.order.service.CheckoutReservationException;
import pl.dybcio.ordered.order.service.InvalidOrderStatusTransitionException;
import pl.dybcio.ordered.order.service.OrderNotFoundException;
import pl.dybcio.ordered.order.service.OrderStatusChangeNotAllowedException;
import pl.dybcio.ordered.payment.service.PaymentProcessingException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Order not found");
    return problem;
  }

  @ExceptionHandler(InvalidOrderStatusTransitionException.class)
  public ProblemDetail handleInvalidTransition(InvalidOrderStatusTransitionException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Invalid order status transition");
    return pd;
  }

  @ExceptionHandler(OrderStatusChangeNotAllowedException.class)
  public ProblemDetail handleStatusChangeNotAllowed(OrderStatusChangeNotAllowedException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    pd.setTitle("Order status change not allowed");
    return pd;
  }

  @ExceptionHandler(PaymentProcessingException.class)
  public ProblemDetail handlePaymentProcessing(PaymentProcessingException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    pd.setTitle("Payment processing failed");
    return pd;
  }

  @ExceptionHandler(CheckoutReservationException.class)
  public ProblemDetail handleCheckoutReservation(CheckoutReservationException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Could not reserve cart for checkout");
    return pd;
  }
}
