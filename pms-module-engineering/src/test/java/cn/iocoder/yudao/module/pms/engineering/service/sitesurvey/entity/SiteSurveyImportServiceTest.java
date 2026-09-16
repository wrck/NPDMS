package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyImportSource;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.EntityExtensionValueDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SiteSurveyImportServiceTest {
    @Test
    void legacyFieldsBecomeTypedValuesWithoutChangingBusinessIdentityOrEvidence() {
        var source = new SiteSurveyImportSource();
        source.setId(41L);
        source.setTenantId(7L);
        source.setProjectId(19L);
        source.setStatus(3);
        source.setVersion(8);
        source.setArchivedAt(LocalDateTime.of(2025, 4, 1, 10, 0));
        source.setOutsourceRequestId(91L);
        source.setFormExtraValues(Map.of("extra_cabinetReady", true, "extra_requiredEndDate", "2027-02-03",
                "extra_powerTypes", List.of("AC", "DC"), "extra_selectedMaterials", List.of(Map.of(
                        "deviceId", 18L, "projectId", 19L, "sn", "SN-1", "reason", "安装")), "extra_note", "扩展值"));
        var target = SiteSurveyImportService.convert(source);
        assertEquals(source.getId(), target.getId());
        assertEquals(source.getTenantId(), target.getTenantId());
        assertEquals(source.getStatus(), target.getStatus());
        assertEquals(source.getVersion(), target.getVersion());
        assertEquals(source.getArchivedAt(), target.getArchivedAt());
        assertEquals(source.getOutsourceRequestId(), target.getOutsourceRequestId());
        assertEquals(Boolean.TRUE, target.getCabinetReady());
        assertEquals(LocalDate.of(2027, 2, 3), target.getRequiredEndDate());
        assertEquals(List.of("AC", "DC"), target.getPowerTypes());
        assertEquals("SN-1", target.getSelectedMaterials().getFirst().getSn());
        assertFalse(SiteSurveyEntityProvider.FIELDS.read(target).containsKey("extra_note"));
        assertFalse(EntityVersionProvider.class.isAssignableFrom(SiteSurveyEntityProvider.class));
        assertEquals("扩展值", source.getFormExtraValues().get("extra_note"));
    }

    @Test
    void reconciliationFindsExactBusinessAndAuditDifferences() {
        var source = new SiteSurveyImportSource();
        source.setPowerSupply("原值");
        source.setVersion(3);
        source.setCreator("11");
        var expected = SiteSurveyImportService.convert(source);
        var actual = BeanUtils.toBean(expected, SiteSurveyEntityDO.class);
        assertTrue(SiteSurveyImportService.differences(expected, actual).isEmpty());
        actual.setPowerSupply("错误值");
        actual.setCreator("22");
        assertEquals(List.of("creator", "powerSupply"), SiteSurveyImportService.differences(expected, actual));
    }

    @Test
    void existingCopyUtilityCopiesRecordIdentityIntoCapabilityRows() {
        var ref = new EntityRef(7L, "SOL", "SITE_SURVEY", 41L);
        var row = BeanUtils.toBean(ref, EntityExtensionValueDO.class);
        assertEquals(ref.tenantId(), row.getTenantId());
        assertEquals(ref.entityId(), row.getEntityId());
        assertEquals(ref.ownerModule(), row.getOwnerModule());
        assertEquals(ref.entityType(), row.getEntityType());
    }

    @Test
    void fieldPatchesCannotReplaceIdentityStatusOrConcurrency() {
        var row = new SiteSurveyEntityDO();
        row.setTenantId(7L);
        row.setVersion(4);
        for (String key : List.of("id", "tenantId", "projectId", "status", "version", "outsourceRequestId")) {
            assertThrows(IllegalArgumentException.class,
                    () -> SiteSurveyEntityProvider.FIELDS.write(row, Map.of(key, 99)));
        }
        assertEquals(7L, row.getTenantId());
        assertEquals(4, row.getVersion());
    }
}
