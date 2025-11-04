package com.pricing.service;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.model.AzurePriceDTO;
import com.pricing.model.EstimationRequestDTO;

@Service
public class AzureEstimateService {

    @Value("${azure.retail.url}")
    private String baseUrl;

    @Value("${azure.retail.source:azure}") // can be 'azure' or 's3'
    private String sourceType;

    @Value("${aws.s3.offline.url:}")
    private String s3OfflineUrl;

    @Value("${azure.api.max.pages:3}")
    private int maxPages;

    private final ObjectMapper mapper = new ObjectMapper();

    private List<AzurePriceDTO> fetchAzurePrices(String product, String region) {
        List<AzurePriceDTO> prices = new ArrayList<>();
        try {
            String url;

            // ✅ S3 fallback logic
            if ("s3".equalsIgnoreCase(sourceType)) {
                try {
                    System.out.println("🔹 Fetching from S3: " + s3OfflineUrl);
                    JsonNode root = mapper.readTree(new URL(s3OfflineUrl));
                    return parseAzureItems(root);
                } catch (Exception s3ex) {
                    System.err.println("⚠️ S3 fetch failed, falling back to Azure API...");
                }
            }

            url = buildUrl(product, region);
            int pageCount = 0;

            while (url != null && pageCount < maxPages) {
                System.out.println("🔹 Fetching page " + (pageCount + 1) + " from Azure...");
                JsonNode root = mapper.readTree(new URL(url));
                prices.addAll(parseAzureItems(root));

                JsonNode next = root.get("NextPageLink");
                url = (next != null && !next.isNull()) ? next.asText() : null;
                pageCount++;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Azure retail prices: " + e.getMessage(), e);
        }
        return prices;
    }

    private List<AzurePriceDTO> parseAzureItems(JsonNode root) throws Exception {
        List<AzurePriceDTO> prices = new ArrayList<>();
        JsonNode items;
        if (root.has("Items")) {
            items = root.get("Items");
        } else if (root.isArray()) {
            items = root;
        } else {
            throw new RuntimeException("Unsupported JSON structure");
        }

        for (JsonNode item : items) {
            AzurePriceDTO dto = mapper.treeToValue(item, AzurePriceDTO.class);
            prices.add(dto);
        }
        return prices;
    }

    private String buildUrl(String serviceName, String region) {
        StringBuilder sb = new StringBuilder(baseUrl);
        List<String> filters = new ArrayList<>();

        if (serviceName != null && !serviceName.isEmpty()) {
            filters.add("productName eq '" + serviceName + "'");
        }
        if (region != null && !region.isEmpty()) {
            filters.add("armRegionName eq '" + region + "'");
        }

        if (!filters.isEmpty()) {
            String filterQuery = String.join(" and ", filters);
            String encodedFilter = URLEncoder.encode(filterQuery, StandardCharsets.UTF_8);
            sb.append("?$filter=").append(encodedFilter);
        }

        return sb.toString();
    }

    public List<String> getAllProducts() {
        List<AzurePriceDTO> prices = fetchAzurePrices(null, null);
        Set<String> products = new TreeSet<>();
        for (AzurePriceDTO dto : prices) {
            if (dto.getProductName() != null) {
                products.add(dto.getProductName());
            }
        }
        return new ArrayList<>(products);
    }

    public List<String> getRegionsForProduct(String product) {
        List<AzurePriceDTO> prices = fetchAzurePrices(product, null);
        Set<String> regions = new TreeSet<>();
        for (AzurePriceDTO dto : prices) {
            if (dto.getArmRegionName() != null) {
                regions.add(dto.getArmRegionName());
            }
        }
        return new ArrayList<>(regions);
    }

    public Map<String, Object> estimateCost(EstimationRequestDTO request) {
        List<AzurePriceDTO> prices = fetchAzurePrices(request.getProductName(), request.getRegion());
        Optional<AzurePriceDTO> cheapest = prices.stream()
                .filter(p -> p.getRetailPrice() > 0)
                .min(Comparator.comparingDouble(AzurePriceDTO::getRetailPrice));

        Map<String, Object> result = new LinkedHashMap<>();
        if (cheapest.isPresent()) {
            AzurePriceDTO dto = cheapest.get();
            double total = dto.getRetailPrice() * request.getQuantity();

            result.put("product", dto.getProductName());
            result.put("region", dto.getArmRegionName());
            result.put("unitPrice", dto.getRetailPrice());
            result.put("quantity", request.getQuantity());
            result.put("currency", dto.getCurrencyCode());
            result.put("estimatedCost", total);
        } else {
            result.put("message", "No pricing data available for this selection.");
        }

        return result;
    }
}
