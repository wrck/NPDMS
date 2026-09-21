package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dp.deviceops.adapter.persistence.jdbc.ParserRegistryPersistenceTest.NOW;
import static org.junit.jupiter.api.Assertions.*;

class ParserScopedPayloadJdbcTest {

    @Test
    void scopedBytesAreStableOpaqueAndNamespaceMediaAndContentSensitive() throws Exception {
        Fixture fixture = fixture();
        byte[] bytes = new byte[] {0, 1, (byte) 0xff, (byte) 0xfe, 10};
        String first = putScoped(fixture.store, "tenant-a", "application/octet-stream", bytes);
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertEquals(first, putScoped(fixture.store, "tenant-a", "application/octet-stream", bytes));
        assertNotEquals(first, putScoped(fixture.store, "tenant-b", "application/octet-stream", bytes));
        assertNotEquals(first, putScoped(fixture.store, "tenant-a", "application/json", bytes));
        assertNotEquals(first, putScoped(fixture.store, "tenant-a", "application/octet-stream", new byte[] {0, 1}));
        try (InputStream content = fixture.store.open(first).openStream()) {
            assertArrayEquals(bytes, content.readAllBytes());
        }
        assertEquals(4, count(fixture));
        assertEquals(0, fixture.ids.get(), "scoped puts must not allocate random payload ids");
    }

    @Test
    void framingPreventsNamespaceMediaBoundaryCollisions() throws Exception {
        Fixture fixture = fixture();
        byte[] bytes = "same".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(putScoped(fixture.store, "a", "bc", bytes),
                putScoped(fixture.store, "ab", "c", bytes));
    }

    @Test
    void scopedOwnershipIsImmediatelyAvailableAndFailsClosedForOtherCallers() throws Exception {
        Fixture fixture = fixture();
        String ref = putScoped(fixture.store, "tenant-a", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));
        assertTrue(isOwnedBy(fixture.store, "tenant-a", ref));
        assertFalse(isOwnedBy(fixture.store, "tenant-b", ref));
        assertFalse(isOwnedBy(fixture.store, "Tenant-a", ref));
        assertFalse(isOwnedBy(fixture.store, "tenant-a", "missing"));
        assertFalse(isOwnedBy(fixture.store, null, ref));
        assertFalse(isOwnedBy(fixture.store, "", ref));
        String internal = fixture.store.put("text/plain", new ByteArrayInputStream(new byte[] {1}));
        assertFalse(isOwnedBy(fixture.store, "tenant-a", internal), "unowned internal payload must stay private");
    }

    @Test
    void concurrentIndependentStoresReplayOnePayloadAndInternalPutRemainsDistinct() throws Exception {
        Fixture fixture = fixture();
        var otherStore = new JdbcParserPayloadStore(fixture.jdbc, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "unused");
        CyclicBarrier barrier = new CyclicBarrier(8);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 8; i++) {
                ParserPayloadStore store = i % 2 == 0 ? fixture.store : otherStore;
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return putScoped(store, "tenant-a", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));
                }));
            }
            var ids = new java.util.HashSet<String>();
            for (var future : futures) {
                ids.add(future.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, ids.size());
        }
        assertEquals(1, count(fixture));
        String first = fixture.store.put("text/plain", new ByteArrayInputStream(new byte[] {1}));
        String second = fixture.store.put("text/plain", new ByteArrayInputStream(new byte[] {1}));
        assertNotEquals(first, second);
        assertEquals(3, count(fixture));
    }

    private static String putScoped(ParserPayloadStore store, String namespace, String media, byte[] bytes)
            throws Exception {
        return assertDoesNotThrow(() -> store.putScoped(namespace, media, new ByteArrayInputStream(bytes)));
    }

    private static boolean isOwnedBy(ParserPayloadStore store, String namespace, String ref) {
        return store.isOwnedBy(namespace, ref);
    }

    private static int count(Fixture fixture) {
        return fixture.jdbc.sql("select count(*) from device_ops_parser_payload").query(Integer.class).single();
    }

    private static Fixture fixture() {
        JdbcClient jdbc = JdbcClient.create(ParserRegistryPersistenceTest.migrated("payload-" + UUID.randomUUID()));
        AtomicInteger ids = new AtomicInteger();
        return new Fixture(jdbc, new JdbcParserPayloadStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "internal-" + ids.incrementAndGet()), ids);
    }

    private record Fixture(JdbcClient jdbc, JdbcParserPayloadStore store, AtomicInteger ids) { }
}
