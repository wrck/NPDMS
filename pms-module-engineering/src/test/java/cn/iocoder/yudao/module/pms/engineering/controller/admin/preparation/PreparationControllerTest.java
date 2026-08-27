package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationItemPatchReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReviewService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSourceService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationWaiverService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisCommandService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PreparationControllerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void patchTracksJsonNullAndOmitsMissingFields() {
        PreparationItemPatchReqVO request = JsonUtils.parseObject("""
                {"expectedPreparationVersion":1,"expectedInputVersion":1,
                 "expectedReadinessVersion":1,"expectedFormVersion":1,"expectedProjectVersion":3,
                 "siteResultDetail":null}
                """, PreparationItemPatchReqVO.class);

        assertEquals(Set.of("siteResultDetail"), request.getSubmittedFields());
        assertNull(request.getSiteResultDetail());
    }

    @Test
    void singleTenantCurrentQueryUsesTrustedTenantZeroAndClearsIt() {
        login(9L);
        PreparationQueryService queryService = mock(PreparationQueryService.class);
        PreparationController controller = new PreparationController(queryService,
                mock(PreparationItemApplicationService.class),
                mock(PreparationReviewService.class),
                mock(PreparationReadinessService.class),
                mock(PreparationSourceService.class),
                mock(PreparationWaiverService.class),
                mock(RequirementAnalysisQueryService.class),
                mock(RequirementAnalysisCommandService.class),
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));

        controller.getCurrent(100L, "PRE_02", false, new PreparationPageReqVO());

        verify(queryService).getCurrent(eq(100L), eq("PRE_02"),
                eq(new PreparationQueryService.Actor(0L, 9L)));
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void enabledMultiTenantWithoutContextFailsClosed() {
        login(9L);
        PreparationQueryService queryService = mock(PreparationQueryService.class);
        PreparationController controller = new PreparationController(queryService,
                mock(PreparationItemApplicationService.class),
                mock(PreparationReviewService.class),
                mock(PreparationReadinessService.class),
                mock(PreparationSourceService.class),
                mock(PreparationWaiverService.class),
                mock(RequirementAnalysisQueryService.class),
                mock(RequirementAnalysisCommandService.class),
                new MockEnvironment().withProperty("yudao.tenant.enable", "true"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.getCurrent(100L, "PRE_02", false, new PreparationPageReqVO()));

        assertEquals(PREPARATION_PROJECT_FACT_INVALID.getCode(), error.getCode());
        verifyNoInteractions(queryService);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void sharedCurrentEndpointRequiresRequirementQueryPermissionForRequirementReads() throws Exception {
        PreAuthorize currentAuthorize = PreparationController.class
                .getMethod("getCurrent", Long.class, String.class, boolean.class, PreparationPageReqVO.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize detailAuthorize = PreparationController.class.getMethod("getDetail", Long.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize compareAuthorize = PreparationController.class
                .getMethod("compareRequirementAnalysis", Long.class, Long.class)
                .getAnnotation(PreAuthorize.class);

        assertTrue(currentAuthorize.value().contains("pms:requirement-analysis:query"));
        assertFalse(currentAuthorize.value().contains("pms:requirement-analysis:manage"));
        assertTrue(currentAuthorize.value().contains("pms:preparation-survey:query"));
        assertTrue(detailAuthorize.value().contains("pms:requirement-analysis:query"));
        assertFalse(detailAuthorize.value().contains("pms:requirement-analysis:manage"));
        assertTrue(compareAuthorize.value().contains("pms:requirement-analysis:query"));
        assertFalse(compareAuthorize.value().contains("pms:requirement-analysis:manage"));
    }

    private void login(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }
}
