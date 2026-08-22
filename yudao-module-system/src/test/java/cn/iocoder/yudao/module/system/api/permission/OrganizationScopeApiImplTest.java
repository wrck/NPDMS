package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(OrganizationScopeApiImpl.class)
class OrganizationScopeApiImplTest extends BaseDbUnitTest {

    @Resource
    private OrganizationScopeApi scopeApi;
    @Resource
    private UserCompanyDepartmentScopeMapper scopeMapper;

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

    private static UserCompanyDepartmentScopeDO scope(Long userId, Long companyId, Long departmentId,
                                                       LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                                                       Integer status) {
        return new UserCompanyDepartmentScopeDO()
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
    }

}
