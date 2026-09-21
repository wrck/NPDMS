package com.dp.deviceops.parser.runtime.model;

public record WorkerCapability(String engineVersion, String extensionId, String extensionVersion) {

    public WorkerCapability {
        engineVersion = ModelSupport.requireText(engineVersion, "engineVersion");
        if ((extensionId == null) != (extensionVersion == null)) {
            throw new IllegalArgumentException("extensionId and extensionVersion must be provided together");
        }
        extensionId = ModelSupport.optionalText(extensionId, "extensionId");
        extensionVersion = ModelSupport.optionalText(extensionVersion, "extensionVersion");
    }

    public boolean supportsExtension() {
        return extensionId != null;
    }
}
