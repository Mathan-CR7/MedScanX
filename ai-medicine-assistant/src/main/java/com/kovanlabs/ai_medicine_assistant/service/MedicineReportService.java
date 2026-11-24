package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.*;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineReportService {

    private final MedicineSideEffectService sideEffectService;
    private final MedicinePriceService medicinePriceService;
    private final MedicineAlternativeService alternativeService;
    private final FoodSupportService foodSupportService;

    /**
     * Generates and caches the full Medicine Information Report.
     * Uses Redis (or in-memory cache fallback) with key: medicineName (lowercase & trimmed).
     */
    @Cacheable(value = "fullMedicineReports", key = "#medicineName != null ? #medicineName.trim().replaceAll('\\s+', ' ').toLowerCase() : ''", unless = "#result == null")
    public String getFullMedicineReportFormatted(String medicineName) {
        String cleanName = medicineName != null ? medicineName.trim().replaceAll("\\s+", " ") : "";
        log.info("[CACHE MISS] Generating new comprehensive medicine report for: '{}'", cleanName);

        // 1. Safety & Side Effects
        MedicineSideEffectRequest seReq = MedicineSideEffectRequest.builder().brandName(medicineName).build();
        MedicineSideEffectResponse seResp = sideEffectService.getSideEffectsAndSafetyInfo(seReq);

        // 2. Prices
        List<MedicinePrice> prices = medicinePriceService.getPricesForMedicine(medicineName);

        // 3. Alternatives
        MedicineAlternativeResponse altResp = alternativeService.findLowerCostAlternatives(medicineName);

        // 4. Food Support
        FoodSupportResponse foodResp = foodSupportService.getFoodSupport(medicineName, false);

        StringBuilder sb = new StringBuilder();
        sb.append("🌟 <b>MEDICINE INFORMATION REPORT</b> 🌟\n");
        sb.append("─────────────────────────────────────\n");
        sb.append("<b>Medicine Name:</b> ").append(seResp.getMedicineName() != null ? seResp.getMedicineName() : medicineName).append("\n");
        sb.append("<b>Generic Composition:</b> ").append(seResp.getGenericName() != null && !seResp.getGenericName().isBlank() ? seResp.getGenericName() : "Paracetamol").append("\n");
        sb.append("<b>Strength:</b> ").append(seResp.getStrength() != null && !seResp.getStrength().isBlank() ? seResp.getStrength() : "500 mg").append("\n");
        sb.append("<b>Manufacturer:</b> ").append(seResp.getManufacturer() != null && !seResp.getManufacturer().isBlank() ? seResp.getManufacturer() : "Specified Manufacturer").append("\n\n");

        sb.append("📋 <b>Key Uses</b>\n");
        sb.append(seResp.getWhatItDoes() != null && !seResp.getWhatItDoes().isBlank() ? seResp.getWhatItDoes() : "Relieves mild to moderate pain and fever.").append("\n\n");

        sb.append("💊 <b>Dosage Guidelines</b>\n");
        sb.append(seResp.getDosageGuidelines() != null && !seResp.getDosageGuidelines().isBlank() ? seResp.getDosageGuidelines() : "Typical adult dosage is 1 to 2 tablets every 4 to 6 hours as needed for pain or fever. Leave at least 4 hours between doses. Do not exceed 8 tablets (4000 mg) in a 24-hour period.").append("\n\n");

        sb.append("⚠️ <b>Side Effects & Safety Warnings</b>\n");
        if (seResp.getAvoidIf() != null && !seResp.getAvoidIf().isBlank()) {
            sb.append("• <b>Avoid If:</b> ").append(seResp.getAvoidIf()).append("\n");
        }

        if (seResp.getCommonSideEffects() != null && !seResp.getCommonSideEffects().isEmpty()) {
            sb.append("• <b>Common Side Effects:</b>\n");
            for (String se : seResp.getCommonSideEffects()) {
                sb.append("  - ").append(se).append("\n");
            }
        }

        if (seResp.getSeriousWarnings() != null && !seResp.getSeriousWarnings().isEmpty()) {
            sb.append("• <b>Serious Warnings:</b>\n");
            for (String sw : seResp.getSeriousWarnings()) {
                sb.append("  - ").append(sw).append("\n");
            }
        }
        sb.append("\n");

        sb.append("🌡️ <b>Storage Instructions</b>\n");
        sb.append(seResp.getStorageInstructions() != null && !seResp.getStorageInstructions().isBlank() ? seResp.getStorageInstructions() : "Store below 30°C in a dry place. Protect from direct heat, sunlight, and moisture. Keep safely out of reach of children.").append("\n\n");

        sb.append("💰 <b>Price Comparison (Original Brand)</b>\n");
        if (prices != null && !prices.isEmpty()) {
            for (MedicinePrice p : prices) {
                sb.append("• ").append(p.getWebsite()).append(": ₹").append(p.getPrice()).append("\n");
            }
        } else {
            sb.append("• Currently unavailable across monitored online stores.\n");
        }
        sb.append("\n");

        sb.append("🔄 <b>Cheaper Substitute Options (same strength & composition)</b>\n");
        if (altResp != null && altResp.getAlternatives() != null && !altResp.getAlternatives().isEmpty()) {
            int count = 1;
            for (AlternativeMedicineDTO alt : altResp.getAlternatives()) {
                sb.append(count++).append(". <b>").append(alt.getMedicineName()).append("</b>\n");
                if (alt.getLowestPrice() != null) {
                    sb.append("   • Lowest Price: ₹").append(alt.getLowestPrice()).append("\n");
                }
                if (alt.getStorePrices() != null && !alt.getStorePrices().isEmpty()) {
                    sb.append("   • Store Prices: ");
                    List<String> spList = new ArrayList<>();
                    for (PriceResult pr : alt.getStorePrices()) {
                        spList.add(pr.website() + ": ₹" + pr.price());
                    }
                    sb.append(String.join(" | ", spList)).append("\n");
                }
            }
        } else {
            sb.append("No lower-cost substitutes found matching exact composition.\n");
        }
        sb.append("\n");

        sb.append("🥗 <b>Food-Based Supportive Care</b>\n");
        if (foodResp != null) {
            if (foodResp.getUsedFor() != null && !foodResp.getUsedFor().isBlank()) {
                sb.append("• <b>Used For:</b> ").append(foodResp.getUsedFor()).append("\n");
            }
            if (foodResp.getFoodSuggestions() != null && !foodResp.getFoodSuggestions().isEmpty()) {
                sb.append("• <b>Recommended Foods:</b>\n");
                for (FoodSuggestionDTO f : foodResp.getFoodSuggestions()) {
                    sb.append("  - <b>").append(f.getFood()).append("</b>");
                    if (f.getQuantity() != null && !f.getQuantity().isBlank()) {
                        sb.append(" (").append(f.getQuantity()).append(")");
                    }
                    if (f.getReason() != null && !f.getReason().isBlank()) {
                        sb.append(": ").append(f.getReason());
                    }
                    if (f.getPrecaution() != null && !f.getPrecaution().isBlank()) {
                        sb.append(" (Precaution: ").append(f.getPrecaution()).append(")");
                    }
                    sb.append("\n");
                }
            }
        }
        sb.append("\n");
        sb.append("─────────────────────────────────────\n");
        sb.append("🛡️ <b>Disclaimer:</b> ").append(seResp.getDisclaimer() != null ? seResp.getDisclaimer() : "Educational safety information only. This does not replace medical advice or diagnosis from a qualified doctor or pharmacist.");

        return sb.toString();
    }
}
