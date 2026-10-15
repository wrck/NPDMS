package com.dp.deviceops.core.model;

/** Optional, signed purpose. Command execution retains the existing whole-batch timeout. */
public record CollectionExecutionOptions(String purpose) {
    public CollectionExecutionOptions {
        if (!"PRECHECK".equals(purpose) && !"INSPECTION".equals(purpose)) throw new IllegalArgumentException("unsupported collection purpose");
    }
    public boolean precheck() { return "PRECHECK".equals(purpose); }
    public void validateScript(String script) {
        if (precheck()) {
            if (script == null || !script.isEmpty()) throw new IllegalArgumentException("precheck must not execute commands");
        } else if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("inspection commands are required");
        }
    }
    /** This framing matches the platform signer. */
    public String authorizationBinding() {
        String version = "collection-purpose-v1";
        return version.length() + ":" + version + purpose.length() + ":" + purpose;
    }
}
