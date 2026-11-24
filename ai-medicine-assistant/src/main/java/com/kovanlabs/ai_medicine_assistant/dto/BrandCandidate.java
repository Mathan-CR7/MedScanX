package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandCandidate {
    private String originalText;
    private String normalizedText;

    private double ocrConfidence;
    private double visualScore;
    private double semanticScore;
    private double candidateScore;

    private OCRTextRegion region;
}
