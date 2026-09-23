package com.frontrow.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frontrow.config.FrontrowProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Built by hand so the OpenAI starter stays off the classpath and boot never
 * requires an API key. Selected only when the provider is openai and a key is set.
 */
public class OpenAiRiskScorer implements RiskScorer {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OpenAiRiskScorer(FrontrowProperties properties) {
        OpenAiApi api = OpenAiApi.builder().apiKey(properties.getAi().getApiKey()).build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getAi().getModel())
                        .temperature(0.0)
                        .build())
                .build();
        this.chatClient = ChatClient.builder(model).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public RiskAssessment score(RiskSignals signals) {
        String raw = chatClient.prompt().user(prompt(signals)).call().content();
        return parse(raw);
    }

    private static String prompt(RiskSignals signals) {
        return """
                You are Frontrow Risk. Reply with JSON only: {"decision":"ALLOW|CHALLENGE|REJECT","reason":"short_code"}.
                Policy: userAttempts>=20 REJECT USER_ATTEMPT_RATE, seatAttempts>=40 REJECT SEAT_ATTEMPT_RATE, \
                distinctSeats>=6 CHALLENGE SEAT_SPRAY, inFlightHolds>=2 CHALLENGE TOO_MANY_HOLDS, else ALLOW OK.
                Signals: userAttempts=%d, seatAttempts=%d, distinctSeats=%d, inFlightHolds=%d.
                """.formatted(
                signals.userAttempts(),
                signals.seatAttempts(),
                signals.distinctSeats(),
                signals.inFlightHolds());
    }

    private RiskAssessment parse(String raw) {
        try {
            String json = raw == null ? "" : raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalStateException("Risk model did not return JSON");
            }
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            RiskDecision decision = RiskDecision.valueOf(node.path("decision").asText());
            String reason = node.path("reason").asText("");
            if (reason.isBlank()) {
                throw new IllegalStateException("Risk model omitted a reason");
            }
            return new RiskAssessment(decision, reason, false);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse risk model output", exception);
        }
    }
}
