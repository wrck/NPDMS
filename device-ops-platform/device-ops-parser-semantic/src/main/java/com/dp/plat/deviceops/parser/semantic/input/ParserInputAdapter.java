package com.dp.plat.deviceops.parser.semantic.input;

import com.dp.plat.deviceops.parser.semantic.evidence.EvidenceDocument;

import java.io.IOException;
import java.io.InputStream;

public interface ParserInputAdapter {
    String inputFormat();

    EvidenceDocument decode(InputStream input) throws IOException;
}
