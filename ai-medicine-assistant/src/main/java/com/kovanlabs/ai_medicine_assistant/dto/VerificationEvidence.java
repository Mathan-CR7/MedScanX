package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationEvidence {
    private double brandSimilarity;
    private double ocrConfidence;
    private double compositionMatch;
    private double manufacturerMatch;
    private double strengthMatch;
}
