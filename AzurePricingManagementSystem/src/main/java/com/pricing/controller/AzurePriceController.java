package com.pricing.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pricing.model.AzureFetchRequestDTO;
import com.pricing.model.AzureFetchResponseDTO;
import com.pricing.service.AzurePriceProcessorService;
import com.pricing.service.AzurePriceService;

@RestController
@ RequestMapping("/api/azure")
public class AzurePriceController {

	@Autowired
    private AzurePriceService service;
	
	@Autowired
    private AzurePriceProcessorService azurePriceProcessorService;

    // 1️⃣ Trigger fetch and upload (optionally with custom URL)
    @PostMapping("/fetch-upload")
    public ResponseEntity<AzureFetchResponseDTO> fetchAndUpload(@RequestBody(required = false) AzureFetchRequestDTO request) {
        String url = (request != null) ? request.getStartUrl() : null;
        AzureFetchResponseDTO response = service.fetchAndUploadData(url);
        return ResponseEntity.ok(response);
    }

    // 2️⃣ Simple GET endpoint for testing
    @GetMapping("/status")
    public String status() {
        return "Azure Pricing Fetch Service is running!";
    }
    
    /**
     * 🔹 Trigger the processing manually via API
     * Example: GET http://localhost:8080/api/azure/pricing/process
     */
    @GetMapping("/process")
    public ResponseEntity<String> processAndUploadPricing() {
        try {
            String s3Url = azurePriceProcessorService.processAndUploadPricingSheet();
            return ResponseEntity.ok("✅ Processed pricing uploaded to: " + s3Url);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("❌ Failed to process pricing sheet: " + e.getMessage());
        }
    }
    
    /**
     * 🔹 (Optional) Endpoint to refresh the cache only.
     * Example: GET http://localhost:8080/api/azure/pricing/cache-refresh
     */
    @GetMapping("/cache-refresh")
    public ResponseEntity<String> refreshCache() {
        try {
            azurePriceProcessorService.processAndUploadPricingSheet();
            return ResponseEntity.ok("✅ Cache refreshed and processed pricing uploaded.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("❌ Failed to refresh cache: " + e.getMessage());
        }
    }

}
