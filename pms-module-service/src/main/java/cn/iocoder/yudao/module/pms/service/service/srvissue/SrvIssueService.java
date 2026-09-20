package cn.iocoder.yudao.module.pms.service.service.srvissue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueActionReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueAssignReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueRetiredDO;

import java.util.List;

/**
 * 巡检问题与整改 Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface SrvIssueService {

    /**
     * 创建巡检问题
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvIssueRetired(SrvIssueSaveReqVO createReqVO);

    /**
     * 更新巡检问题
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvIssueRetired(SrvIssueSaveReqVO updateReqVO);

    /**
     * 删除巡检问题
     *
     * @param id 编号
     */
    void deleteSrvIssueRetired(Long id);

    /**
     * 获得巡检问题分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvIssueRetiredDO> getSrvIssuePageRetired(SrvIssuePageReqVO pageReqVO);

    /**
     * 获得巡检问题
     *
     * @param id 编号
     * @return 巡检问题
     */
    SrvIssueRetiredDO getSrvIssueRetired(Long id);

    /**
     * 根据任务编号获得巡检问题列表
     *
     * @param taskId 任务编号
     * @return 巡检问题列表
     */
    List<SrvIssueRetiredDO> getSrvIssueListByTaskRetired(Long taskId);

    /**
     * 分派问题（0待分派 → 1已分派）
     *
     * @param reqVO 分派信息
     */
    void assignIssueRetired(SrvIssueAssignReqVO reqVO);

    /**
     * 提交整改方案（1已分派 → 2待验证）
     *
     * @param reqVO 整改信息
     */
    void resolveIssueRetired(SrvIssueActionReqVO reqVO);

    /**
     * 验证（2待验证 → 3已关闭）
     *
     * @param reqVO 验证信息
     */
    void verifyIssueRetired(SrvIssueActionReqVO reqVO);

    /**
     * 取消问题（0待分派/1已分派 → 4已取消）
     *
     * @param id 编号
     */
    void cancelIssueRetired(Long id);

    /**
     * 校验巡检闭环：所有问题必须为已关闭或已取消
     *
     * @param taskId 任务编号
     * @return 是否通过校验
     */
    boolean validateInspectionClosureRetired(Long taskId);

}
