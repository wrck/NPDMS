package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.semantic.input.ParserInputSource;

import java.io.IOException;
import java.io.InputStream;

public interface ParserPayloadStore {
    String put(String mediaType, InputStream content) throws IOException;

    /** Stores exact bytes under a deterministic, namespace- and media-scoped opaque reference. */
    String putScoped(String callerNamespace, String mediaType, InputStream content) throws IOException;

    /** True only for scoped payloads or existing task-owned input references in this namespace. */
    boolean isOwnedBy(String callerNamespace, String inputRef);

    ParserInputSource open(String inputRef);
}
