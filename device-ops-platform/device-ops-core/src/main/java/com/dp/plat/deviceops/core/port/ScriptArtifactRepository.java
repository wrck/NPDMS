package com.dp.plat.deviceops.core.port;

import com.dp.plat.deviceops.core.model.ScriptArtifact;

import java.util.Optional;

/** Stores immutable, externally namespaced script versions only. */
public interface ScriptArtifactRepository {

    void save(String namespace, ScriptArtifact artifact);

    Optional<ScriptArtifact> find(String namespace, String scriptKey, String version);
}
