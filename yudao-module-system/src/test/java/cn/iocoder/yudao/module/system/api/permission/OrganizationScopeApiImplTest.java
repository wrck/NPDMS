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
import org.junit.jupiter.api.BeforeEach;
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

@Import(OrganizationScopeApiImpl.class)
class OrganizationScopeApiImplTest extends BaseDbUnitTest {

    @Resource
    private OrganizationScopeApi scopeApi;
    @Resource
    private UserCompanyDepartmentScopeMapper scopeMapper;
    @Resource
    private CompanyMapper companyMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private AdminUserMapper userMapper;

    @BeforeEach
    void setTenantContext() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void hasScope_requiresCompanyAndDepartmentFromSameActiveRow() {
        long userId = 1001L;
        long companyAId = 2001L;
        long companyBId = 2002L;
        long departmentId = 3001L;
        scopeMapper.insert(scope(userId, companyAId, departmentId,
                LocalDateTime.now().minusDays(1), null, CommonStatusEnum.ENABLE.getStatus()));
        scopeMapper.insert(scope(userId, companyBId, 3002L,
                LocalDateTime.now().minusDays(1), null,
                CommonStatusEnum.ENABLE.getStatus()));
        scopeMapper.insert(scope(userId, 2003L, 3003L,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1),
                CommonStatusEnum.ENABLE.getStatus()));

        List<UserCompanyDepartmentScopeRespDTO> activeScopes = scopeApi.getActiveScopes(userId);

        assertEquals(2, activeScopes.size());
        assertTrue(scopeApi.hasScope(userId, companyAId, departmentId));
        assertFalse(scopeApi.hasScope(userId, companyBId, departmentId));
    }

    @Test
    void pageActiveUsers_returnsOnlyExactActiveScope() {
        long companyId = 2101L;
        long departmentId = 3101L;
        insertOrganization(companyId, departmentId, "OFFICE-HZ");
        insertUser(1101L, "active", "在职服务经理", CommonStatusEnum.ENABLE.getStatus());
        insertUser(1102L, "disabled", "停用服务经理", CommonStatusEnum.DISABLE.getStatus());
        insertUser(1103L, "expired", "范围过期服务经理", CommonStatusEnum.ENABLE.getStatus());
        scopeMapper.insert(scope(1101L, companyId, departmentId,
                LocalDateTime.now().minusDays(1), null, CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-HZ").setDepartmentName("杭州办事处"));
        scopeMapper.insert(scope(1102L, companyId, departmentId,
                LocalDateTime.now().minusDays(1), null, CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-HZ").setDepartmentName("杭州办事处"));
        scopeMapper.insert(scope(1103L, companyId, departmentId,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1),
                CommonStatusEnum.ENABLE.getStatus())
                .setDepartmentCode("OFFICE-HZ").setDepartmentName("杭州办事处"));

        PageResult<OrganizationUserCandidateRespDTO> result = scopeApi.pageActiveUsers(
                candidateRequest(companyId, departmentId, "OFFICE-HZ"));

        assertEquals(1L, result.getTotal());
        assertEquals(1101L, result.getList().getFirst().getUserId());
        assertEquals("active", result.getList().getFirst().getUsername());
        assertEquals("杭州办事处", result.getList().getFirst().getDepartmentName());
    }

    @Test
    void pageActiveUsers_returnsEmptyWithoutFallback() {
        long companyId = 2102L;
        long departmentId = 3102L;
        insertOrganization(companyId, departmentId, "OFFICE-SH");

        PageResult<OrganizationUserCandidateRespDTO> result = scopeApi.pageActiveUsers(
                candidateRequest(companyId, departmentId, "OFFICE-SH"));

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void pageActiveUsers_rejectsDepartmentCodeMismatch() {
        long companyId = 2103L;
        long departmentId = 3103L;
        insertOrganization(companyId, departmentId, "OFFICE-BJ");

        assertServiceException(() -> scopeApi.pageActiveUsers(
                candidateRequest(companyId, departmentId, "OFFICE-GZ")), ORGANIZATION_SCOPE_INVALID);
    }

    @Test
    void pageActiveUsers_rejectsPageSizeAboveContractLimit() {
        OrganizationUserCandidatePageReqDTO request = candidateRequest(2104L, 3104L, "OFFICE-SZ");
        request.setPageSize(101);

        assertServiceException(() -> scopeApi.pageActiveUsers(request), ORGANIZATION_SCOPE_INVALID_ARGUMENT);
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
