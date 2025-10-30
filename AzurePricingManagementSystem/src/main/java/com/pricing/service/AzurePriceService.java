package com.pricing.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.model.AzureFetchResponseDTO;
import com.pricing.model.AzurePriceDTO;

@Service
public class AzurePriceService {

    @Value("${azure.pricing.start.url}")
    private String defaultUrl;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.output.folder}")
    private String outputFolder;

    private final AmazonS3 amazonS3;
    private final ObjectMapper mapper = new ObjectMapper();

    public AzurePriceService(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }

    /**
     * Fetch all Azure pricing pages, convert to CSV, and upload to S3
     */
    public AzureFetchResponseDTO fetchAndUploadData(String customUrl) {
        List<AzurePriceDTO> allPrices = new ArrayList<>();
        String currentUrl = (customUrl != null && !customUrl.isEmpty()) ? customUrl : defaultUrl;

        try {
            while (currentUrl != null && !currentUrl.isEmpty()) {
                JsonNode root = mapper.readTree(new URL(currentUrl));

                // Parse current page
                JsonNode items = root.get("Items");
                if (items != null && items.isArray()) {
                    for (JsonNode node : items) {
                        AzurePriceDTO price = mapper.treeToValue(node, AzurePriceDTO.class);
                        allPrices.add(price);
                    }
                }

                // Check for next page
                JsonNode nextLink = root.get("NextPageLink");
                currentUrl = (nextLink != null && !nextLink.isNull()) ? nextLink.asText() : null;
            }

            // ✅ Generate CSV file from all collected records
            File csvFile = createCsvFile(allPrices);

            // ✅ Upload to AWS S3
            String fileName = outputFolder + "/azure_prices_" + System.currentTimeMillis() + ".csv";
            amazonS3.putObject(new PutObjectRequest(bucketName, fileName, csvFile));

            return new AzureFetchResponseDTO(
                    "Successfully uploaded CSV to S3",
                    "s3://" + bucketName + "/" + fileName,
                    allPrices.size()
            );

        } catch (Exception e) {
            throw new RuntimeException("Error fetching or uploading Azure data", e);
        }
    }

    /**
     * ✅ Helper method to create a temporary CSV file
     */
    private File createCsvFile(List<AzurePriceDTO> prices) throws IOException {
        File file = File.createTempFile("azure_prices_", ".csv");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("MeterId,ProductName,SKU,Region,Location,Price,Currency,ServiceFamily,Unit,EffectiveDate,Type\n");
            for (AzurePriceDTO p : prices) {
                writer.write(String.join(",",
                        nullSafe(p.getMeterId()), nullSafe(p.getProductName()), nullSafe(p.getSkuName()),
                        nullSafe(p.getArmRegionName()), nullSafe(p.getLocation()),
                        String.valueOf(p.getRetailPrice()), nullSafe(p.getCurrencyCode()),
                        nullSafe(p.getServiceFamily()), nullSafe(p.getUnitOfMeasure()),
                        nullSafe(p.getEffectiveStartDate()), nullSafe(p.getType())) + "\n");
            }
        }
        return file;
    }

    /**
     * Helper to prevent null or comma errors in CSV
     */
    private String nullSafe(String value) {
        return value == null ? "" : value.replace(",", " ");
    }
}
