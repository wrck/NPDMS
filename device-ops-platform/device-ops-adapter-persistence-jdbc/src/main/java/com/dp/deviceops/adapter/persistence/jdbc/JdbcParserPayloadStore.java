package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.semantic.input.ParserInputSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

public final class JdbcParserPayloadStore implements ParserPayloadStore {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Supplier<String> payloadIds;

    public JdbcParserPayloadStore(JdbcClient jdbc, Clock clock, Supplier<String> payloadIds) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.payloadIds = Objects.requireNonNull(payloadIds, "payloadIds");
    }

    @Override
    public String put(String mediaType, InputStream content) throws IOException {
        String payloadId = payloadIds.get();
        String text = new String(Objects.requireNonNull(content, "content").readAllBytes(), StandardCharsets.UTF_8);
        jdbc.sql("insert into device_ops_parser_payload(payload_id,media_type,content,created_at) "
                        + "values(:id,:type,:content,:created)")
                .param("id", payloadId).param("type", mediaType).param("content", text)
                .param("created", clock.instant()).update();
        return payloadId;
    }

    @Override
    public String putScoped(String callerNamespace, String mediaType, InputStream content) throws IOException {
        requireScopeValue(callerNamespace, "callerNamespace", 200);
        requireScopeValue(mediaType, "mediaType", 200);
        byte[] bytes = Objects.requireNonNull(content, "content").readAllBytes();
        String payloadId = scopedId(callerNamespace, mediaType, bytes);
        try {
            jdbc.sql("insert into device_ops_parser_payload "
                            + "(payload_id,media_type,content,created_at,caller_namespace,scoped_content) "
                            + "values(:id,:type,'',:created,:namespace,:content)")
                    .param("id", payloadId).param("type", mediaType).param("created", clock.instant())
                    .param("namespace", callerNamespace).param("content", bytes).update();
        } catch (DuplicateKeyException exception) {
            // The primary key serializes concurrent retries. Never treat an unrelated collision as ownership.
            boolean same = jdbc.sql("select caller_namespace,media_type,scoped_content "
                            + "from device_ops_parser_payload where payload_id=:id")
                    .param("id", payloadId).query((rs, rowNum) ->
                            callerNamespace.equals(rs.getString("caller_namespace"))
                                    && mediaType.equals(rs.getString("media_type"))
                                    && Arrays.equals(bytes, rs.getBytes("scoped_content")))
                    .optional().orElse(false);
            if (!same) {
                throw exception;
            }
        }
        return payloadId;
    }

    @Override
    public boolean isOwnedBy(String callerNamespace, String inputRef) {
        if (callerNamespace == null || callerNamespace.isBlank() || inputRef == null || inputRef.isBlank()) {
            return false;
        }
        boolean scoped = jdbc.sql("select caller_namespace from device_ops_parser_payload where payload_id=:id")
                .param("id", inputRef).query((rs, rowNum) -> callerNamespace.equals(rs.getString("caller_namespace")))
                .optional().orElse(false);
        if (scoped) {
            return true;
        }
        // Compare in Java as well, so case-insensitive database collations cannot grant another caller access.
        return jdbc.sql("select caller_namespace from device_ops_parse_task "
                        + "where caller_namespace=:namespace and input_payload_id=:id")
                .param("namespace", callerNamespace).param("id", inputRef).query(String.class).list()
                .stream().anyMatch(callerNamespace::equals);
    }

    @Override
    public ParserInputSource open(String inputRef) {
        return () -> new ByteArrayInputStream(jdbc.sql(
                        "select content,scoped_content from device_ops_parser_payload where payload_id=:id")
                .param("id", inputRef).query((rs, rowNum) -> {
                    byte[] scoped = rs.getBytes("scoped_content");
                    return scoped == null ? rs.getString("content").getBytes(StandardCharsets.UTF_8) : scoped;
                }).single());
    }

    private static String scopedId(String namespace, String mediaType, byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateFramed(digest, namespace.getBytes(StandardCharsets.UTF_8));
            updateFramed(digest, mediaType.getBytes(StandardCharsets.UTF_8));
            updateFramed(digest, content);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateFramed(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }

    private static void requireScopeValue(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
    }
}
