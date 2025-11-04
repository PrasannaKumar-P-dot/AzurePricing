package com.pricing.model;

import lombok.Data;

@Data
public class EstimationRequestDTO {

	private String productName;
    private String region;
    private int quantity;
}
