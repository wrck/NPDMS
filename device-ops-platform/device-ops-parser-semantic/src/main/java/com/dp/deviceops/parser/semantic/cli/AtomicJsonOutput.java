package com.dp.deviceops.parser.semantic.cli;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class AtomicJsonOutput {

    public void write(Path output, byte[] content) throws IOException {
        Path absolute = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("output parent is unavailable");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString() + '.', ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
