import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROJECT_MAPPER = ROOT / "pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml"
MEMBER_MAPPER = ROOT / "pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml"
SNAPSHOT_MAPPER = ROOT / "pms-module-project/src/main/resources/mapper/projectgovernance/ProjectStageSnapshotMapper.xml"
SNAPSHOT_REPOSITORY = ROOT / (
    "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/repository/"
    "projectgovernance/ProjectStageSnapshotRepository.java"
)


class FProj006Task2PersistenceTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.project = PROJECT_MAPPER.read_text(encoding="utf-8")
        cls.member = MEMBER_MAPPER.read_text(encoding="utf-8")
        cls.snapshot = SNAPSHOT_MAPPER.read_text(encoding="utf-8")
        cls.snapshot_repository = SNAPSHOT_REPOSITORY.read_text(encoding="utf-8")

    def test_history_page_has_tenant_scope_and_stable_order(self) -> None:
        self.assertIn("tenant_id = #{query.tenantId}", self.snapshot)
        self.assertIn("project_id = #{query.projectId}", self.snapshot)
        self.assertIn("ORDER BY operated_at DESC, id DESC", self.snapshot)
        self.assertIn("LIMIT #{query.limit} OFFSET #{query.offset}", self.snapshot)
        page_query = (ROOT / "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/"
                             "projectgovernance/query/ProjectGovernanceHistoryPageQuery.java").read_text(encoding="utf-8")
        self.assertIn("PageParam pageParam", page_query)
        self.assertIn("MAX_PAGE_SIZE = 200", page_query)
        self.assertIn("pageParam = copy", page_query)

    def test_snapshot_mapper_has_only_append_insert_and_read_contracts(self) -> None:
        mapper = (ROOT / "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/"
                         "projectgovernance/ProjectStageSnapshotMapper.java").read_text(encoding="utf-8")
        self.assertNotIn("BaseMapperX", mapper)
        self.assertIn("insertAppendOnly", mapper)
        self.assertNotRegex(mapper, re.compile(r"\b(update|delete)\w*\s*\("))

    def test_reopen_locks_only_unconsumed_latest_exception_close(self) -> None:
        self.assertIn("operation_type = 'EXCEPTION_CLOSE'", self.snapshot)
        self.assertIn("reopen_snapshot.operation_type = 'REOPEN'", self.snapshot)
        self.assertIn("reopen_snapshot.related_snapshot_id = close_snapshot.id", self.snapshot)
        self.assertIn("ORDER BY close_snapshot.operated_at DESC, close_snapshot.id DESC", self.snapshot)
        lock_call = "projectMasterMapper.selectByIdForUpdate(query.projectId())"
        select_call = "mapper.selectLatestReusableExceptionClose(query)"
        self.assertIn(lock_call, self.snapshot_repository)
        self.assertIn(select_call, self.snapshot_repository)
        self.assertLess(self.snapshot_repository.index(lock_call), self.snapshot_repository.index(select_call))
        self.assertIn("FOR UPDATE", self.snapshot)

    def test_snapshot_append_uses_trusted_tenant_context(self) -> None:
        self.assertIn("TenantContextHolder.getRequiredTenantId()", self.snapshot_repository)
        self.assertIn("snapshot.setTenantId(tenantId)", self.snapshot_repository)
        self.assertIn("snapshot tenant must match trusted tenant context", self.snapshot_repository)

    def test_project_governance_update_is_tenant_scoped_cas(self) -> None:
        update = self.project.split('<update id="updateGovernanceStateIfMatch">', 1)[1].split("</update>", 1)[0]
        for condition in (
            "tenant_id = #{query.tenantId}",
            "id = #{query.projectId}",
            "version = #{query.expectedVersion}",
            "lifecycle_status = #{query.expectedLifecycleStatus}",
        ):
            with self.subTest(condition=condition):
                self.assertIn(condition, update)
        for assignment in (
            "current_stage = #{query.currentStage}",
            "lifecycle_status = #{query.lifecycleStatus}",
            "assignment_status = #{query.assignmentStatus}",
            "version = version + 1",
        ):
            with self.subTest(assignment=assignment):
                self.assertIn(assignment, update)

    def test_member_close_preserves_history_and_limits_responsibilities(self) -> None:
        update = self.member.split('<update id="closeEffectiveServiceManagerAssignments">', 1)[1].split(
            "</update>", 1
        )[0]
        self.assertNotRegex(update, re.compile(r"\bDELETE\s+FROM\b", re.IGNORECASE))
        self.assertIn("effective_to = #{query.closedAt}", update)
        self.assertIn("member_role IN ('SERVICE_MANAGER_L1', 'SERVICE_MANAGER_L2')", update)
        self.assertIn("assignment_type IN ('PRIMARY', 'COLLABORATOR')", update)
        self.assertIn("tenant_id = #{query.tenantId}", update)
        self.assertIn("project_id = #{query.projectId}", update)


if __name__ == "__main__":
    unittest.main()
