package de.wasessenwir.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled;
    private String provider = "ollama";
    private int maxInputLength = 500;
    private Ollama ollama = new Ollama();
    private OpenAi openai = new OpenAi();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String provider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int maxInputLength() {
        return maxInputLength;
    }

    public void setMaxInputLength(int maxInputLength) {
        this.maxInputLength = maxInputLength;
    }

    public Ollama ollama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public OpenAi openai() {
        return openai;
    }

    public void setOpenai(OpenAi openai) {
        this.openai = openai;
    }

    public static class Ollama {

        private String baseUrl = "http://127.0.0.1:11434";
        private String model = "qwen3.5:9b";
        private Duration timeout = Duration.ofSeconds(30);

        public String baseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String model() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration timeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class OpenAi {

        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = "gpt-4.1-mini";
        private Duration timeout = Duration.ofSeconds(30);

        public String baseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String apiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String model() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Duration timeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
