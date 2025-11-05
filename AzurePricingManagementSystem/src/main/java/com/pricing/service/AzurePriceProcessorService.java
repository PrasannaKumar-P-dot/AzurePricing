package com.pricing.service;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.model.AzurePriceDTO;

@Service
public class AzurePriceProcessorService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.processed.folder}")
    private String processedFolder;

    @Value("${aws.s3.offline.url}")
    private String sourceS3Url;

    @Value("${local.cache.file:cache/azure_prices_cache.json}")
    private String localCacheFilePath;

    public AzurePriceProcessorService(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }

    public String processAndUploadPricingSheet() {
        try {
            // 🔹 Step 1: Load pricing data (from cache or S3)
            List<AzurePriceDTO> prices = loadPricingData();

            // 🔹 Step 2: Transform and create processed CSV
            File processedCsv = transformAndWriteCsv(prices);

            // 🔹 Step 3: Upload to S3
            String fileName = processedFolder + "/azure_prices_processed_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv";

            amazonS3.putObject(bucketName, fileName, processedCsv);
            System.out.println("✅ Uploaded processed pricing sheet to S3: s3://" + bucketName + "/" + fileName);

            return "s3://" + bucketName + "/" + fileName;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process pricing sheet", e);
        }
    }

    /**
     * 🔹 Load data from cache or S3 (auto-detects JSON or CSV)
     */
    private List<AzurePriceDTO> loadPricingData() throws Exception {
        File cacheFile = new File(localCacheFilePath);

        // ✅ Step 1: Try using cached data if exists
        if (cacheFile.exists() && Files.size(Paths.get(localCacheFilePath)) > 1000) {
            System.out.println("⚡ Using local cache file: " + localCacheFilePath);
            return parseFile(cacheFile.getPath());
        }

        // ✅ Step 2: Otherwise, download from S3
        System.out.println("🔹 Downloading from S3: " + sourceS3Url);
        String ext = getFileExtension(sourceS3Url);
        String cachePath = localCacheFilePath.replace(".json", "." + ext);

        Files.createDirectories(Paths.get(new File(cachePath).getParent()));
        try (InputStream in = new URL(sourceS3Url).openStream()) {
            System.out.println("♻️ Refreshing cache file: " + cachePath);
            Files.copy(in, Paths.get(cachePath), StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("✅ Cached data locally at: " + cachePath);
        return parseFile(cachePath);
    }

    /**
     * 🔹 Parse file based on extension (.json or .csv)
     */
    private List<AzurePriceDTO> parseFile(String filePath) throws Exception {
        if (filePath.endsWith(".json")) {
            System.out.println("📘 Parsing JSON file...");
            JsonNode root = mapper.readTree(new File(filePath));
            JsonNode items = root.has("Items") ? root.get("Items") : root;
            List<AzurePriceDTO> list = new ArrayList<>();
            for (JsonNode node : items) {
                list.add(mapper.treeToValue(node, AzurePriceDTO.class));
            }
            return list;
        } else if (filePath.endsWith(".csv")) {
            System.out.println("📗 Parsing CSV file...");
            List<AzurePriceDTO> list = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                boolean headerSkipped = false;

                while ((line = br.readLine()) != null) {
                    // skip the header line
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }

                    String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1); // safe split (handles commas inside quotes)

                    AzurePriceDTO dto = new AzurePriceDTO();
                    dto.setMeterId(get(cols, 0));
                    dto.setProductName(get(cols, 1));
                    dto.setSkuName(get(cols, 2));
                    dto.setArmRegionName(get(cols, 3));
                    dto.setLocation(get(cols, 4));
                    dto.setRetailPrice(parseDouble(get(cols, 5)));
                    dto.setCurrencyCode(get(cols, 6));
                    dto.setServiceFamily(get(cols, 7));
                    dto.setUnitOfMeasure(get(cols, 8));
                    dto.setEffectiveStartDate(get(cols, 9));
                    dto.setType(get(cols, 10));

                    list.add(dto);
                }
            }
            return list;
        } else {
            throw new RuntimeException("Unsupported file format: " + filePath);
        }
    }

    private String get(String[] cols, int i) {
        return (i < cols.length) ? cols[i].replace("\"", "").trim() : "";
    }
    
    private double parseDouble(String val) {
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getFileExtension(String url) {
        int lastDot = url.lastIndexOf('.');
        return (lastDot != -1) ? url.substring(lastDot + 1).toLowerCase() : "json";
    }

    /**
     * 🔹 Transform and create new CSV with custom columns
     */
    private File transformAndWriteCsv(List<AzurePriceDTO> prices) throws Exception {
        File file = File.createTempFile("processed_prices_", ".csv");

        try (FileWriter writer = new FileWriter(file)) {
            // 🧾 Header (Custom Order)
            writer.write("MeterId,ProductName,DeploymentOption,Compute,SKU,VCore,Region,Location,Price,Currency,ServiceFamily,Unit,EffectiveDate,Type\n");

            for (AzurePriceDTO dto : prices) {
                // Base fields
                String meterId = safeCsv(dto.getMeterId());
                String productName = safeCsv(dto.getProductName());
                String sku = safeCsv(dto.getSkuName());
                String region = safeCsv(dto.getArmRegionName());
                String location = safeCsv(dto.getLocation());
                String price = String.valueOf(dto.getRetailPrice());
                String currency = safeCsv(dto.getCurrencyCode());
                String serviceFamily = safeCsv(dto.getServiceFamily());
                String unit = safeCsv(dto.getUnitOfMeasure());
                String effectiveDate = safeCsv(dto.getEffectiveStartDate());
                String type = safeCsv(dto.getType());

                // Derived fields
                String deploymentOption = safeCsv(extractDeploymentOption(productName));
                String compute = safeCsv(extractCompute(productName));
                String vCore = safeCsv(extractVCoreFromSku(sku));

                // 🧩 Write in mapped order
                writer.write(String.join(",",
                        quote(meterId),
                        quote(productName),
                        quote(deploymentOption),
                        quote(compute),
                        quote(sku),
                        quote(vCore),
                        quote(region),
                        quote(location),
                        quote(price),
                        quote(currency),
                        quote(serviceFamily),
                        quote(unit),
                        quote(effectiveDate),
                        quote(type)));
                writer.write("\n");
            }
        }

        return file;
    }

    private String quote(String val) {
        if (val == null) return "";
        String v = val.replace("\"", "\"\"");
        return "\"" + v + "\""; // always wrap in quotes
    }

    /**
     * Safely handle commas and nulls for CSV.
     */
    private String safeCsv(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\""); // escape quotes
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v + "\""; // wrap in quotes if needed
        }
        return v;
    }


    // 🔹 Extract "Flexible Server" or "Single Server" after "MySQL"
    private String extractDeploymentOption(String productName) {
        if (productName == null) return "";
        Pattern p = Pattern.compile("MySQL\\s+(\\w+\\s+\\w+)");
        Matcher m = p.matcher(productName);
        return m.find() ? m.group(1).trim() : "";
    }

    // 🔹 Extract text around "Compute"
    private String extractCompute(String productName) {
        if (productName == null) return "";
        Pattern p = Pattern.compile("(Compute[^,]*)");
        Matcher m = p.matcher(productName);
        if (m.find()) return m.group(1).trim();
        Pattern alt = Pattern.compile("([\\w\\s]*Compute[\\w\\s]*)");
        Matcher altM = alt.matcher(productName);
        return altM.find() ? altM.group(1).trim() : "";
    }

    // 🔹 Extract last 3 chars from SKU (LRS, ZRS, GRS)
    private String extractVCoreFromSku(String sku) {
        if (sku == null) return "";
        sku = sku.toUpperCase();

        if (sku.contains("LRS")) return "LRS";
        if (sku.contains("ZRS")) return "ZRS";
        if (sku.contains("GRS")) return "GRS";

        return "";
    }

}
