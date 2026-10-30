package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.core.KeyValue;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProjectSummaryProvider;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BpmProjectSummaryService {
    private final List<BpmProjectSummaryProvider> providers;

    public PageResult<BpmTaskRespVO> appendToAuthorizedPage(PageResult<BpmTaskRespVO> page) {
        var ids = page.getList().stream().map(BpmTaskRespVO::getProcessInstance)
                .filter(Objects::nonNull).map(BpmTaskRespVO.ProcessInstance::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return page;
        var query = new BpmProjectSummaryProvider.Query(TenantContextHolder.getRequiredTenantId(), ids);
        var names = new HashMap<String, String>();
        for (var provider : providers) {
            for (var item : provider.getProjectSummaries(query)) {
                if (!ids.contains(item.processInstanceId()) || item.projectName() == null || item.projectName().isBlank()) continue;
                var prior = names.putIfAbsent(item.processInstanceId(), item.projectName());
                if (prior != null && !prior.equals(item.projectName())) throw new IllegalStateException("流程项目摘要来源冲突");
            }
        }
        for (var row : page.getList()) {
            var instance = row.getProcessInstance();
            if (instance == null || !names.containsKey(instance.getId())) continue;
            var summary = instance.getSummary() == null ? new ArrayList<KeyValue<String, String>>()
                    : new ArrayList<>(instance.getSummary());
            var name = names.get(instance.getId());
            if (summary.stream().noneMatch(item -> item != null && "项目名称".equals(item.getKey()) && name.equals(item.getValue()))) {
                summary.add(new KeyValue<>("项目名称", name));
            }
            instance.setSummary(summary);
        }
        return page;
    }
}
