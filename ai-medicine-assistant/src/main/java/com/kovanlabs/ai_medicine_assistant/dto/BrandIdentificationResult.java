package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by Spring AI after reasoning over raw OCR text from a medicine label image.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandIdentificationResult {
    /** The identified brand medicine name (e.g. "Calpol 500", "Dolo 650", "Pan 40"). */
    private String brandName;

    /** Strength portion of the brand (e.g. "500mg", "650mg", "40mg"). */
    private String strength;

    /** Identified active pharmaceutical composition (e.g. "Paracetamol", "Pantoprazole"). */
    private String composition;

    /**
     * AI confidence score between 0.0 (completely unsure) and 1.0 (certain).
     * Scores below 0.40 are treated as unidentifiable.
     */
    private double confidence;

    /** AI's explanation of how it identified the brand from OCR text fragments. */
    private String reasoning;
}
