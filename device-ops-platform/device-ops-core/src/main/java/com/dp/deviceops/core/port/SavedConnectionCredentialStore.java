package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.SavedCredential;
import com.dp.deviceops.core.model.TransientCredential;

/**
 * Internal credential boundary exclusively for saved-connection aggregates.
 * It is intentionally separate from the user-managed {@link CredentialStore}.
 */
public interface SavedConnectionCredentialStore {

    boolean available();

    SavedCredential createForConnection(String ownerId, String namespace, String connectionId,
                                        CommandExecutionPort.AuthenticationType authenticationType,
                                        char[] secret, char[] passphrase);

    SavedCredential replaceForConnection(String ownerId, String namespace, String credentialId, String connectionId,
                                         CommandExecutionPort.AuthenticationType authenticationType,
                                         char[] secret, char[] passphrase);

    TransientCredential loadForConnection(String ownerId, String namespace, String credentialId);

    void deleteForConnection(String ownerId, String namespace, String credentialId);
}
