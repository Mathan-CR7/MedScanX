package com.kovanlabs.ai_medicine_assistant.config;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class SpringAiConfig {
    @Value("${spring.ai.google.genai.api-key:}")
    private String geminiApiKey;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.0-flash}")
    private String defaultModelName;

    @Bean
    public ChatClient chatClient() {
        // SNAKE_CASE: Gemini returns snake_case JSON (e.g. common_side_effects)
        // This maps it automatically to Java camelCase fields (commonSideEffects).
        // FAIL_ON_UNKNOWN_PROPERTIES=false: silently ignore any extra fields Gemini adds.
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        log.info("[SpringAiConfig] Initializing Google Gemini ChatClient bean " +
                 "(model: '{}', API key present: {}).", defaultModelName, !geminiApiKey.isBlank());
        return new GeminiChatClient(geminiApiKey, defaultModelName, objectMapper);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gemini ChatClient Implementation
    // ─────────────────────────────────────────────────────────────────────────

    private static class GeminiChatClient implements ChatClient {
        private final String apiKey;
        private final String defaultModel;
        private final ObjectMapper mapper;
        private final HttpClient httpClient;

        GeminiChatClient(String apiKey, String defaultModel, ObjectMapper mapper) {
            this.apiKey = apiKey;
            this.defaultModel = defaultModel;
            this.mapper = mapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
        }

        @Override
        public ChatClientRequestSpec prompt()
        {
            return new GeminiRequestSpec(apiKey, defaultModel, mapper, httpClient);
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Gemini Request Builder (fluent API)
    // ─────────────────────────────────────────────────────────────────────────
    private static class GeminiRequestSpec implements ChatClient.ChatClientRequestSpec {
        private final String apiKey;
        private final String defaultModel;
        private final ObjectMapper mapper;
        private final HttpClient httpClient;

        private String customModel = null;
        private String userPrompt = "";
        private String systemPrompt = "";

        GeminiRequestSpec(String apiKey, String defaultModel, ObjectMapper mapper, HttpClient httpClient) {
            this.apiKey = apiKey;
            this.defaultModel = defaultModel;
            this.mapper = mapper;
            this.httpClient = httpClient;
        }

        @Override
        public ChatClient.ChatClientRequestSpec model(String modelName) {
            if (modelName != null && !modelName.isBlank()) this.customModel = modelName;
            return this;
        }

        @Override
        public ChatClient.ChatClientRequestSpec user(String message) {
            this.userPrompt = message;
            return this;
        }

        @Override
        public ChatClient.ChatClientRequestSpec system(String message) {
            this.systemPrompt = message;
            return this;
        }

        @Override
        public <T> T entity(Class<T> type) {
            String raw = content();
            if (raw == null || raw.isBlank()) return null;

            String cleanedRaw = raw.trim();
            // Strip markdown code fences if present
            if (cleanedRaw.contains("```json")) {
                cleanedRaw = cleanedRaw.substring(cleanedRaw.indexOf("```json") + 7);
                if (cleanedRaw.contains("```")) cleanedRaw = cleanedRaw.substring(0, cleanedRaw.indexOf("```"));
            } else if (cleanedRaw.contains("```")) {
                cleanedRaw = cleanedRaw.substring(cleanedRaw.indexOf("```") + 3);
                if (cleanedRaw.contains("```")) cleanedRaw = cleanedRaw.substring(0, cleanedRaw.indexOf("```"));
            }

            cleanedRaw = cleanedRaw.trim();
            // Robust extraction: if it doesn't start with '{' but contains it, find outermost braces
            if (!cleanedRaw.startsWith("{") && cleanedRaw.contains("{") && cleanedRaw.contains("}")) {
                int firstBrace = cleanedRaw.indexOf("{");
                int lastBrace = cleanedRaw.lastIndexOf("}");
                if (lastBrace > firstBrace) {
                    cleanedRaw = cleanedRaw.substring(firstBrace, lastBrace + 1);
                }
            }

            try {
                return mapper.readValue(cleanedRaw, type);
            } catch (Exception e) {
                log.error("[GeminiChatClient] JSON mapping failed for {}. Error: {}", type.getSimpleName(), e.getMessage());
                log.error("[GeminiChatClient] Raw response from Gemini was: \n{}", raw);
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public String content() {
            String model = (customModel != null && !customModel.isBlank()) ? customModel : defaultModel;

            if (apiKey == null || apiKey.isBlank()) {
                log.warn("[GeminiChatClient] No API key configured — skipping Gemini call.");
                return "";
            }

            try {
                log.info("[GeminiChatClient] Calling Gemini model '{}'.", model);

                // Build Gemini generateContent request body
                // Gemini REST API format:
                //   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
                //   Body: { "system_instruction": { "parts": [{ "text": "..." }] },
                //           "contents": [{ "parts": [{ "text": "..." }] }],
                //           "generationConfig": { "temperature": 0.0, "responseMimeType": "application/json" } }

                Map<String, Object> systemInstruction = Map.of(
                        "parts", List.of(Map.of("text",
                                systemPrompt.isBlank() ? "You are a helpful medical assistant." : systemPrompt))
                );

                Map<String, Object> userContent = Map.of(
                        "parts", List.of(Map.of("text",
                                userPrompt + "\n\nIMPORTANT: Respond ONLY with valid JSON. No markdown, no explanation, no code fences."))
                );

                Map<String, Object> generationConfig = Map.of(
                        "temperature", 0.0,
                        "responseMimeType", "application/json"
                );

                Map<String, Object> body = Map.of(
                        "system_instruction", systemInstruction,
                        "contents", List.of(userContent),
                        "generationConfig", generationConfig
                );

                String requestBody = mapper.writeValueAsString(body);

                String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent?key=" + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    var json = mapper.readTree(response.body());
                    // Gemini response: candidates[0].content.parts[0].text
                    return json.path("candidates").get(0)
                               .path("content").path("parts").get(0)
                               .path("text").asText();
                } else {
                    log.warn("[GeminiChatClient] Gemini API returned HTTP {}: {}",
                            response.statusCode(), response.body());
                }

            } catch (Exception e) {
                log.warn("[GeminiChatClient] Gemini API call failed: {}", e.getMessage());
            }
            return "";
        }
    }
}