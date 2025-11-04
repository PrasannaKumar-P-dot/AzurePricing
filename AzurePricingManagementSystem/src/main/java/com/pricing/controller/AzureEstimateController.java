package com.pricing.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pricing.model.EstimationRequestDTO;
import com.pricing.service.AzureEstimateService;

@RestController
@RequestMapping("/api/azure/estimate")
public class AzureEstimateController {

	private final AzureEstimateService estimateService;

    public AzureEstimateController(AzureEstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @GetMapping("/products")
    public ResponseEntity<List<String>> getAllProducts() {
        return ResponseEntity.ok(estimateService.getAllProducts());
    }

    @GetMapping("/products/{product}/regions")
    public ResponseEntity<List<String>> getRegions(@PathVariable("product") String product) {
        return ResponseEntity.ok(estimateService.getRegionsForProduct(product));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> getEstimate(@RequestBody EstimationRequestDTO request) {
        return ResponseEntity.ok(estimateService.estimateCost(request));
    }
    
    @GetMapping("/ping")
    public String ping() {
        return "Azure Estimate API is alive!";
    }
}
