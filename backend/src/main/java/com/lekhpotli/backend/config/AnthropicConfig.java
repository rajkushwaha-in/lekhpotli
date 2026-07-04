package com.lekhpotli.backend.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    // Boot even without a real key so the server starts; ChatController surfaces
    // a clear error to the UI the moment a chat request actually hits the API.
    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("sk-ant-your-key-here")) {
            log.warn("ANTHROPIC_API_KEY is not set. Add your real key to backend/.env "
                    + "(see backend/.env.example) before sending chat messages.");
        }
        return AnthropicOkHttpClient.builder().apiKey(apiKey == null ? "" : apiKey).build();
    }
}
