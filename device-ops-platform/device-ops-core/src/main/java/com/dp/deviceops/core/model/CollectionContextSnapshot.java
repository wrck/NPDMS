package com.dp.deviceops.core.model;

import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable project and device facts supplied when a collection is submitted.
 */
public final class CollectionContextSnapshot {

    private static final int MAX_EXTENSION_VALUE_LENGTH = 1_024;
    private static final Set<String> ALLOWED_EXTENSION_KEYS = Set.of(
            "region", "site", "sitecode", "room", "rack", "position", "role", "assettag",
            "serialnumber", "softwareversion", "devicetype", "managementdomain");

    private final ProjectSnapshot project;
    private final DeviceSnapshot device;
    private final Map<String, String> extensions;

    private CollectionContextSnapshot(ProjectSnapshot project, DeviceSnapshot device, Map<String, String> extensions) {
        this.project = project;
        this.device = device;
        this.extensions = copyAllowedExtensions(extensions);
    }

    public static CollectionContextSnapshot of(String namespace, String projectKey, String projectName, String projectCode,
                                               String deviceKey, String deviceName, String vendor, String model,
                                               Map<String, String> extensions) {
        return new CollectionContextSnapshot(new ProjectSnapshot(namespace, projectKey, projectName, projectCode),
                new DeviceSnapshot(deviceKey, deviceName, vendor, model), extensions);
    }

    public static CollectionContextSnapshot ofOptional(ProjectSnapshot project, DeviceSnapshot device,
                                                       Map<String, String> extensions) {
        return new CollectionContextSnapshot(project, device, extensions);
    }

    public Optional<ProjectSnapshot> project() {
        return Optional.ofNullable(project);
    }

    public Optional<DeviceSnapshot> device() {
        return Optional.ofNullable(device);
    }

    public Map<String, String> extensions() {
        return extensions;
    }

    public record ProjectSnapshot(String namespace, String projectKey, String projectName, String projectCode) {

        public ProjectSnapshot {
            namespace = requireKey(namespace, "namespace");
            projectKey = requireKey(projectKey, "projectKey");
            projectName = normalizeDisplay(projectName);
            projectCode = normalizeDisplay(projectCode);
        }
    }

    public record DeviceSnapshot(String deviceKey, String deviceName, String vendor, String model) {

        public DeviceSnapshot {
            deviceKey = requireKey(deviceKey, "deviceKey");
            deviceName = normalizeDisplay(deviceName);
            vendor = normalizeDisplay(vendor);
            model = normalizeDisplay(model);
        }
    }

    private static String requireKey(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String normalizeDisplay(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> copyAllowedExtensions(Map<String, String> extensions) {
        Objects.requireNonNull(extensions, "extensions must not be null");
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            String normalizedKey = normalizeExtensionKey(entry.getKey());
            if (normalizedKey.isBlank() || !ALLOWED_EXTENSION_KEYS.contains(normalizedKey)) {
                throw new IllegalArgumentException("extension key is not allowed");
            }
            String value = Objects.requireNonNull(entry.getValue(), "extension value must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException("extension value must not be blank");
            }
            if (value.length() > MAX_EXTENSION_VALUE_LENGTH) {
                throw new IllegalArgumentException("extension value exceeds maximum length");
            }
            if (isPrivateKeyPayload(value)) {
                throw new IllegalArgumentException("extensions must not contain private key material");
            }
        }
        return Map.copyOf(extensions);
    }

    private static String normalizeExtensionKey(String key) {
        Objects.requireNonNull(key, "extension key must not be null");
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean isPrivateKeyPayload(String value) {
        if (value == null) {
            return false;
        }
        String upperCaseValue = value.toUpperCase(Locale.ROOT);
        return upperCaseValue.contains("-----BEGIN") && upperCaseValue.contains("PRIVATE KEY-----");
    }
}
