package pl.dybcio.ordered.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(@NotNull @Valid AddressSnapshot deliveryAddress) {}
