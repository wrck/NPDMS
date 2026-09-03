package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskApi;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskFact;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskRequestCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SatisfactionResultExportApplicationService {
    private final ExportTaskApi exportTaskApi;

    public ExportTaskFact request(Long tenantId, Long actorUserId, String operationId, Long projectId,
                                  List<String> fields, boolean includeFiles) {
        return exportTaskApi.request(new ExportTaskRequestCommand(tenantId, actorUserId, operationId,
                "ACC", "SATISFACTION_RESULT",
                JsonUtils.toJsonString(new SatisfactionResultExportBusinessDataProvider.Filter(projectId)),
                List.copyOf(fields), includeFiles));
    }
}
