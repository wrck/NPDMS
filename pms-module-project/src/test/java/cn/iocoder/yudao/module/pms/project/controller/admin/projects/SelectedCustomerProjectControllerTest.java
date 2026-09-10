package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.SelectedCustomerProjectCreateReqVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.*;

class SelectedCustomerProjectControllerTest {
    @Test
    void newEndpointRequiresSelectedCustomerWithoutChangingTheOldInputContract() {
        assertEquals("/api/v1/pms/projects", SelectedCustomerProjectController.class
                .getAnnotation(RequestMapping.class).value()[0]);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var selected = new SelectedCustomerProjectCreateReqVO();
            fillRequired(selected);
            assertTrue(validator.validate(selected).stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerCode")));
            selected.setCustomerCode("C-001");
            assertTrue(validator.validate(selected).isEmpty());
            selected.setCustomerName("不能自由填写的名称");
            assertTrue(validator.validate(selected).stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerName")));
            selected.setCustomerName(null);
            selected.setParentId(20L);
            assertTrue(validator.validate(selected).stream().anyMatch(v -> v.getPropertyPath().toString().equals("parentId")));
            var legacy = new ProjectCreateReqVO();
            fillRequired(legacy);
            legacy.setCustomerName("原入口仍可使用原字段");
            assertTrue(validator.validate(legacy).isEmpty());
        }
    }

    private void fillRequired(ProjectCreateReqVO request) {
        request.setProjectName("客户关联验收项目");
        request.setCreationReason("验证客户选择");
        request.setOrderOfficeCompanyId(10L);
        request.setOrderOfficeDepartmentId(20L);
        request.setImplementationLocation("待维护地点");
        request.setCandidateWatermark("candidate-version");
    }
}
