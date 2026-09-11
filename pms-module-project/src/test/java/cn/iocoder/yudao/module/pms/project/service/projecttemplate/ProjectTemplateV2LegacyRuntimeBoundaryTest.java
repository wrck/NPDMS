package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_PUBLISH_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectTemplateV2LegacyRuntimeBoundaryTest {

    @Test
    void returnsPersistedExecutionSnapshotWithoutRecompiling() {
        ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
        TemplateCompiler compiler = mock(TemplateCompiler.class);
        ProjectTemplateV2ServiceImpl service = service(revisions, compiler);

        TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
        snapshot.setCompilerVersion("compiler-test");
        ProjectTemplateRevisionDO revision = publishedRevision(3);
        revision.setExecutionSchemaVersion(TemplateExecutionSnapshot.SCHEMA_VERSION);
        revision.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(revisions.selectByTemplateIdAndRevisionNo(10L, 3)).thenReturn(revision);

        TemplateExecutionSnapshot result = service.getExecutionSnapshot(10L, 3);

        assertNotNull(result);
        assertEquals("compiler-test", result.getCompilerVersion());
        verifyNoInteractions(compiler);
    }

    @Test
    void rejectsLegacyPublishedRevisionWithoutPersistedV2Snapshot() {
        ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
        TemplateCompiler compiler = mock(TemplateCompiler.class);
        ProjectTemplateV2ServiceImpl service = service(revisions, compiler);

        ProjectTemplateRevisionDO revision = publishedRevision(2);
        revision.setDefinitionSnapshot("{\"legacy\":true}");
        when(revisions.selectByTemplateIdAndRevisionNo(10L, 2)).thenReturn(revision);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getExecutionSnapshot(10L, 2));

        assertEquals(PROJECT_TEMPLATE_PUBLISH_INVALID.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("禁止由当前编译器即时重解释"));
        assertTrue(error.getMessage().contains("显式复制为V2草稿并重新发布"));
        verifyNoInteractions(compiler);
    }

    private ProjectTemplateV2ServiceImpl service(ProjectTemplateRevisionMapper revisions, TemplateCompiler compiler) {
        ProjectTemplateV2ServiceImpl service = new ProjectTemplateV2ServiceImpl();
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisions);
        ReflectionTestUtils.setField(service, "templateCompiler", compiler);
        return service;
    }

    private ProjectTemplateRevisionDO publishedRevision(int revisionNo) {
        ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
        revision.setTemplateId(10L);
        revision.setRevisionNo(revisionNo);
        revision.setStatus(TemplateRules.REVISION_STATUS_PUBLISHED);
        return revision;
    }
}
