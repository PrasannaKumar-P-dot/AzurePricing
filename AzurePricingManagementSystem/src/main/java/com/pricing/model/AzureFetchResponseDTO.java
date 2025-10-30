package com.pricing.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AzureFetchResponseDTO {

	private String message;
    private String s3FilePath;
    private long recordCount;
}
