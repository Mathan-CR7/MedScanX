package com.kovanlabs.ai_medicine_assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response returned from POST /api/medicines/search-image.
 * Contains verified brand name and store-wise live prices.
 * sideEffects / alternatives / foodSupport are only populated
 * by their own dedicated endpoints — null fields are hidden from JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicineSearchResponse(
    String medicineName,
    List<PriceResult> prices,
    MedicineSideEffectResponse sideEffects,
    MedicineAlternativeResponse alternatives,
    FoodSupportResponse foodSupport
) {
    public MedicineSearchResponse(String medicineName, List<PriceResult> prices) {
        this(medicineName, prices, null, null, null);
    }

    public MedicineSearchResponse(String medicineName, List<PriceResult> prices, MedicineSideEffectResponse sideEffects) {
        this(medicineName, prices, sideEffects, null, null);
    }

    public MedicineSearchResponse(String medicineName, List<PriceResult> prices, MedicineSideEffectResponse sideEffects, MedicineAlternativeResponse alternatives) {
        this(medicineName, prices, sideEffects, alternatives, null);
    }
}
