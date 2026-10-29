package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import java.util.Set;
import static org.mockito.Mockito.*;

/** Existing domain fixtures run as confirmed PMs; each test may override this qualification explicitly. */
final class EngineeringManagementFixtures {
    private EngineeringManagementFixtures() { }
    static ProjectManagementQualificationApi confirmed(ProjectParticipantFactApi participants) {
        lenient().when(participants.inspectAuthorization(any())).thenAnswer(call -> {
            ProjectParticipantFactQuery query = call.getArgument(0);
            if (query == null) return null;
            return new ProjectParticipantFact(query.projectId(), query.subjectUserId(), Set.of("PROJECT_MANAGER"),
                    "PRIMARY", "ACTIVE", "S1", 0L, 0L);
        });
        var qualifications = mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class);
        lenient().when(qualifications.inspect(any())).thenAnswer(call -> {
            cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Query subject = call.getArgument(0);
            if (subject == null) return null;
            var member = participants.inspectAuthorization(new ProjectParticipantFactQuery(subject.projectId(),subject.userId(),
                    cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.MANAGEMENT_CODES,java.time.LocalDateTime.now()));
            return member == null ? null : new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact(subject,
                    cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Source.PROJECT_MEMBER,member,null);
        });
        lenient().when(qualifications.lockAndRevalidate(any())).thenAnswer(call -> {
            cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Revalidation request = call.getArgument(0);
            var member = request.expected().member();
            participants.lockAndRevalidateAuthorization(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery(
                    member.projectId(),member.userId(),request.expectedProjectVersion(),request.requiredLifecycleStatus(),request.requiredCurrentStage(),member.effectiveRoleCodes()));
            return request.expected();
        });
        return qualifications;
    }
}
