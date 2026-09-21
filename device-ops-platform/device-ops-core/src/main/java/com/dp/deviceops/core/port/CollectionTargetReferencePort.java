package com.dp.deviceops.core.port;

import java.util.List;

/** Resolves durable target ids in insertion order after task submission. */
public interface CollectionTargetReferencePort {
    List<Long> findTargetIds(String collectionId);
}
