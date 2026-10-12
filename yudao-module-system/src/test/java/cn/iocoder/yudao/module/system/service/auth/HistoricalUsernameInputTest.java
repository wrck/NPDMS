package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalUsernameInputTest {
    @Test void approvedHistoricalNamesPassLoginAndAccountMaintenanceWithoutAllowingArbitraryUnderscores() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String name : new String[]{"c02791", "admin", "zhaoyangxi_supp", "xialangguo_ss"}) {
                assertTrue(validator.validateValue(AuthLoginReqVO.class, "username", name).isEmpty(), name);
                assertTrue(validator.validateValue(UserSaveReqVO.class, "username", name).isEmpty(), name);
            }
            for (String name : new String[]{"arbitrary_name", "name_supp1", "xialangguo_ss\u00a0", "a_ss\n", "   _ss"}) {
                assertFalse(validator.validateValue(AuthLoginReqVO.class, "username", name).isEmpty(), name);
                assertFalse(validator.validateValue(UserSaveReqVO.class, "username", name).isEmpty(), name);
            }
            assertFalse(validator.validateValue(AuthRegisterReqVO.class, "username", "xialangguo_ss").isEmpty());
        }
    }
}
