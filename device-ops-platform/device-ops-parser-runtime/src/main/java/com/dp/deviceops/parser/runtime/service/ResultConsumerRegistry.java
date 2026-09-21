package com.dp.deviceops.parser.runtime.service;

import java.net.URI;
import java.util.Optional;

@FunctionalInterface
public interface ResultConsumerRegistry {
    Optional<URI> findDestination(String consumerId);
}
