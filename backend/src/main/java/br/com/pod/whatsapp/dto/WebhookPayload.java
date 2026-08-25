package br.com.pod.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
    String object,
    List<Entry> entry
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(List<Change> changes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(Value value, String field) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(List<Message> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String from, Text text, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Text(String body) {}
}
