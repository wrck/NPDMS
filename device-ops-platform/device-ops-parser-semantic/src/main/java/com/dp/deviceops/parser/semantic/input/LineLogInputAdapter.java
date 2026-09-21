package com.dp.deviceops.parser.semantic.input;

import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LineLogInputAdapter implements ParserInputAdapter {

    @Override
    public String inputFormat() {
        return "line-log/v1";
    }

    @Override
    public EvidenceDocument decode(InputStream input) throws IOException {
        List<EvidenceUnit> units = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(input, "input"), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                units.add(new EvidenceUnit(lineNumber, "LOG_RECORD",
                        Map.of("lineNumber", Integer.toString(lineNumber)),
                        List.of(line), List.of(), false));
            }
        }
        return new EvidenceDocument(units);
    }
}
