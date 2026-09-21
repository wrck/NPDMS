package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.service.ResultConsumerRegistry;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public final class ConfiguredResultConsumerRegistry implements ResultConsumerRegistry {

    private final ParserControlProperties properties;

    public ConfiguredResultConsumerRegistry(ParserControlProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public Optional<URI> findDestination(String consumerId) {
        return Optional.ofNullable(properties.getResultConsumers().get(consumerId));
    }
}
