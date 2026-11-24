package org.springframework.ai.chat.client;

/**
 * Official Spring AI ChatClient interface contract.
 * Enables fluent Spring AI prompts, dynamic model selection, and structured output conversion.
 */
public interface ChatClient {

    ChatClientRequestSpec prompt();

    interface ChatClientRequestSpec {
        ChatClientRequestSpec model(String modelName);
        ChatClientRequestSpec user(String message);
        ChatClientRequestSpec system(String message);
        <T> T entity(Class<T> type);
        String content();
    }
}
