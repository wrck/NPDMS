package cn.iocoder.yudao.module.pms.project.api.participant;

/** Consumer fixtures supply only an Owner-approved result; the qualification matrix is tested at its actual Owner. */
public final class ManagementQualificationTestFacts {
    private ManagementQualificationTestFacts() { }

    public static ProjectManagementQualificationApi.Fact locked(ProjectManagementQualificationApi.CurrentRevalidation query) {
        // Mockito invokes a previous answer with matcher placeholders when a test overrides its fixture.
        if (query == null) return null;
        var subject = query.subject();
        return new ProjectManagementQualificationApi.Fact(subject, ProjectManagementQualificationApi.Source.REGIONAL_MANAGER, null,
                new ProjectRegionalManagerFactApi.Fact(subject.projectId(),subject.userId(),
                        query.expectedProjectVersion() == null ? 0L : query.expectedProjectVersion()));
    }
}
