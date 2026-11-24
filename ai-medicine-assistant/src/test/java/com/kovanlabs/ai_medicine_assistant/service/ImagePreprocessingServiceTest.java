package com.kovanlabs.ai_medicine_assistant.service;

import com.kovanlabs.ai_medicine_assistant.service.impl.ImagePreprocessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ImagePreprocessingServiceTest {

    private ImagePreprocessingService preprocessingService;

    @BeforeEach
    public void setUp() {
        preprocessingService = new ImagePreprocessingService();
    }

    @Test
    public void testLowResolutionImageUpscaling() throws IOException {
        // Create synthetic low-res image (400x300)
        BufferedImage smallImage = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = smallImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 300);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Dolo 650", 50, 100);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(smallImage, "png", baos);
        byte[] inputBytes = baos.toByteArray();

        byte[] processedBytes = preprocessingService.preprocessImage(inputBytes, "test_lowres.png");

        assertNotNull(processedBytes);
        assertTrue(processedBytes.length > 0);

        BufferedImage resultImage = ImageIO.read(new ByteArrayInputStream(processedBytes));
        assertNotNull(resultImage);

        // Verify longer side is upscaled to 1600px
        int maxSide = Math.max(resultImage.getWidth(), resultImage.getHeight());
        assertEquals(1600, maxSide);
    }

    @Test
    public void testGrayscaleAndContrastProcessing() throws IOException {
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, 800, 600);
        g.setColor(Color.DARK_GRAY);
        g.drawString("Augmentin 625", 100, 200);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] inputBytes = baos.toByteArray();

        byte[] processedBytes = preprocessingService.preprocessImage(inputBytes, "test_contrast.png");

        assertNotNull(processedBytes);
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processedBytes));
        assertNotNull(result);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, result.getType());
    }

    @Test
    public void testNullOrEmptyInputHandling() {
        assertNull(preprocessingService.preprocessImage(null, "null.png"));
        byte[] empty = new byte[0];
        assertArrayEquals(empty, preprocessingService.preprocessImage(empty, "empty.png"));
    }
}
