package de.wasessenwir.ai;

public interface AiProvider {

    String interpret(String systemPrompt, String userMessage);
}
