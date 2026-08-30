package pl.dybcio.ordered.outbox;

public final class KafkaTopics {
  public static final String ORDER_PLACED = "order-placed";
  public static final String ORDER_CANCELLED = "order-cancelled";
  public static final String ORDER_DELIVERED = "order-delivered";

  private KafkaTopics() {}
}
