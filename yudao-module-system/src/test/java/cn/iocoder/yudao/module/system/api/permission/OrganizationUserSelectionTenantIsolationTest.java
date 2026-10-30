package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({OrganizationUserSelectionApiImpl.class, OrganizationUserSelectionTenantIsolationTest.TenantTestConfiguration.class})
class OrganizationUserSelectionTenantIsolationTest extends BaseDbUnitTest {

    @Resource
    private OrganizationUserSelectionApi selectionApi;
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;
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
    void frameworkTenantInterceptorFiltersCompanyDepartmentAndCandidateQuery() {
        TenantContextHolder.setTenantId(1L);
        long companyId = 52201L;
        long departmentId = 53201L;
        long userId = 51201L;
        insertOrganization(companyId, departmentId);
        insertUser(userId);
        scopeMapper.insert(scope(userId, companyId, departmentId));

        var request = new OrganizationUserCandidatePageReqDTO()
                .setCompanyId(companyId)
                .setDepartmentId(departmentId)
                .setDepartmentCode("OFFICE-EMAIL-TENANT").setKeyword("tenant-email");
        assertEquals(1L, selectionApi.page(request).getTotal());

        TenantContextHolder.setTenantId(0L);
        assertServiceException(() -> selectionApi.page(request), ORGANIZATION_SCOPE_INVALID);
    }

    private void insertOrganization(long companyId, long departmentId) {
        CompanyDO company = new CompanyDO().setId(companyId).setCode("COMPANY-CD")
                .setName("成都公司").setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
        company.setTenantId(1L);
        companyMapper.insert(company);
        DeptDO department = new DeptDO().setId(departmentId).setCode("OFFICE-EMAIL-TENANT").setName("成都办事处")
                .setParentId(0L).setSort(1).setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
        department.setTenantId(1L);
        deptMapper.insert(department);
    }

    private void insertUser(long userId) {
        AdminUserDO user = new AdminUserDO().setId(userId).setUsername("tenant-one")
                .setEmail("tenant-email@test.invalid").setPassword("").setNickname("租户一服务经理")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setTenantId(1L);
        userMapper.insert(user);
    }

    private static UserCompanyDepartmentScopeDO scope(long userId, long companyId, long departmentId) {
        UserCompanyDepartmentScopeDO scope = new UserCompanyDepartmentScopeDO()
                .setUserId(userId)
                .setCompanyId(companyId)
                .setCompanyCode("COMPANY-CD")
                .setCompanyName("成都公司")
                .setDepartmentId(departmentId)
                .setDepartmentCode("OFFICE-EMAIL-TENANT")
                .setDepartmentName("成都办事处")
                .setScopeRole("MEMBER")
                .setIsPrimary(true)
                .setEffectiveFrom(LocalDateTime.now().minusDays(1))
                .setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setVersion(0);
        scope.setTenantId(1L);
        return scope;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TenantTestConfiguration {

        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
