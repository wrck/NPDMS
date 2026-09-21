package com.dp.deviceops.adapter.web.callback;

import com.dp.deviceops.core.port.CallbackOutboxPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** The immutable outbox payload is the log; delivery never re-reads mutable execution state. */
final class NpdmsCallbackSender {
    static boolean send(CallbackOutboxPort.Event event, CallbackProperties properties, RestClient http) throws Exception {
        var json = new ObjectMapper();
        var payload = json.readTree(event.payload());
        String namespace = payload.path("namespace").asText();
        if (!namespace.startsWith(properties.getNpdmsNamespacePrefix())) return false;
        long tenantId = Long.parseLong(namespace.substring(properties.getNpdmsNamespacePrefix().length()));
        if (tenantId < 0) return false;
        byte[] log = event.payload().getBytes(StandardCharsets.UTF_8);
        if (log.length == 0 || log.length > 20 * 1024 * 1024) return false;
        String fileHash = sha(log);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("tenantId", tenantId);
        metadata.put("callbackId", event.eventId());
        metadata.put("platformTaskId", payload.path("externalRequestId").asText());
        metadata.put("externalTaskId", payload.path("collectionId").asText());
        metadata.put("externalStatus", payload.path("truncated").asBoolean() ? "FAILED" : payload.path("status").asText());
        metadata.put("sequence", 1);
        metadata.put("resultVersion", 1);
        metadata.put("sizeBytes", log.length);
        metadata.put("sha256", fileHash);
        metadata.put("failureCategory", failureCategory(payload));
        metadata.put("traceId", event.eventId());
        String raw = json.writeValueAsString(metadata);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String canonical = timestamp + "\n" + event.eventId() + "\n"
                + sha(raw.getBytes(StandardCharsets.UTF_8)) + "\n" + fileHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getNpdmsSigningKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        var body = new LinkedMultiValueMap<String, Object>();
        var metadataHeaders = new HttpHeaders();
        metadataHeaders.setContentType(MediaType.TEXT_PLAIN);
        body.add("metadata", new HttpEntity<>(raw, metadataHeaders));
        body.add("log", new ByteArrayResource(log) {
            @Override public String getFilename() { return "collection.json"; }
        });
        String ack = http.post().uri(properties.getNpdmsDestination())
                .header("tenant-id", Long.toString(tenantId))
                .header("X-DAC-Timestamp", timestamp).header("X-DAC-Nonce", event.eventId())
                .header("X-DAC-Signature", signature).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(String.class);
        if (ack == null) return false;
        var response = json.readTree(ack);
        return "ACKNOWLEDGED".equals(response.path("status").asText())
                && event.eventId().equals(response.path("callbackId").asText())
                && response.path("receiptId").asLong() > 0;
    }

    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    static String failureCategory(com.fasterxml.jackson.databind.JsonNode payload) {
        if (payload.path("truncated").asBoolean()) return "OUTPUT_TRUNCATED";
        if (java.util.Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "CANCELLED").contains(payload.path("status").asText())) return null;
        String outcome = payload.path("outcome").asText();
        if ("COMMAND_REJECTED".equals(outcome)) return outcome;
        for (var code : com.dp.deviceops.core.model.ConnectionFailure.Code.values()) {
            if (code.name().equals(outcome)) return outcome;
        }
        return "EXECUTION_FAILED";
    }
}
