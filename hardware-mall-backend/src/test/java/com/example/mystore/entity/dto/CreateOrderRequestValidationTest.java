package com.example.mystore.entity.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankItems_shouldViolate() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(java.util.Collections.emptyList());
        req.setAddressId(null);
        req.setLogisticsId(null);

        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);

        assertThat(v).isNotEmpty();
        assertThat(v.stream().map(c -> c.getPropertyPath().toString()))
                .contains("items", "addressId", "logisticsId");
    }

    @Test
    void negativeQuantity_shouldViolate() {
        CreateOrderRequest.CartItem ci = new CreateOrderRequest.CartItem();
        ci.setSkuId(1L);
        ci.setQuantity(0);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(java.util.List.of(ci));
        req.setAddressId(1L);
        req.setLogisticsId(1L);

        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);

        assertThat(v.stream().anyMatch(c -> c.getPropertyPath().toString().contains("quantity"))).isTrue();
    }
}
