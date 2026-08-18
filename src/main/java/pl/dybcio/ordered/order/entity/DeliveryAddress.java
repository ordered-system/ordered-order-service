package pl.dybcio.ordered.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAddress {

  @Column(name = "delivery_recipient_name")
  private String recipientName;

  @Column(name = "delivery_phone")
  private String phone;

  @Column(name = "delivery_street")
  private String street;

  @Column(name = "delivery_building_number")
  private String buildingNumber;

  @Column(name = "delivery_apartment_number")
  private String apartmentNumber;

  @Column(name = "delivery_city")
  private String city;

  @Column(name = "delivery_postal_code")
  private String postalCode;

  @Column(name = "delivery_country")
  private String country;
}
