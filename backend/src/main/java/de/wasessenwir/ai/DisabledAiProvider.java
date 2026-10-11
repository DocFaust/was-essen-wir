package de.wasessenwir.ai;

final class DisabledAiProvider implements AiProvider {

    @Override
    public String interpret(String systemPrompt, String userMessage) {
        throw new IllegalStateException("Der Essensassistent ist deaktiviert.");
    }
}
