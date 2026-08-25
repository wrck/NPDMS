package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CutTaskMapperContractTest {

    @Test
    void governanceQueryMustKeepTenantDeletionCandidateAndStableOrder() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/task/CutTaskMapper.xml"));
        assertTrue(xml.contains("tenant_id = #{query.tenantId}"));
        assertTrue(xml.contains("deleted = b'0'"));
        assertTrue(xml.contains("collection=\"query.projectIds\""));
        assertTrue(xml.contains("ORDER BY project_id, id"));
    }
}
