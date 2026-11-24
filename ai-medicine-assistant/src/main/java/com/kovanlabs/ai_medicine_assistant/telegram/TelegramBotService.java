package com.kovanlabs.ai_medicine_assistant.telegram;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.ai_medicine_assistant.dto.*;
import com.kovanlabs.ai_medicine_assistant.model.Medicine;
import com.kovanlabs.ai_medicine_assistant.model.MedicinePrice;
import com.kovanlabs.ai_medicine_assistant.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotService {

    @Value("${telegram.bot.token:8854688772:AAGDHxiZtiKmx_3AHP-GyUX3JePauATUNaU}")
    private String botToken;

    @Value("${telegram.bot.enabled:true}")
    private boolean enabled;

    private final MedicineSearchService medicineSearchService;
    private final MedicineSideEffectService sideEffectService;
    private final MedicineAlternativeService alternativeService;
    private final FoodSupportService foodSupportService;
    private final MedicinePriceService medicinePriceService;
    private final TrackedMedicineService trackedMedicineService;
    private final MedicineReportService medicineReportService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private long lastUpdateId = 0;

    @EventListener(ApplicationReadyEvent.class)
    public void startBotPolling() {
        if (!enabled || botToken == null || botToken.isBlank()) {
            log.info("[TelegramBot] Telegram bot polling is disabled or token is missing.");
            return;
        }
        log.info("[TelegramBot] Starting Telegram bot long-polling thread for token: {}...",
                botToken.substring(0, Math.min(10, botToken.length())) + "...");

        executorService.submit(this::pollingLoop);
    }

    private void pollingLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                pollUpdates();
            } catch (Exception e) {
                log.error("[TelegramBot] Exception in polling loop: {}. Retrying in 5 seconds...", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void pollUpdates() {
        String url = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=20", botToken, lastUpdateId + 1);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.path("ok").asBoolean()) {
                    JsonNode results = root.path("result");
                    for (JsonNode update : results) {
                        long updateId = update.path("update_id").asLong();
                        if (updateId > lastUpdateId) {
                            lastUpdateId = updateId;
                        }
                        processUpdate(update);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[TelegramBot] Polling request error: {}", e.getMessage());
        }
    }

    private void processUpdate(JsonNode update) {
        try {
            if (update.has("message")) {
                JsonNode message = update.get("message");
                long chatId = message.path("chat").path("id").asLong();

                if (message.has("photo")) {
                    handlePhotoMessage(chatId, message.get("photo"));
                } else if (message.has("text")) {
                    String text = message.get("text").asText().trim();
                    handleTextMessage(chatId, text);
                }
            } else if (update.has("callback_query")) {
                JsonNode callback = update.get("callback_query");
                long chatId = callback.path("message").path("chat").path("id").asLong();
                String data = callback.path("data").asText();
                String callbackId = callback.path("id").asText();
                answerCallbackQuery(callbackId);
                handleCallbackQuery(chatId, data);
            }
        } catch (Exception e) {
            log.error("[TelegramBot] Failed to process update: {}", e.getMessage(), e);
        }
    }

    private void handleTextMessage(long chatId, String text) {
        if (text.startsWith("/start") || text.startsWith("/help")) {
            sendWelcomeMessage(chatId);
        } else if (text.startsWith("/report")) {
            String medName = text.replace("/report", "").trim();
            if (medName.isBlank()) {
                sendMessage(chatId, "⚠️ Please specify a medicine name.\nExample: <code>/report Calpol 500</code>");
            } else {
                handleFullReport(chatId, medName);
            }
        } else if (text.startsWith("/sideeffects")) {
            String medName = text.replace("/sideeffects", "").trim();
            if (medName.isBlank()) {
                sendMessage(chatId, "⚠️ Please specify a medicine name.\nExample: <code>/sideeffects Paracetamol</code>");
            } else {
                handleSideEffects(chatId, medName);
            }
        } else if (text.startsWith("/alternatives")) {
            String medName = text.replace("/alternatives", "").trim();
            if (medName.isBlank()) {
                sendMessage(chatId, "⚠️ Please specify a medicine name.\nExample: <code>/alternatives Augmentin 625</code>");
            } else {
                handleAlternatives(chatId, medName);
            }
        } else if (text.startsWith("/food")) {
            String medName = text.replace("/food", "").trim();
            if (medName.isBlank()) {
                sendMessage(chatId, "⚠️ Please specify a medicine name.\nExample: <code>/food Metformin</code>");
            } else {
                handleFoodSupport(chatId, medName);
            }
        } else if (text.startsWith("/price")) {
            String medName = text.replace("/price", "").trim();
            if (medName.isBlank()) {
                sendMessage(chatId, "⚠️ Please specify a medicine name.\nExample: <code>/price Dolo 650</code>");
            } else {
                handlePriceCheck(chatId, medName);
            }
        } else if (text.startsWith("/track")) {
            String medName = text.replace("/track", "").trim();
            if (medName.isBlank()) {
                sendMessage(chatId, "⚠️ Please specify a medicine name to track.\nExample: <code>/track Crocin</code>");
            } else {
                handleTrackMedicine(chatId, medName);
            }
        } else if (text.startsWith("/mymeds")) {
            handleMyMeds(chatId);
        } else {
            // Treat typed text as a medicine name and generate the full report!
            handleFullReport(chatId, text);
        }
    }

    private void handleCallbackQuery(long chatId, String data) {
        if (data.startsWith("report:")) {
            handleFullReport(chatId, data.substring("report:".length()));
        } else if (data.startsWith("sideeffects:")) {
            handleSideEffects(chatId, data.substring("sideeffects:".length()));
        } else if (data.startsWith("alternatives:")) {
            handleAlternatives(chatId, data.substring("alternatives:".length()));
        } else if (data.startsWith("food:")) {
            handleFoodSupport(chatId, data.substring("food:".length()));
        } else if (data.startsWith("track:")) {
            handleTrackMedicine(chatId, data.substring("track:".length()));
        }
    }

    private void handlePhotoMessage(long chatId, JsonNode photoArray) {
        sendMessage(chatId, "🔍 <i>Analyzing medicine label with PaddleOCR & Gemini AI... Please wait.</i>");
        try {
            // Get largest photo
            JsonNode largestPhoto = photoArray.get(photoArray.size() - 1);
            String fileId = largestPhoto.get("file_id").asText();

            // Get file path
            String getFileUrl = String.format("https://api.telegram.org/bot%s/getFile?file_id=%s", botToken, fileId);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getFileUrl)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode fileRoot = objectMapper.readTree(response.body());
            String filePath = fileRoot.path("result").path("file_path").asText();

            // Download image bytes
            String downloadUrl = String.format("https://api.telegram.org/file/bot%s/%s", botToken, filePath);
            HttpRequest downloadReq = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
            HttpResponse<byte[]> imageResp = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofByteArray());

            byte[] imageBytes = imageResp.body();

            // Execute Spring AI search pipeline
            MedicineSearchResponse result = medicineSearchService.searchMedicineByImage(imageBytes, "telegram_upload.jpg");

            if ("UNVERIFIED".equalsIgnoreCase(result.medicineName()) || result.medicineName() == null) {
                sendMessage(chatId, "❌ <b>Medicine Could Not Be Identified</b>\nCould not extract clear brand text from this label. Please try taking a clearer, well-lit photo of the brand name.");
                return;
            }

            // Generate full report for identified medicine
            handleFullReport(chatId, result.medicineName());

        } catch (Exception e) {
            log.error("[TelegramBot] Error processing photo: {}", e.getMessage(), e);
            sendMessage(chatId, "⚠️ Error processing medicine label photo: " + e.getMessage());
        }
    }

    private void handleFullReport(long chatId, String medicineName) {
        sendMessage(chatId, "⏳ <i>Retrieving Medicine Information Report for " + medicineName + "...</i>");
        try {
            // Get cached report from Redis (or in-memory cache fallback)
            String reportHtml = medicineReportService.getFullMedicineReportFormatted(medicineName);

            List<List<Map<String, String>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "📌 Track Medicine Price", "callback_data", "track:" + medicineName)
                    )
            );

            sendMessageWithKeyboard(chatId, reportHtml, keyboard);

        } catch (Exception e) {
            log.error("[TelegramBot] Error generating full report: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Failed to generate report for " + medicineName + ": " + e.getMessage());
        }
    }



    private void handleSideEffects(long chatId, String medicineName) {
        sendMessage(chatId, "⏳ <i>Retrieving safety info for " + medicineName + "...</i>");
        try {
            MedicineSideEffectRequest request = MedicineSideEffectRequest.builder().brandName(medicineName).build();
            MedicineSideEffectResponse resp = sideEffectService.getSideEffectsAndSafetyInfo(request);

            StringBuilder sb = new StringBuilder();
            sb.append("💊 <b>Safety & Side Effects: ").append(resp.getMedicineName()).append("</b>\n");
            if (resp.getGenericName() != null && !resp.getGenericName().isBlank()) {
                sb.append("🔬 <i>Active Composition: ").append(resp.getGenericName()).append("</i>\n");
            }
            sb.append("\n");

            if (resp.getWhatItDoes() != null && !resp.getWhatItDoes().isBlank()) {
                sb.append("📋 <b>What It Does:</b>\n").append(resp.getWhatItDoes()).append("\n\n");
            }

            if (resp.getAvoidIf() != null && !resp.getAvoidIf().isBlank()) {
                sb.append("⚠️ <b>Avoid If:</b>\n").append(resp.getAvoidIf()).append("\n\n");
            }

            if (resp.getCommonSideEffects() != null && !resp.getCommonSideEffects().isEmpty()) {
                sb.append("🔹 <b>Common Side Effects:</b>\n");
                for (String se : resp.getCommonSideEffects()) {
                    sb.append("• ").append(se).append("\n");
                }
                sb.append("\n");
            }

            if (resp.getSeriousWarnings() != null && !resp.getSeriousWarnings().isEmpty()) {
                sb.append("🚨 <b>Serious Warnings:</b>\n");
                for (String w : resp.getSeriousWarnings()) {
                    sb.append("• ").append(w).append("\n");
                }
                sb.append("\n");
            }

            sb.append("ℹ️ <i>").append(resp.getDisclaimer() != null ? resp.getDisclaimer() : "Consult a doctor for advice.").append("</i>");
            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Failed to fetch side effects for " + medicineName + ": " + e.getMessage());
        }
    }

    private void handleAlternatives(long chatId, String medicineName) {
        sendMessage(chatId, "⏳ <i>Searching lower-cost substitutes for " + medicineName + "...</i>");
        try {
            MedicineAlternativeResponse resp = alternativeService.findLowerCostAlternatives(medicineName);

            StringBuilder sb = new StringBuilder();
            sb.append("🔄 <b>Lower-Cost Alternatives for ").append(resp.getOriginalMedicine()).append("</b>\n");
            if (resp.getComposition() != null) {
                sb.append("🧪 <b>Composition:</b> ").append(resp.getComposition()).append("\n");
            }
            sb.append("\n");

            if (resp.getAlternatives() != null && !resp.getAlternatives().isEmpty()) {
                for (AlternativeMedicineDTO alt : resp.getAlternatives()) {
                    sb.append("🏷️ <b>").append(alt.getMedicineName()).append("</b>\n");
                    if (alt.getStrength() != null) {
                        sb.append("   • Strength: ").append(alt.getStrength()).append("\n");
                    }
                    if (alt.getLowestPrice() != null) {
                        sb.append("   • Est. Price: ₹").append(alt.getLowestPrice()).append("\n");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("ℹ️ No lower-cost alternatives with exact composition match found in catalog.\n\n");
            }

            if (resp.getDisclaimer() != null) {
                sb.append("ℹ️ <i>").append(resp.getDisclaimer()).append("</i>");
            }
            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Failed to fetch alternatives for " + medicineName + ": " + e.getMessage());
        }
    }

    private void handleFoodSupport(long chatId, String medicineName) {
        sendMessage(chatId, "⏳ <i>Fetching food support recommendations for " + medicineName + "...</i>");
        try {
            FoodSupportResponse resp = foodSupportService.getFoodSupport(medicineName, false);

            StringBuilder sb = new StringBuilder();
            sb.append("🥗 <b>Dietary Support & Food Tips for ").append(resp.getMedicine()).append("</b>\n");
            if (resp.getUsedFor() != null) {
                sb.append("🎯 <i>Condition Treated: ").append(resp.getUsedFor()).append("</i>\n\n");
            }

            if (resp.getFoodSuggestions() != null && !resp.getFoodSuggestions().isEmpty()) {
                sb.append("🥑 <b>Recommended Foods:</b>\n");
                for (FoodSuggestionDTO f : resp.getFoodSuggestions()) {
                    sb.append("• <b>").append(f.getFood()).append("</b>");
                    if (f.getQuantity() != null) {
                        sb.append(" (").append(f.getQuantity()).append(")");
                    }
                    if (f.getReason() != null) {
                        sb.append(": ").append(f.getReason());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            if (resp.getWarning() != null && !resp.getWarning().isBlank()) {
                sb.append("⚠️ <b>Warning:</b> ").append(resp.getWarning());
            }

            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Failed to fetch food support for " + medicineName + ": " + e.getMessage());
        }
    }

    private void handlePriceCheck(long chatId, String medicineName) {
        sendMessage(chatId, "⏳ <i>Searching price across online pharmacies for " + medicineName + "...</i>");
        try {
            List<MedicinePrice> prices = medicinePriceService.getPricesForMedicine(medicineName);

            StringBuilder sb = new StringBuilder();
            sb.append("💰 <b>Price Comparison: ").append(medicineName).append("</b>\n\n");

            if (prices != null && !prices.isEmpty()) {
                for (MedicinePrice p : prices) {
                    sb.append("• <b>").append(p.getWebsite()).append("</b>: ₹").append(p.getPrice());
                    if (p.getAvailability() != null && !p.getAvailability().isBlank()) {
                        sb.append(" (").append(p.getAvailability()).append(")");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("ℹ️ No stored prices found. Initiating live scrape...\n");
                List<MedicinePrice> scraped = medicinePriceService.scrapePricesForMedicine(medicineName);
                if (scraped != null && !scraped.isEmpty()) {
                    for (MedicinePrice p : scraped) {
                        sb.append("• <b>").append(p.getWebsite()).append("</b>: ₹").append(p.getPrice()).append("\n");
                    }
                } else {
                    sb.append("❌ Could not retrieve price for ").append(medicineName).append(".");
                }
            }

            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Price lookup error: " + e.getMessage());
        }
    }

    private void handleTrackMedicine(long chatId, String medicineName) {
        try {
            Medicine med = trackedMedicineService.addTrackedMedicine(medicineName);
            sendMessage(chatId, "📌 <b>Tracked!</b>\nAdded <b>" + med.getName() + "</b> to your price monitoring watchlist.");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Could not track medicine: " + e.getMessage());
        }
    }

    private void handleMyMeds(long chatId) {
        try {
            List<Medicine> meds = trackedMedicineService.getTrackedMedicines();
            if (meds == null || meds.isEmpty()) {
                sendMessage(chatId, "📋 <b>Tracked Medicines</b>\nYou are currently not tracking any medicines.\nUse <code>/track &lt;name&gt;</code> to add one!");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📋 <b>Your Tracked Medicines Watchlist:</b>\n\n");
            for (Medicine m : meds) {
                sb.append("• <b>").append(m.getName()).append("</b> (ID: ").append(m.getId()).append(")\n");
            }
            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Failed to fetch tracked medicines: " + e.getMessage());
        }
    }

    private void sendWelcomeMessage(long chatId) {
        String welcome = """
                🤖 <b>Welcome to MedScanX AI Telegram Assistant!</b>

                Upload a medicine strip photo or use commands to compare prices, side effects, and substitutes.

                📸 <b>Send any photo</b> of a medicine strip/bottle to scan & identify prices instantly!

                <b>Available Commands:</b>
                🔹 <code>/price &lt;name&gt;</code> — Live price comparison across pharmacies
                🔹 <code>/sideeffects &lt;name&gt;</code> — Simple AI side effect breakdown
                🔹 <code>/alternatives &lt;name&gt;</code> — Lower-cost generic substitutes
                🔹 <code>/food &lt;name&gt;</code> — Dietary recommendations for condition
                🔹 <code>/track &lt;name&gt;</code> — Track medicine for price drops
                🔹 <code>/mymeds</code> — View your tracked medicine list
                🔹 <code>/help</code> — Show this menu
                """;
        sendMessage(chatId, welcome);
    }

    private void answerCallbackQuery(String callbackId) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/answerCallbackQuery", botToken);
            Map<String, String> body = Map.of("callback_query_id", callbackId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Failed to answer callback query: {}", e.getMessage());
        }
    }

    private void sendMessage(long chatId, String htmlText) {
        sendMessageWithKeyboard(chatId, htmlText, null);
    }

    private void sendMessageWithKeyboard(long chatId, String htmlText, List<List<Map<String, String>>> inlineKeyboard) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("chat_id", chatId);
            bodyMap.put("text", htmlText);
            bodyMap.put("parse_mode", "HTML");

            if (inlineKeyboard != null) {
                bodyMap.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("[TelegramBot] Error sending message to chatId {}: {}", chatId, e.getMessage());
        }
    }
}
