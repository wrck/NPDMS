package com.dp.plat.deviceops.core.port;

import java.util.List;

/** Resolves durable target ids in insertion order after task submission. */
public interface CollectionTargetReferencePort {
    List<Long> findTargetIds(String collectionId);
}
