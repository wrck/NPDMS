package cn.iocoder.yudao.module.pms.platform.controller.admin.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.user.BusinessUserSelectionApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessUserSelectionControllerTest {
    @Test void bindsBusinessSearchAndReturnsOnlySparseOptionsWithExactLongIdentifier() throws Exception {
        var selections = mock(BusinessUserSelectionApi.class);
        when(selections.page(any())).thenReturn(new PageResult<>(List.of(
                new BusinessUserSelectionApi.Option(2115387679367467008L, "张三", "z00001", 3L, "交付")), 2L));
        var mvc = MockMvcBuilders.standaloneSetup(new BusinessUserSelectionController(selections)).build();
        mvc.perform(get("/api/v1/pms/user-selections").param("keyword", "zhangsan").param("pageNo", "2").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].nickname").value("张三"))
                .andExpect(jsonPath("$.data.list[0].username").value("z00001"))
                .andExpect(jsonPath("$.data.list[0].id").value(2115387679367467008L))
                .andExpect(jsonPath("$.data.list[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].mobile").doesNotExist());
        verify(selections).page(new BusinessUserSelectionApi.Query(2, 1, "zhangsan"));
    }

    @Test void rejectsInvalidHttpPageAndKeywordBeforeDirectoryAccess() throws Exception {
        var selections = mock(BusinessUserSelectionApi.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BusinessUserSelectionController(selections)).build();
        mvc.perform(get("/api/v1/pms/user-selections").param("pageNo", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/pms/user-selections").param("pageSize", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/pms/user-selections").param("keyword", "x".repeat(65))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/pms/user-selections").param("userIds", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(selections);
    }
}
