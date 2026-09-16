package cn.iocoder.yudao.module.pms.engineering.requirement;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldMapping;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RequirementAnalysisFieldMappingTest {
    private final EntityFieldMapping<RequirementAnalysisDO> fields = new EntityFieldMapping<>(RequirementAnalysisDO.class);

    @Test
    void sameMappingReadsAndWritesInheritedBusinessFields() {
        var revision = new RequirementAnalysisRevisionDO();
        fields.write(revision, Map.of("projectBackground", "现网背景", "ipPlanning", "地址规划"));
        assertEquals("现网背景", revision.getProjectBackground());
        assertEquals("地址规划", fields.read(revision).get("ipPlanning"));
        var current = BeanUtils.toBean(revision, RequirementAnalysisDO.class);
        assertEquals(fields.read(revision), fields.read(current));
        assertEquals(fields.fields(), new EntityFieldMapping<>(RequirementAnalysisRevisionDO.class, RequirementAnalysisDO.class).fields());
    }

    @Test
    void patchCanExplicitlyClearWithoutClearingOtherFields() {
        var row = new RequirementAnalysisDO();
        row.setProjectBackground("已有内容");
        row.setProjectObjective("保留目标");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("projectBackground", null);
        fields.write(row, patch);
        assertNull(row.getProjectBackground());
        assertEquals("保留目标", row.getProjectObjective());
    }

    @Test
    void formCannotWriteIdentityAuditOrRevisionMetadata() {
        var row = new RequirementAnalysisRevisionDO();
        row.setTenantId(7L);
        row.setVersion(4);
        row.setEffectiveMarker(1);
        for (String code : List.of("id", "tenantId", "creator", "deleted", "version", "effectiveMarker", "frozenAt")) {
            assertThrows(IllegalArgumentException.class, () -> fields.write(row, Map.of(code, 99)));
        }
        assertEquals(7L, row.getTenantId());
        assertEquals(4, row.getVersion());
        assertEquals(1, row.getEffectiveMarker());
    }

    @Test
    void invalidValueRejectsEntirePatchBeforeMutation() {
        var row = new RequirementAnalysisDO();
        row.setProjectBackground("原内容");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("projectBackground", "新内容");
        patch.put("projectObjective", 123);
        assertThrows(IllegalArgumentException.class, () -> fields.write(row, patch));
        assertEquals("原内容", row.getProjectBackground());
    }
}
