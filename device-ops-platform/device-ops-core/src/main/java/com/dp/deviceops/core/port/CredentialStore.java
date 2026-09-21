package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.SavedCredential;
import com.dp.deviceops.core.model.TransientCredential;

import java.util.List;
import java.util.Optional;

/** Public boundary for credentials explicitly managed by the user. */
public interface CredentialStore {

    boolean available();

    SavedCredential save(String ownerId, String namespace, String name,
                         CommandExecutionPort.AuthenticationType authenticationType,
                         char[] secret, char[] passphrase);

    SavedCredential replace(String ownerId, String namespace, String id, String name,
                            CommandExecutionPort.AuthenticationType authenticationType,
                            char[] secret, char[] passphrase);

    List<SavedCredential> findAll(String ownerId, String namespace);

    Optional<SavedCredential> find(String ownerId, String namespace, String id);

    <T> T withCredential(String ownerId, String namespace, String id,
                         TransientCredential.CredentialOperation<T> operation);

    void delete(String ownerId, String namespace, String id);

    final class UnavailableException extends IllegalStateException {
        public UnavailableException() {
            super("credential storage is disabled because no master key is configured");
        }
    }
}
