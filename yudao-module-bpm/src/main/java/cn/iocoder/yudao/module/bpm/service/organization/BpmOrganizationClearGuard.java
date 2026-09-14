package cn.iocoder.yudao.module.bpm.service.organization;

import cn.iocoder.yudao.module.system.api.organization.OrganizationClearGuard;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessDefinitionInfoMapper;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class BpmOrganizationClearGuard implements OrganizationClearGuard {
    private final RepositoryService repository;
    private final BpmProcessDefinitionInfoMapper definitions;
    public void check(Scope scope) {
        if(scope.departmentIds().isEmpty())return;
        var query=repository.createProcessDefinitionQuery().processDefinitionTenantId(scope.tenantId().toString());
        for(int offset=0;;offset+=100) {
            var page=query.listPage(offset,100);
            for(var definition:page) {
                var info=definitions.selectByProcessDefinitionId(definition.getId());
                if(info!=null&&info.getStartDeptIds()!=null&&!Collections.disjoint(info.getStartDeptIds(),scope.departmentIds()))
                    throw new IllegalArgumentException("不能清空部门：流程 "+definition.getName()+" 的发起部门范围仍有引用");
                var model=repository.getBpmnModel(definition.getId());
                for(var process:model.getProcesses()) for(var task:process.findFlowElementsOfType(UserTask.class,true)) {
                    Integer strategy=BpmnModelUtils.parseCandidateStrategy(task);
                    if(strategy==null)continue;
                    if(Set.of(20,21,23).contains(strategy)) {
                        String param=BpmnModelUtils.parseCandidateParam(task);
                        if(param==null||!Collections.disjoint(StrUtils.splitToLong(param.split("\\|")[0],","),scope.departmentIds()))
                            throw new IllegalArgumentException("不能清空部门：流程 "+definition.getName()+" 的审批部门仍有引用");
                    }
                    if(Set.of(51,60).contains(strategy))
                        throw new IllegalArgumentException("不能自动清空部门：流程 "+definition.getName()+" 存在动态部门或表达式审批，请先由流程管理员核对并处理");
                }
            }
            if(page.size()<100)break;
        }
    }
}
