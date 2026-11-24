package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import java.util.concurrent.CompletableFuture;

public interface ScraperService {
    CompletableFuture<MedicinePrice> scrapeWebsite(String medicineName, String website);
}
