package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCodeSequenceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectCodeSequenceMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectCodeRules;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CODE_EXHAUSTED;

/**
 * 项目平台编码分配器（F-PM01 / ADR-0020，BR-8）
 * <p>
 * 行锁读 `proj_project_code_sequence`（tenant_id + code_namespace='PLATFORM_ROOT'，
 * 不存在则先插入 next_value=1），取 next_value→next_value+1 更新→返回编码；
 * 年份取当前系统时间。并发兜底：首次插入冲突（DuplicateKeyException）整体重试一次（重新分配流水）。
 * 需在事务内调用（FOR UPDATE 行锁随外层创建事务持有，串行化并发分配）。
 */
@Component
public class ProjectCodeAllocator {

    /** V1 平台根项目编码命名空间（租户级；PM-02 预留 ROOT:<code_root_id>） */
    public static final String NAMESPACE_PLATFORM_ROOT = "PLATFORM_ROOT";

    @Resource
    private ProjectCodeSequenceMapper projectCodeSequenceMapper;

    /**
     * 分配根项目编码：PJT + 年份4位 + 流水6位零填充。
     */
    @Transactional(rollbackFor = Exception.class)
    public String allocateRootCode() {
        try {
            return doAllocateRootCode();
        } catch (DuplicateKeyException concurrentFirstInsert) {
            // 并发首插命名空间行冲突：重试一次（重新走行锁读+流水递增）
            return doAllocateRootCode();
        }
    }

    private String doAllocateRootCode() {
        ProjectCodeSequenceDO sequence = projectCodeSequenceMapper.selectByNamespaceForUpdate(NAMESPACE_PLATFORM_ROOT);
        if (sequence == null) {
            ProjectCodeSequenceDO created = new ProjectCodeSequenceDO();
            created.setCodeNamespace(NAMESPACE_PLATFORM_ROOT);
            created.setNextValue(1L);
            // 并发首插冲突（DuplicateKeyException）由外层 allocateRootCode 捕获后重试
            projectCodeSequenceMapper.insert(created);
            sequence = created;
        }
        long nextValue = sequence.getNextValue();
        if (ProjectCodeRules.isSequenceExhausted(nextValue)) {
            throw exception(PROJECT_CODE_EXHAUSTED);
        }
        ProjectCodeSequenceDO update = new ProjectCodeSequenceDO();
        update.setId(sequence.getId());
        update.setNextValue(nextValue + 1);
        projectCodeSequenceMapper.updateById(update);
        return ProjectCodeRules.buildRootCode(Year.now().getValue(), nextValue);
    }
}
