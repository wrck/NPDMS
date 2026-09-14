package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.controller.admin.sync.DataSyncController;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.SQLException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SyncControllerErrorTest {
    @Test void localDuplicateWithSqlCauseIsNotReportedAsSourceConnectionFailure()throws Exception {
        var tasks=mock(SyncTaskService.class);
        when(tasks.save(any())).thenThrow(new DuplicateKeyException("private SQL",new SQLException("private connection")));
        var controller=new DataSyncController(null,null,null,null,tasks,null,null,null,null);
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/api/v1/pms/integration/tasks")
                        .contentType("application/json").content("{\"name\":\"重复任务\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("集成配置唯一键冲突，请刷新列表并检查已有配置"));
    }
}
