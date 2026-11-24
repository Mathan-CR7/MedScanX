package com.kovanlabs.ai_medicine_assistant.service.impl;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.kovanlabs.ai_medicine_assistant.dto.BrandCandidate;
import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import com.kovanlabs.ai_medicine_assistant.service.BrandCandidateGenerator;
import com.kovanlabs.ai_medicine_assistant.service.BrandCandidateScoringService;
import com.kovanlabs.ai_medicine_assistant.service.OcrService;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import io.github.mymonstercat.ocr.config.ParamConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PaddleOcrService implements OcrService {

    private final InferenceEngine engine;
    private final ParamConfig paramConfig;
    private final ImagePreprocessingService preprocessingService;
    private final BrandCandidateGenerator candidateGenerator;
    private final BrandCandidateScoringService candidateScoringService;

    public PaddleOcrService(ImagePreprocessingService preprocessingService,
                             BrandCandidateGenerator candidateGenerator,
                             BrandCandidateScoringService candidateScoringService) {
        this.preprocessingService = preprocessingService;
        this.candidateGenerator = candidateGenerator;
        this.candidateScoringService = candidateScoringService;

        log.info("Initializing PaddleOCR InferenceEngine and ParamConfig...");
        InferenceEngine tempEngine;
        try {
            tempEngine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
        } catch (Throwable e) {
            log.warn("Failed to load ONNX_PPOCR_V4, falling back to ONNX_PPOCR_V3", e);
            try {
                tempEngine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
            } catch (Throwable t) {
                log.error("Could not load ONNX_PPOCR_V3 either. OCR engine will not be functional.", t);
                tempEngine = null;
            }
        }
        this.engine = tempEngine;

        // Configure ParamConfig for high-accuracy medicine label OCR
        ParamConfig config = ParamConfig.getDefaultConfig();
        config.setDoAngle(true);
        config.setMostAngle(true);
        config.setBoxScoreThresh(0.3f); // Lower threshold so faint/glossy packaging text is preserved
        config.setMaxSideLen(1800);    // Preserve fine details on packaging photos
        this.paramConfig = config;

        log.info("PaddleOCR InferenceEngine initialized successfully: {} (ParamConfig: doAngle=true, boxScoreThresh=0.3, maxSideLen=1800)",
                this.engine != null);
    }

    @Override
    public List<OCRTextRegion> extractTextRegions(byte[] imageBytes, String originalFilename) {
        log.info("Running PaddleOCR region extraction for file: {}", originalFilename);
        if (engine == null) {
            throw new IllegalStateException("PaddleOCR engine is not loaded.");
        }

        // Apply pure Java image preprocessing (scaling, grayscale, contrast boost, sharpening)
        byte[] processedBytes = preprocessingService != null
                ? preprocessingService.preprocessImage(imageBytes, originalFilename)
                : imageBytes;

        Path tempFile = null;
        try {
            // Use short, clean temp filename to prevent Windows MAX_PATH (>260 chars) native ONNX file open failures
            tempFile = Files.createTempFile("ocr_med_", ".png");
            Files.write(tempFile, processedBytes);

            String tempPathString = tempFile.toAbsolutePath().toString();
            // RapidOCR wrapper appends ".png" internally if path string doesn't end in ".png"
            // To prevent double extension issues, pass path cleanly
            OcrResult result = engine.runOcr(tempPathString, paramConfig);
            List<OCRTextRegion> regions = new ArrayList<>();

            if (result != null && result.getTextBlocks() != null && !result.getTextBlocks().isEmpty()) {
                for (TextBlock block : result.getTextBlocks()) {
                    String text = block.getText();
                    if (text == null || text.trim().isEmpty()) {
                        continue;
                    }

                    double confidence = block.getBoxScore();
                    double minX = 0, minY = 0, maxX = 0, maxY = 0;

                    if (block.getBoxPoint() != null && !block.getBoxPoint().isEmpty()) {
                        minX = block.getBoxPoint().stream().mapToDouble(Point::getX).min().orElse(0.0);
                        minY = block.getBoxPoint().stream().mapToDouble(Point::getY).min().orElse(0.0);
                        maxX = block.getBoxPoint().stream().mapToDouble(Point::getX).max().orElse(0.0);
                        maxY = block.getBoxPoint().stream().mapToDouble(Point::getY).max().orElse(0.0);
                    }

                    double width = Math.max(1.0, maxX - minX);
                    double height = Math.max(1.0, maxY - minY);
                    double area = width * height;

                    regions.add(OCRTextRegion.builder()
                            .text(text.trim())
                            .confidence(confidence > 0 ? confidence : 0.90)
                            .x(minX)
                            .y(minY)
                            .width(width)
                            .height(height)
                            .area(area)
                            .build());
                }
            }

            log.info("Extracted {} OCR text regions from image.", regions.size());
            return regions;
        } catch (IOException e) {
            log.error("Error processing OCR image file", e);
            throw new RuntimeException("Error processing image file for OCR: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary OCR file: {}", tempFile, e);
                }
            }
        }
    }

    @Override
    public String extractMedicineName(byte[] imageBytes, String originalFilename) {
        List<OCRTextRegion> regions = extractTextRegions(imageBytes, originalFilename);
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("No text detected in medicine image.");
        }

        if (candidateGenerator != null && candidateScoringService != null) {
            List<BrandCandidate> candidates = candidateGenerator.generateCandidates(regions);
            List<BrandCandidate> ranked = candidateScoringService.rankCandidates(candidates, regions);
            for (BrandCandidate candidate : ranked) {
                String candidateText = candidateGenerator.cleanBrandName(candidate.getOriginalText());
                if (!candidateText.isBlank() 
                        && !candidateGenerator.isExcludedText(candidateText) 
                        && !candidateGenerator.isGibberishToken(candidateText)) {
                    return candidateText;
                }
            }
        }

        return regions.get(0).getText();
    }
}
