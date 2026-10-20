package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.projectgovernance.ProjectReopenScopeApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectReopenBusinessEvidenceTest {
    private final LocalDateTime openedAt = LocalDateTime.of(2026, 10, 20, 11, 0);
    private final TaskBusinessObjectProvider provider = mock(TaskBusinessObjectProvider.class);
    private final TaskBusinessObjectProvider.AssociationContext context =
            new TaskBusinessObjectProvider.AssociationContext(1L, 20L, null, null);
    private final ProjectReopenBusinessEvidence evidence = new ProjectReopenBusinessEvidence(
            query -> Optional.of(new ProjectReopenScopeApi.Round(50L, openedAt)));

    @Test void ordinaryProjectDoesNotRequireNewEvidenceOrReconfirmOldAssociations() {
        var ordinary = new ProjectReopenBusinessEvidence(query -> Optional.empty());
        when(provider.ownerContext()).thenReturn("PROJ");
        when(provider.objectType()).thenReturn("PROJECT_ASSIGNMENT");
        assertTrue(ordinary.mayAssociate(provider, context, "1"));
        assertTrue(ordinary.mayComplete(provider, context, new ProjectTaskBusinessLinkDO()));
        assertFalse(ordinary.mayReuseProjectObject(provider, context));
        verify(provider, never()).handlingCreatedAt(any(), any());
    }

    @Test void oldUnknownAndBoundaryRecordsCannotBeAssociatedOrComplete() {
        var link = new ProjectTaskBusinessLinkDO(); link.setObjectId("1");
        for (var time : new LocalDateTime[]{null, openedAt.minusDays(1), openedAt}) {
            when(provider.handlingCreatedAt(context, "1")).thenReturn(time);
            assertFalse(evidence.mayAssociate(provider, context, "1"));
            assertFalse(evidence.mayComplete(provider, context, link));
        }
        when(provider.handlingCreatedAt(context, "1")).thenReturn(openedAt.plusSeconds(1));
        assertTrue(evidence.mayAssociate(provider, context, "1"));
        assertTrue(evidence.mayComplete(provider, context, link));
    }

    @Test void projectObjectsUseCurrentOwnerFactsWithoutExtraConfirmation() {
        for (var owner : new String[][]{{"PROJ", "PROJECT_ASSIGNMENT"}, {"IMP", "INSTALLATION"}}) {
            when(provider.ownerContext()).thenReturn(owner[0]);
            when(provider.objectType()).thenReturn(owner[1]);
            var link = new ProjectTaskBusinessLinkDO(); link.setObjectId("20");
            link.setNodeExecutionId(80L); link.setFactVersion("old-owner-version");
            link.setLinkedAt(openedAt.plusSeconds(1)); link.setLinkedBy(0L);
            assertTrue(evidence.mayAssociate(provider, context, "20"));
            assertTrue(evidence.mayReuseProjectObject(provider, context));
            assertTrue(evidence.mayComplete(provider, context, link));
        }
        verify(provider, never()).handlingCreatedAt(any(), any());
    }
}
