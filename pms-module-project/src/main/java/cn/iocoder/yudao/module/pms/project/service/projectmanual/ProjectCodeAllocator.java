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

    /** V1 平台根项目编码命名空间（租户级） */
    public static final String NAMESPACE_PLATFORM_ROOT = "PLATFORM_ROOT";
    /** 子项目编码命名空间前缀（PM-02：ROOT:<code_root_id>） */
    public static final String NAMESPACE_CHILD_PREFIX = "ROOT:";

    /**
     * 子项目编码分配结果：编码 + 命名空间序号（>0，不回收复用）。
     */
    public record ChildCodeAllocation(String projectCode, int projectSequence) {
    }

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
        long nextValue = doAllocateSequence(NAMESPACE_PLATFORM_ROOT);
        return ProjectCodeRules.buildRootCode(Year.now().getValue(), nextValue);
    }

    /**
     * 分配子项目编码（PM-02 / ADR-0020）：命名空间 `ROOT:<code_root_id>` 内递增，
     * 返回 `<根项目编码>-SP<流水>` 与命名空间序号（>0）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChildCodeAllocation allocateChildCode(Long codeRootId, String rootProjectCode) {
        try {
            return doAllocateChildCode(codeRootId, rootProjectCode);
        } catch (DuplicateKeyException concurrentFirstInsert) {
            // 并发首插命名空间行冲突：重试一次
            return doAllocateChildCode(codeRootId, rootProjectCode);
        }
    }

    private ChildCodeAllocation doAllocateChildCode(Long codeRootId, String rootProjectCode) {
        long nextValue = doAllocateSequence(NAMESPACE_CHILD_PREFIX + codeRootId);
        return new ChildCodeAllocation(
                ProjectCodeRules.buildChildCode(rootProjectCode, nextValue), (int) nextValue);
    }

    /**
     * 行锁读序列表（tenant_id + code_namespace，不存在则先插入 next_value=1），
     * 取 next_value 后递增。并发首插冲突（DuplicateKeyException）由调用方捕获重试。
     */
    private long doAllocateSequence(String namespace) {
        ProjectCodeSequenceDO sequence = projectCodeSequenceMapper.selectByNamespaceForUpdate(namespace);
        if (sequence == null) {
            ProjectCodeSequenceDO created = new ProjectCodeSequenceDO();
            created.setCodeNamespace(namespace);
            created.setNextValue(1L);
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
        return nextValue;
    }
}
