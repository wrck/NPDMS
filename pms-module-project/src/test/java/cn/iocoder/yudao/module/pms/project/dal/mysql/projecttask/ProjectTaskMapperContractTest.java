package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTaskMapperContractTest {

    @Test
    void governanceQueryMustKeepTenantDeletionCandidateAndStableOrderPredicates() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/projecttask/ProjectTaskMapper.xml"));

        assertTrue(xml.contains("tenant_id = #{query.tenantId}"));
        assertTrue(xml.contains("deleted = b'0'"));
        assertTrue(xml.contains("collection=\"query.projectIds\""));
        assertTrue(xml.contains("ORDER BY project_id, id"));
    }
}
