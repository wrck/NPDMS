package cn.iocoder.yudao.module.pms.service.service.srvissue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueActionReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueAssignReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueDO;

import java.util.List;

/**
 * 巡检问题与整改 Service 接口
 */
public interface SrvIssueService {

    /**
     * 创建巡检问题
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvIssue(SrvIssueSaveReqVO createReqVO);

    /**
     * 更新巡检问题
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvIssue(SrvIssueSaveReqVO updateReqVO);

    /**
     * 删除巡检问题
     *
     * @param id 编号
     */
    void deleteSrvIssue(Long id);

    /**
     * 获得巡检问题分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvIssueDO> getSrvIssuePage(SrvIssuePageReqVO pageReqVO);

    /**
     * 获得巡检问题
     *
     * @param id 编号
     * @return 巡检问题
     */
    SrvIssueDO getSrvIssue(Long id);

    /**
     * 根据任务编号获得巡检问题列表
     *
     * @param taskId 任务编号
     * @return 巡检问题列表
     */
    List<SrvIssueDO> getSrvIssueListByTask(Long taskId);

    /**
     * 分派问题（0待分派 → 1已分派）
     *
     * @param reqVO 分派信息
     */
    void assignIssue(SrvIssueAssignReqVO reqVO);

    /**
     * 提交整改方案（1已分派 → 2待验证）
     *
     * @param reqVO 整改信息
     */
    void resolveIssue(SrvIssueActionReqVO reqVO);

    /**
     * 验证（2待验证 → 3已关闭）
     *
     * @param reqVO 验证信息
     */
    void verifyIssue(SrvIssueActionReqVO reqVO);

    /**
     * 取消问题（0待分派/1已分派 → 4已取消）
     *
     * @param id 编号
     */
    void cancelIssue(Long id);

    /**
     * 校验巡检闭环：所有问题必须为已关闭或已取消
     *
     * @param taskId 任务编号
     * @return 是否通过校验
     */
    boolean validateInspectionClosure(Long taskId);

}
