package com.pricing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzurePriceDTO {

	private String meterId;
    private String productName;
    private String skuName;
    private String armRegionName;
    private String location;
    private double retailPrice;
    private String currencyCode;
    private String serviceFamily;
    private String unitOfMeasure;
    private String effectiveStartDate;
    private String type;
    private int tierMinimumUnits;
    private int tierMaximumUnits;
    private String skuId;
}
