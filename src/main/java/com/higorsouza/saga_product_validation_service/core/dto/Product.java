package com.higorsouza.saga_product_validation_service.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product {

    private String code;
    private double unitValue;
}
