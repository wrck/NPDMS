package cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CutoverLeadTimeSnapshotCodecTest {

    private final CutoverLeadTimeCalculator calculator = new CutoverLeadTimeCalculator();
    private final CutoverLeadTimeSnapshotCodec codec = new CutoverLeadTimeSnapshotCodec();

    @Test
    void roundTripsTheExactEightKeySnapshot() {
        CutoverLeadTimeCompliance source = calculator.calculate("A", "DEVICE_REPLACE_WHOLE",
                LocalDateTime.of(2026, 9, 6, 9, 0),
                LocalDateTime.of(2026, 9, 1, 18, 0));

        String encoded = codec.encode(source);
        ObjectNode json = (ObjectNode) JsonUtils.parseTree(encoded);

        assertEquals(8, json.size());
        assertEquals(source, codec.decode(encoded));
        assertEquals("CUT_LEAD_TIME_R034_V1", json.path("ruleVersion").asText());
        assertEquals("Asia/Shanghai", json.path("timezoneId").asText());
    }

    @Test
    void rejectsMissingAndAdditionalKeys() {
        ObjectNode missing = snapshotJson();
        missing.remove("requiredDays");
        ObjectNode additional = snapshotJson();
        additional.put("decision", "DISPLAY_ONLY");

        assertThrows(IllegalArgumentException.class, () -> codec.decode(missing));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(additional));
    }

    @Test
    void rejectsFactsThatConflictWithTheFrozenRule() {
        ObjectNode wrongVersion = snapshotJson();
        wrongVersion.put("ruleVersion", "CUT_LEAD_TIME_R034_V2");
        ObjectNode wrongType = snapshotJson();
        wrongType.put("cutoverType", "UNKNOWN");
        ObjectNode wrongLateFlag = snapshotJson();
        wrongLateFlag.put("lateSubmission", true);

        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongVersion));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongType));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongLateFlag));
    }

    private ObjectNode snapshotJson() {
        CutoverLeadTimeCompliance source = calculator.calculate("A", "DEVICE_REPLACE_WHOLE",
                LocalDateTime.of(2026, 9, 6, 9, 0),
                LocalDateTime.of(2026, 9, 1, 18, 0));
        return (ObjectNode) JsonUtils.parseTree(codec.encode(source));
    }
}
