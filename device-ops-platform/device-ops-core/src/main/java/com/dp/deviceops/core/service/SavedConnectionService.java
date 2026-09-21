package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.SavedConnection;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.SavedConnectionStore;

import java.util.Objects;

/** Verifies remote reachability before persisting a saved connection or credential replacement. */
public final class SavedConnectionService {
    private final SavedConnectionStore store;
    private final ConnectionTestService tests;

    public SavedConnectionService(SavedConnectionStore store, ConnectionTestService tests) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.tests = Objects.requireNonNull(tests, "tests must not be null");
    }

    public SaveResult verifyAndCreate(String ownerId, String namespace,
                                      SavedConnectionDraft draft, TransientCredential proposed) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(proposed, "proposed must not be null");
        try (proposed) {
            ConnectionTestService.TestResult test = tests.test(draft.connection(), proposed);
            if (!test.reachable()) {
                return new SaveResult(false, test, null);
            }
            SavedConnection connection = proposed.withCredentials((secret, passphrase) ->
                    store.create(ownerId, namespace, draft, secret, passphrase));
            return new SaveResult(true, test, connection);
        }
    }

    public SaveResult verifyAndReplace(String ownerId, String namespace, String id,
                                       long expectedVersion, SavedConnectionDraft draft,
                                       TransientCredential proposedOrNull) {
        Objects.requireNonNull(draft, "draft must not be null");
        requireExisting(ownerId, namespace, id);
        if (proposedOrNull == null) {
            ConnectionTestService.TestResult test = store.withConnection(ownerId, namespace, id,
                    (existing, credential) -> tests.test(draft.connection(), credential));
            if (!test.reachable()) {
                return new SaveResult(false, test, null);
            }
            SavedConnection connection = store.replace(ownerId, namespace, id, expectedVersion,
                    draft, null, null);
            return new SaveResult(true, test, connection);
        }
        try (proposedOrNull) {
            ConnectionTestService.TestResult test = tests.test(draft.connection(), proposedOrNull);
            if (!test.reachable()) {
                return new SaveResult(false, test, null);
            }
            SavedConnection connection = proposedOrNull.withCredentials((secret, passphrase) ->
                    store.replace(ownerId, namespace, id, expectedVersion, draft, secret, passphrase));
            return new SaveResult(true, test, connection);
        }
    }

    public SaveResult verifyAndCreateIdentified(String ownerId, String namespace, String id,
                                                SavedConnectionDraft draft, TransientCredential proposed) {
        try (proposed) {
            var existing = store.find(ownerId, namespace, id);
            if (existing.isPresent()) return replay(existing.get(), draft);
            var test = tests.test(draft.connection(), proposed);
            if (!test.reachable()) return new SaveResult(false, test, null);
            try {
                var saved = proposed.withCredentials((secret, passphrase) ->
                        store.createIdentified(ownerId, namespace, id, draft, secret, passphrase));
                return new SaveResult(true, test, saved);
            } catch (RuntimeException failure) {
                // A concurrent identical request may already have committed. Never replace its secret on replay.
                var winner = store.find(ownerId, namespace, id);
                if (winner.isPresent()) return replay(winner.get(), draft);
                throw failure;
            }
        }
    }

    private SaveResult replay(SavedConnection saved, SavedConnectionDraft draft) {
        if (!saved.connection().equals(draft.connection()) || !saved.displayName().equals(draft.displayName())) {
            throw new SavedConnectionStore.VersionConflictException(saved.id());
        }
        return new SaveResult(true, null, saved);
    }

    private void requireExisting(String ownerId, String namespace, String id) {
        if (store.find(ownerId, namespace, id).isEmpty()) {
            throw new SavedConnectionStore.NotFoundException(id);
        }
    }

    public record SaveResult(boolean saved, ConnectionTestService.TestResult test,
                             SavedConnection connection) {
    }
}
