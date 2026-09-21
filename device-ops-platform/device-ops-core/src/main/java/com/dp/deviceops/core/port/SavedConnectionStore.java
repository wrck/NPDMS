package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.SavedConnection;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.TransientCredential;

import java.util.List;
import java.util.Optional;

public interface SavedConnectionStore {
    boolean available();

    List<SavedConnection> findAll(String ownerId, String namespace);

    Optional<SavedConnection> find(String ownerId, String namespace, String id);

    SavedConnection create(String ownerId, String namespace, SavedConnectionDraft draft,
                           char[] secret, char[] passphrase);

    default SavedConnection createIdentified(String ownerId, String namespace, String id, SavedConnectionDraft draft,
                                            char[] secret, char[] passphrase) {
        throw new UnsupportedOperationException("identified connection creation is unavailable");
    }

    SavedConnection replace(String ownerId, String namespace, String id, long expectedVersion,
                            SavedConnectionDraft draft, char[] newSecret, char[] newPassphrase);

    SavedConnection rename(String ownerId, String namespace, String id, long expectedVersion,
                           String displayName, String description);

    <T> T withConnection(String ownerId, String namespace, String id,
                         SavedConnectionOperation<T> operation);

    void delete(String ownerId, String namespace, String id, long expectedVersion);

    @FunctionalInterface
    interface SavedConnectionOperation<T> {
        T apply(SavedConnection connection, TransientCredential credential);
    }

    /** Signals an optimistic-lock mismatch for a saved connection. */
    final class VersionConflictException extends IllegalStateException {
        public VersionConflictException(String connectionId) {
            super("saved connection version conflict: " + connectionId);
        }
    }

    /** Signals that a connection is absent from the caller's owner and namespace scope. */
    final class NotFoundException extends IllegalStateException {
        public NotFoundException(String connectionId) {
            super("saved connection not found: " + connectionId);
        }
    }
}
