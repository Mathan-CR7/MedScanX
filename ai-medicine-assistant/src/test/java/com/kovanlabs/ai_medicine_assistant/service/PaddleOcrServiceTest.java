package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.dto.OCRTextRegion;
import com.kovanlabs.ai_medicine_assistant.service.impl.ImagePreprocessingService;
import com.kovanlabs.ai_medicine_assistant.service.impl.PaddleOcrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PaddleOcrServiceTest {

    private PaddleOcrService ocrService;
    private ImagePreprocessingService preprocessingService;

    @BeforeEach
    public void setUp() {
        OCRNormalizationService normalizationService = new OCRNormalizationService();
        BrandCandidateGenerator generator = new BrandCandidateGenerator(normalizationService);
        BrandCandidateScoringService scoringService = new BrandCandidateScoringService();
        preprocessingService = new ImagePreprocessingService();

        ocrService = new PaddleOcrService(preprocessingService, generator, scoringService);
    }

    @Test
    public void testOcrRegionExtractionOnSyntheticLabelImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1200, 600);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        g.drawString("Dolo 650", 100, 150);
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        g.drawString("Paracetamol Tablets IP 650 mg", 100, 250);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        List<OCRTextRegion> regions = ocrService.extractTextRegions(imageBytes, "synthetic_dolo.png");
        assertNotNull(regions);
        assertFalse(regions.isEmpty(), "OCR text regions should be extracted from clear text image");
    }

    @Test
    public void testOcrRegionExtractionOnRotatedImage() throws IOException {
        BufferedImage baseImage = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = baseImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 800);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 40));

        // Rotate graphic context 90 degrees
        AffineTransform at = new AffineTransform();
        at.rotate(Math.toRadians(90), 400, 400);
        g.setTransform(at);
        g.drawString("PAN D 40mg", 300, 400);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(baseImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        List<OCRTextRegion> regions = ocrService.extractTextRegions(imageBytes, "rotated_pand.png");
        assertNotNull(regions);
    }

    @Test
    public void testExtractMedicineName() throws IOException {
        BufferedImage image = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1000, 500);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 54));
        g.drawString("Calpol 650", 80, 120);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("Paracetamol 650mg", 80, 220);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        String medicineName = ocrService.extractMedicineName(imageBytes, "calpol_650.png");
        assertNotNull(medicineName);
        assertFalse(medicineName.isBlank());
    }
}
