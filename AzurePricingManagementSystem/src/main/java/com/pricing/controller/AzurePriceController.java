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
import com.pricing.service.AzurePriceService;

@RestController
@ RequestMapping("/api/azure")
public class AzurePriceController {

	@Autowired
    private AzurePriceService service;

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

}
