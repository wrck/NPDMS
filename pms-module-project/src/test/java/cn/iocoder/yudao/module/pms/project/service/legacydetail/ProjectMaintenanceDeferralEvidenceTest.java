package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMaintenanceDeferralEvidenceTest {
    private final PlatformMigrationEvidenceApi evidence=mock(PlatformMigrationEvidenceApi.class);
    private final ProjectLegacyNativeEvidenceService service=new ProjectLegacyNativeEvidenceService(evidence);
    @Test void currentStatusDoesNotTreatPriorMaintenanceReceiptsAsApprovedCarry() {
        var record=new ProjectLegacyRecordDO();record.setDomainCode("maintenance");record.setSourceKey("301");
        var status=service.statuses(1L,"101",200L,List.of(record)).get("301");
        assertEquals("RETAINED",status.classification());
        assertEquals("USER_DEFERRED_MAINTENANCE_RECORD",status.reasonCode());
        assertTrue(status.reason().contains("维护记录本轮暂不迁入"));
        assertTrue(status.targets().isEmpty());
        verifyNoInteractions(evidence);
    }
    @Test void deferralCannotAppendOrRewriteHistoricalMigrationEvidence() {
        var record=new ProjectLegacyRecordDO();record.setDomainCode("maintenance");record.setSourceKey("301");
        service.reconcile(null,null,"maintenance",List.of(record),Map.of());
        verifyNoInteractions(evidence);
    }
}
