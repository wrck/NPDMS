package cn.iocoder.yudao.module.pms.project.service.phase;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhasePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhaseSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseRetiredDO;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * PMS 项目阶段 Service 接口（FR-PROJ-017 / FR-PROJ-016 / FR-PROJ-019 / T-V1-PROJ-007 / T-V1-PROJ-008）。
 * <p>
 * 承载项目阶段 CRUD、模板实例化、阶段顺序校验、完成门禁校验、超期与临期预警。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface ProjectPhaseService {

    /**
     * 创建项目阶段。校验项目存在、阶段编码项目内唯一、模板存在（如指定）。
     */
    Long createPhaseRetired(@Valid ProjectPhaseSaveReqVO createReqVO);

    /**
     * 更新项目阶段。校验存在、编码唯一。
     */
    void updatePhaseRetired(@Valid ProjectPhaseSaveReqVO updateReqVO);

    /**
     * 删除项目阶段。
     */
    void deletePhaseRetired(Long id);

    /**
     * 批量删除项目阶段。
     */
    void deletePhaseListRetired(Collection<Long> ids);

    /**
     * 查询阶段详情。
     */
    ProjectPhaseRetiredDO getPhaseRetired(Long id);

    /**
     * 校验阶段存在。
     */
    ProjectPhaseRetiredDO validatePhaseExistsRetired(Long id);

    /**
     * 分页查询阶段。
     */
    PageResult<ProjectPhaseRetiredDO> getPhasePageRetired(ProjectPhasePageReqVO pageReqVO);

    /**
     * 查询项目下全部阶段（按 sort 升序）。
     */
    List<ProjectPhaseRetiredDO> getPhaseListByProjectIdRetired(Long projectId);

    /**
     * 校验阶段顺序：开始某阶段前，前序阶段须已完成或已跳过。
     */
    void validateSequenceRetired(Long phaseId);

    /**
     * 校验完成门禁：
     * <ul>
     *   <li>项目下全部任务须为已完成或已取消（基础校验）</li>
     *   <li>exit_criteria 须非空（已记录退出条件）</li>
     * </ul>
     * 返回门禁校验结果详情（含失败原因），不抛异常则通过。
     *
     * @param phaseId 阶段编号
     * @return 门禁详情（含通过/失败、未完成任务数等）
     */
    GateCheckResultRetired checkCompletionGateRetired(Long phaseId);

    /**
     * 完成阶段。先执行门禁校验，通过后写入实际结束时间与状态。
     */
    void completePhaseRetired(Long phaseId, String gateEvidence, Integer version);

    /**
     * 查询超期阶段：plan_end_time 早于当前时间且状态不在已完成(2)/已跳过(3)。
     */
    List<ProjectPhaseRetiredDO> getOverduePhasesRetired();

    /**
     * 查询临期截止阶段：plan_end_time 在 [当前时间, 当前时间+daysWithin] 区间且状态不在已完成(2)/已跳过(3)。
     */
    List<ProjectPhaseRetiredDO> getUpcomingPhasesRetired(int daysWithin);

    /**
     * 门禁校验结果详情。
     */
    class GateCheckResultRetired {
        /** 是否通过 */
        private boolean passed;
        /** 未完成任务数 */
        private long unfinishedTaskCount;
        /** 退出条件是否已记录 */
        private boolean exitCriteriaDocumented;
        /** 失败原因汇总（多行） */
        private String reason;

        public GateCheckResultRetired(boolean passed, long unfinishedTaskCount, boolean exitCriteriaDocumented, String reason) {
            this.passed = passed;
            this.unfinishedTaskCount = unfinishedTaskCount;
            this.exitCriteriaDocumented = exitCriteriaDocumented;
            this.reason = reason;
        }

        public boolean isPassed() {
            return passed;
        }

        public long getUnfinishedTaskCount() {
            return unfinishedTaskCount;
        }

        public boolean isExitCriteriaDocumented() {
            return exitCriteriaDocumented;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 查询当前时间（便于测试覆盖）。
     */
    LocalDateTime nowRetired();
}
