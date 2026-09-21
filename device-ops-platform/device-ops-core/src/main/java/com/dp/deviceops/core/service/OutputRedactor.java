package com.dp.deviceops.core.service;

/** Removes current credentials and common key-value credential forms before persistence. */
public final class OutputRedactor {
    private OutputRedactor() { }

    public static String redact(String output, char[] secret, char[] passphrase) {
        try (StreamingOutputRedactor redactor = new StreamingOutputRedactor(secret, passphrase)) {
            return redactor.accept(output == null ? "" : output) + redactor.flush();
        }
    }
}
