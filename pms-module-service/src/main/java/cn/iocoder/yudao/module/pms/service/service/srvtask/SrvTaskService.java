package cn.iocoder.yudao.module.pms.service.service.srvtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskRetiredDO;

/**
 * 巡检任务 Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface SrvTaskService {

    /**
     * 创建巡检任务
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvTaskRetired(SrvTaskSaveReqVO createReqVO);

    /**
     * 更新巡检任务
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvTaskRetired(SrvTaskSaveReqVO updateReqVO);

    /**
     * 删除巡检任务
     *
     * @param id 编号
     */
    void deleteSrvTaskRetired(Long id);

    /**
     * 获得巡检任务分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvTaskRetiredDO> getSrvTaskPageRetired(SrvTaskPageReqVO pageReqVO);

    /**
     * 获得巡检任务
     *
     * @param id 编号
     * @return 巡检任务
     */
    SrvTaskRetiredDO getSrvTaskRetired(Long id);

    /**
     * 校验设备账号有效性
     *
     * @param id 任务编号
     */
    void validateEquipmentAccountRetired(Long id);

    /**
     * 提交巡检任务（0草稿 → 1待执行）
     *
     * @param id 编号
     */
    void submitSrvTaskRetired(Long id);

    /**
     * 开始执行（1待执行 → 2执行中）
     *
     * @param id 编号
     */
    void startExecutionRetired(Long id);

    /**
     * 完成执行（2执行中 → 3待确认）
     *
     * @param id 编号
     */
    void completeExecutionRetired(Long id);

    /**
     * 确认报告（3待确认 → 4已完成）
     *
     * @param id 编号
     */
    void confirmReportRetired(Long id);

    /**
     * 取消（0草稿/1待执行 → 5已取消）
     *
     * @param id 编号
     */
    void cancelSrvTaskRetired(Long id);

}
