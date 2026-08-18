package pl.dybcio.ordered.order.dto;

import jakarta.validation.constraints.NotBlank;
import pl.dybcio.ordered.order.entity.DeliveryAddress;

public record AddressSnapshot(
    @NotBlank String recipientName,
    @NotBlank String phone,
    @NotBlank String street,
    @NotBlank String buildingNumber,
    String apartmentNumber,
    @NotBlank String city,
    @NotBlank String postalCode,
    @NotBlank String country) {

  public DeliveryAddress toEmbeddable() {
    return DeliveryAddress.builder()
        .recipientName(recipientName)
        .phone(phone)
        .street(street)
        .buildingNumber(buildingNumber)
        .apartmentNumber(apartmentNumber)
        .city(city)
        .postalCode(postalCode)
        .country(country)
        .build();
  }
}
