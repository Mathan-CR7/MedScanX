package com.kovanlabs.ai_medicine_assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OCRTextRegion {
    private String text;
    private double confidence;

    // Bounding Box layout metrics
    private double x;
    private double y;
    private double width;
    private double height;
    private double area;
}
