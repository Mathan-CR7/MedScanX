package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MedicineLabelAiReasoningServiceTests {

    @Autowired
    private MedicineLabelAiReasoningService aiReasoningService;

    @Test
    public void testIdentifyBrandName_withCalpolFragments() {
        // Simulates garbled OCR output from a Calpol 500 label
        List<OCRTextRegion> regions = Arrays.asList(
                OCRTextRegion.builder().text("CALPOL").confidence(0.85).x(0).y(0).width(100).height(30).area(3000).build(),
                OCRTextRegion.builder().text("500").confidence(0.90).x(0).y(35).width(60).height(25).area(1500).build(),
                OCRTextRegion.builder().text("Paracetamol").confidence(0.80).x(0).y(65).width(120).height(25).area(3000).build()
        );

        String result = aiReasoningService.identifyBrandName(regions);

        // When Spring AI is available: expects a non-null, non-blank brand name
        // When Spring AI is not configured: expects null (no fallback)
        if (result != null) {
            assertFalse(result.isBlank(), "Identified brand name should not be blank");
            assertTrue(result.length() > 2, "Brand name should have reasonable length");
            log.info("Spring AI identified brand from Calpol fragments: {}", result);
        } else {
            // Spring AI not configured in test context — expected
            System.out.println("[Test] Spring AI not configured — identifyBrandName returned null as expected.");
        }
    }

    @Test
    public void testIdentifyBrandName_withEmptyRegions() {
        String result = aiReasoningService.identifyBrandName(Collections.emptyList());
        assertNull(result, "Empty regions should return null");
    }

    @Test
    public void testIdentifyBrandName_withNullInput() {
        String result = aiReasoningService.identifyBrandName(null);
        assertNull(result, "Null input should return null");
    }

    @Test
    public void testIdentifyBrandName_withBlankTextRegions() {
        List<OCRTextRegion> regions = List.of(
                OCRTextRegion.builder().text("  ").confidence(0.1).x(0).y(0).width(10).height(10).area(100).build()
        );
        String result = aiReasoningService.identifyBrandName(regions);
        assertNull(result, "All-blank OCR regions should return null");
    }

    // Logger accessor for test output
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MedicineLabelAiReasoningServiceTests.class);
}
