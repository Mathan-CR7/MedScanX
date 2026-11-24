package com.kovanlabs.ai_medicine_assistant.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class ImagePreprocessingService {

    private static final int MIN_TARGET_SIDE = 1600;

    /**
     * Preprocesses raw camera/packaging image bytes prior to OCR:
     * 1. Bicubic upscaling if longer side < 1600px.
     * 2. Grayscale conversion.
     * 3. Contrast & brightness adjustment (RescaleOp).
     * 4. Mild sharpen convolution filter.
     * 5. Lossless PNG byte array encoding.
     */
    public byte[] preprocessImage(byte[] imageBytes, String filename) {
        if (imageBytes == null || imageBytes.length == 0) {
            return imageBytes;
        }

        try {
            BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (inputImage == null) {
                log.warn("ImageIO could not decode image bytes for file: {}. Returning original bytes.", filename);
                return imageBytes;
            }

            int width = inputImage.getWidth();
            int height = inputImage.getHeight();
            int maxSide = Math.max(width, height);

            BufferedImage scaledImage = inputImage;
            if (maxSide < MIN_TARGET_SIDE && maxSide > 0) {
                double scaleFactor = (double) MIN_TARGET_SIDE / maxSide;
                int targetWidth = (int) Math.round(width * scaleFactor);
                int targetHeight = (int) Math.round(height * scaleFactor);

                log.info("Upscaling image '{}' from {}x{} to {}x{} (bicubic interpolation)",
                        filename, width, height, targetWidth, targetHeight);

                scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = scaledImage.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(inputImage, 0, 0, targetWidth, targetHeight, null);
                g2d.dispose();
            }

            // Convert to Grayscale
            BufferedImage grayImage = new BufferedImage(
                    scaledImage.getWidth(), scaledImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D gGray = grayImage.createGraphics();
            gGray.drawImage(scaledImage, 0, 0, null);
            gGray.dispose();

            // Contrast & Brightness Enhancement (RescaleOp)
            // Factor 1.25x contrast, slight offset for clear text-background separation on foil packaging
            RescaleOp rescaleOp = new RescaleOp(1.25f, -10.0f, null);
            BufferedImage contrastImage = rescaleOp.filter(grayImage, null);

            // Mild Sharpening Convolution Filter
            float[] sharpenKernel = {
                 0.0f, -0.25f,  0.0f,
                -0.25f, 2.00f, -0.25f,
                 0.0f, -0.25f,  0.0f
            };
            Kernel kernel = new Kernel(3, 3, sharpenKernel);
            ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            BufferedImage sharpenedImage = convolveOp.filter(contrastImage, null);
    
            // Write as PNG (lossless)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(sharpenedImage, "png", baos);
            byte[] processedBytes = baos.toByteArray();
            log.info("Image preprocessing complete for file '{}'. Original size: {} bytes, Processed PNG size: {} bytes",
                    filename, imageBytes.length, processedBytes.length);

            return processedBytes;
        } catch (IOException e) {
            log.error("Failed to preprocess image for file: {}", filename, e);
            return imageBytes;
        }
    }
}
