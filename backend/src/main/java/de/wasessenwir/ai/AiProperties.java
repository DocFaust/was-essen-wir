package de.wasessenwir.ai;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled;
    private String provider = "ollama";
    private int maxInputLength = 500;
    private int maxOutputTokens = 1_024;
    private boolean allowFixture;
    private RateLimit rateLimit = new RateLimit();
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

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean allowFixture() {
        return allowFixture;
    }

    public void setAllowFixture(boolean allowFixture) {
        this.allowFixture = allowFixture;
    }

    public RateLimit rateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit == null ? new RateLimit() : rateLimit;
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

    public String normalizedProvider() {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    public void validate() {
        String name = normalizedProvider();
        if (enabled && !name.equals("ollama") && !name.equals("openai") && !name.equals("fixture")) {
            throw new IllegalStateException("Unbekannter KI-Provider: " + provider);
        }
        if (maxInputLength < 1 || maxInputLength > 2_000) {
            throw new IllegalStateException("AI_MAX_INPUT_LENGTH muss zwischen 1 und 2000 liegen.");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > 8_192) {
            throw new IllegalStateException("AI_MAX_OUTPUT_TOKENS muss zwischen 1 und 8192 liegen.");
        }
        rateLimit.validate();
        validateTimeout("OLLAMA_TIMEOUT", ollama.timeout());
        validateTimeout("OPENAI_TIMEOUT", openai.timeout());
    }

    private static void validateTimeout(String name, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(name + " muss positiv sein.");
        }
        if (timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException(name + " darf höchstens 2 Minuten betragen.");
        }
    }

    public static class RateLimit {

        private int perUserPerMinute = 5;
        private int perUserPerDay = 50;
        private int globalPerDay = 150;
        private int maxConcurrentPerUser = 1;
        private int maxConcurrentGlobal = 3;
        private int maxTrackedUsers = 1_000;

        public int perUserPerMinute() {
            return perUserPerMinute;
        }

        public void setPerUserPerMinute(int perUserPerMinute) {
            this.perUserPerMinute = perUserPerMinute;
        }

        public int perUserPerDay() {
            return perUserPerDay;
        }

        public void setPerUserPerDay(int perUserPerDay) {
            this.perUserPerDay = perUserPerDay;
        }

        public int globalPerDay() {
            return globalPerDay;
        }

        public void setGlobalPerDay(int globalPerDay) {
            this.globalPerDay = globalPerDay;
        }

        public int maxConcurrentPerUser() {
            return maxConcurrentPerUser;
        }

        public void setMaxConcurrentPerUser(int maxConcurrentPerUser) {
            this.maxConcurrentPerUser = maxConcurrentPerUser;
        }

        public int maxConcurrentGlobal() {
            return maxConcurrentGlobal;
        }

        public void setMaxConcurrentGlobal(int maxConcurrentGlobal) {
            this.maxConcurrentGlobal = maxConcurrentGlobal;
        }

        public int maxTrackedUsers() {
            return maxTrackedUsers;
        }

        public void setMaxTrackedUsers(int maxTrackedUsers) {
            this.maxTrackedUsers = maxTrackedUsers;
        }

        void validate() {
            requirePositive("AI_RATE_LIMIT_PER_USER_PER_MINUTE", perUserPerMinute);
            requirePositive("AI_RATE_LIMIT_PER_USER_PER_DAY", perUserPerDay);
            requirePositive("AI_RATE_LIMIT_GLOBAL_PER_DAY", globalPerDay);
            requirePositive("AI_MAX_CONCURRENT_PER_USER", maxConcurrentPerUser);
            requirePositive("AI_MAX_CONCURRENT_GLOBAL", maxConcurrentGlobal);
            requirePositive("AI_RATE_LIMIT_MAX_TRACKED_USERS", maxTrackedUsers);
            if (maxConcurrentPerUser > maxConcurrentGlobal) {
                throw new IllegalStateException(
                        "AI_MAX_CONCURRENT_PER_USER darf AI_MAX_CONCURRENT_GLOBAL nicht überschreiten.");
            }
            if (perUserPerDay > globalPerDay) {
                throw new IllegalStateException(
                        "AI_RATE_LIMIT_PER_USER_PER_DAY darf AI_RATE_LIMIT_GLOBAL_PER_DAY nicht überschreiten.");
            }
        }

        private static void requirePositive(String name, int value) {
            if (value < 1) {
                throw new IllegalStateException(name + " muss mindestens 1 sein.");
            }
        }
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
