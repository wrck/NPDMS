package com.dp.plat.deviceops.parser.semantic.input;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface ParserInputSource {
    InputStream openStream() throws IOException;
}
