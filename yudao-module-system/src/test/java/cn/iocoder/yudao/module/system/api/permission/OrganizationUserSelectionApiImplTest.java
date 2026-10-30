package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({OrganizationUserSelectionApiImpl.class, OrganizationScopeApiImpl.class})
class OrganizationUserSelectionApiImplTest extends BaseDbUnitTest {

    @Resource
    private OrganizationUserSelectionApi selectionApi;
    @Resource
    private OrganizationScopeApi legacyApi;
    @Resource
    private UserCompanyDepartmentScopeMapper scopeMapper;
    @Resource
    private CompanyMapper companyMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private AdminUserMapper userMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void pageActiveUsers_returnsOnlyExactActiveScope() {
        long companyId = 52101L;
        long departmentId = 53101L;
        insertOrganization(companyId, departmentId, "OFFICE-HZ");
        insertUser(51101L, "active", "在职服务经理", CommonStatusEnum.ENABLE.getStatus());
        insertUser(51102L, "disabled", "停用服务经理", CommonStatusEnum.DISABLE.getStatus());
        insertUser(51103L, "expired", "范围过期服务经理", CommonStatusEnum.ENABLE.getStatus());
        scopeMapper.insert(scope(51101L, companyId, departmentId,
                LocalDateTime.now().minusDays(1), null, CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-HZ").setDepartmentName("杭州办事处"));
        scopeMapper.insert(scope(51102L, companyId, departmentId,
                LocalDateTime.now().minusDays(1), null, CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-HZ").setDepartmentName("杭州办事处"));
        scopeMapper.insert(scope(51103L, companyId, departmentId,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1),
                CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-HZ").setDepartmentName("杭州办事处"));

        PageResult<OrganizationUserCandidateRespDTO> result = selectionApi.page(
                candidateRequest(companyId, departmentId, "OFFICE-HZ"));

        assertEquals(1L, result.getTotal());
        assertEquals(51101L, result.getList().getFirst().getUserId());
        assertEquals("active", result.getList().getFirst().getUsername());
        assertEquals("杭州办事处", result.getList().getFirst().getDepartmentName());
    }

    @Test
    void pageActiveUsers_worksWithoutTenantContextWhenTenantInterceptionIsDisabled() {
        long companyId = 52105L;
        long departmentId = 53105L;
        insertOrganization(companyId, departmentId, "OFFICE-NJ");
        insertUser(51105L, "single-tenant", "单租户服务经理", CommonStatusEnum.ENABLE.getStatus());
        scopeMapper.insert(scope(51105L, companyId, departmentId,
                LocalDateTime.now().minusDays(1), null, CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-NJ").setDepartmentName("南京办事处"));
        TenantContextHolder.clear();

        PageResult<OrganizationUserCandidateRespDTO> result = selectionApi.page(
                candidateRequest(companyId, departmentId, "OFFICE-NJ"));

        assertEquals(1L, result.getTotal());
        assertEquals(51105L, result.getList().getFirst().getUserId());
    }

    @Test
    void pageActiveUsers_returnsEmptyWithoutFallback() {
        long companyId = 52102L;
        long departmentId = 53102L;
        insertOrganization(companyId, departmentId, "OFFICE-SH");

        PageResult<OrganizationUserCandidateRespDTO> result = selectionApi.page(
                candidateRequest(companyId, departmentId, "OFFICE-SH"));

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void pageActiveUsers_rejectsDepartmentCodeMismatch() {
        long companyId = 52103L;
        long departmentId = 53103L;
        insertOrganization(companyId, departmentId, "OFFICE-BJ");

        assertServiceException(() -> selectionApi.page(
                candidateRequest(companyId, departmentId, "OFFICE-GZ")), ORGANIZATION_SCOPE_INVALID);
    }

    @Test
    void pageActiveUsers_rejectsPageSizeAboveContractLimit() {
        OrganizationUserCandidatePageReqDTO request = candidateRequest(52104L, 53104L, "OFFICE-SZ");
        request.setPageSize(101);

        assertServiceException(() -> selectionApi.page(request), ORGANIZATION_SCOPE_INVALID_ARGUMENT);
    }

    @Test
    void emailKeywordMatchesOnlyCurrentCompanyDepartmentActiveCandidates() {
        insertOrganization(52106L, 53106L, "OFFICE-EMAIL");
        insertOrganization(52107L, 53107L, "OFFICE-OTHER");
        for (long id = 51201; id <= 51208; id++) {
            insertUser(id, "account-" + id, "人员" + id, id == 51205 ? 1 : 0);
            AdminUserDO email = new AdminUserDO().setId(id).setEmail("zhangsan" + id + "@test.invalid");
            userMapper.updateById(email);
            var grant = scope(id, 52106L, 53106L, LocalDateTime.now().minusDays(1), null, 0)
                    .setDepartmentCode("OFFICE-EMAIL");
            if (id == 51203) grant.setCompanyId(52107L);
            if (id == 51204) grant.setDepartmentId(53107L).setDepartmentCode("OFFICE-OTHER");
            if (id == 51206) grant.setStatus(1);
            if (id == 51207) grant.setEffectiveTo(LocalDateTime.now().minusHours(1));
            if (id == 51208) grant.setEffectiveFrom(LocalDateTime.now().plusDays(1));
            scopeMapper.insert(grant);
        }
        var request = candidateRequest(52106L, 53106L, "OFFICE-EMAIL");
        var baseline = candidateRequest(52106L, 53106L, "OFFICE-EMAIL");
        assertEquals(legacyApi.pageActiveUsers(baseline).getList(), selectionApi.page(baseline).getList());
        baseline.setKeyword("zhangsan");
        assertEquals(0L, legacyApi.pageActiveUsers(baseline).getTotal());
        assertFalse(java.util.Arrays.stream(OrganizationUserCandidateRespDTO.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("email")));
        request.setKeyword("  zhangsan  ");
        request.setPageSize(1);
        var first = selectionApi.page(request);
        assertEquals(2L, first.getTotal());
        assertEquals(51201L, first.getList().getFirst().getUserId());
        request.setPageNo(2);
        var second = selectionApi.page(request);
        assertEquals(2L, second.getTotal());
        assertEquals(51202L, second.getList().getFirst().getUserId());
        request.setPageNo(1);
        request.setKeyword("no_match_email");
        assertTrue(selectionApi.page(request).getList().isEmpty());
    }

    private void insertOrganization(long companyId, long departmentId, String departmentCode) {
        CompanyDO company = new CompanyDO().setId(companyId).setCode("COMPANY-" + companyId)
                .setName("公司 " + companyId).setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
        company.setTenantId(1L);
        companyMapper.insert(company);
        DeptDO department = new DeptDO().setId(departmentId).setCode(departmentCode).setName("杭州办事处")
                .setParentId(0L).setSort(1).setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
        department.setTenantId(1L);
        deptMapper.insert(department);
    }

    private void insertUser(long userId, String username, String nickname, int status) {
        AdminUserDO user = new AdminUserDO().setId(userId).setUsername(username).setPassword("")
                .setNickname(nickname).setStatus(status);
        user.setTenantId(1L);
        userMapper.insert(user);
    }

    private static OrganizationUserCandidatePageReqDTO candidateRequest(
            long companyId, long departmentId, String departmentCode) {
        return new OrganizationUserCandidatePageReqDTO().setCompanyId(companyId)
                .setDepartmentId(departmentId).setDepartmentCode(departmentCode);
    }

    private static UserCompanyDepartmentScopeDO scope(Long userId, Long companyId, Long departmentId,
                                                       LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                                                       Integer status) {
        UserCompanyDepartmentScopeDO scope = new UserCompanyDepartmentScopeDO()
                .setUserId(userId)
                .setCompanyId(companyId)
                .setCompanyCode("COMPANY-" + companyId)
                .setCompanyName("公司 " + companyId)
                .setDepartmentId(departmentId)
                .setDepartmentCode("DEPT-" + departmentId)
                .setDepartmentName("部门 " + departmentId)
                .setScopeRole("MEMBER")
                .setIsPrimary(true)
                .setEffectiveFrom(effectiveFrom)
                .setEffectiveTo(effectiveTo)
                .setStatus(status)
                .setVersion(0);
        scope.setTenantId(1L);
        return scope;
    }

}
