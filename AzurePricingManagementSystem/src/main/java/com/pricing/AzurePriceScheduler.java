package com.pricing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pricing.service.AzurePriceService;

@Component
public class AzurePriceScheduler {

	private final AzurePriceService azurePriceService;

    public AzurePriceScheduler(AzurePriceService azurePriceService) {
        this.azurePriceService = azurePriceService;
    }

    /**
     * Runs automatically every Monday at 10:00 AM IST
     */
    @Scheduled(cron = "0 */1 * * * *", zone = "Asia/Kolkata")
    public void scheduleWeeklyUpload() {
        System.out.println("📅 Starting scheduled Azure pricing upload to S3...");

        try {
            azurePriceService.fetchAndUploadData(null);
            System.out.println("✅ Weekly Azure pricing upload completed successfully!");
        } catch (Exception e) {
            System.err.println("❌ Error during scheduled upload: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
