package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import java.util.List;

public interface OcrService {
    String extractMedicineName(byte[] imageBytes, String originalFilename);
    List<OCRTextRegion> extractTextRegions(byte[] imageBytes, String originalFilename);
}
