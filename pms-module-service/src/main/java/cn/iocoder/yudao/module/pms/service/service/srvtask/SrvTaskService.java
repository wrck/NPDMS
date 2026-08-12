package cn.iocoder.yudao.module.pms.service.service.srvtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskDO;

/**
 * 巡检任务 Service 接口
 */
public interface SrvTaskService {

    /**
     * 创建巡检任务
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvTask(SrvTaskSaveReqVO createReqVO);

    /**
     * 更新巡检任务
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvTask(SrvTaskSaveReqVO updateReqVO);

    /**
     * 删除巡检任务
     *
     * @param id 编号
     */
    void deleteSrvTask(Long id);

    /**
     * 获得巡检任务分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvTaskDO> getSrvTaskPage(SrvTaskPageReqVO pageReqVO);

    /**
     * 获得巡检任务
     *
     * @param id 编号
     * @return 巡检任务
     */
    SrvTaskDO getSrvTask(Long id);

    /**
     * 校验设备账号有效性
     *
     * @param id 任务编号
     */
    void validateEquipmentAccount(Long id);

    /**
     * 提交巡检任务（0草稿 → 1待执行）
     *
     * @param id 编号
     */
    void submitSrvTask(Long id);

    /**
     * 开始执行（1待执行 → 2执行中）
     *
     * @param id 编号
     */
    void startExecution(Long id);

    /**
     * 完成执行（2执行中 → 3待确认）
     *
     * @param id 编号
     */
    void completeExecution(Long id);

    /**
     * 确认报告（3待确认 → 4已完成）
     *
     * @param id 编号
     */
    void confirmReport(Long id);

    /**
     * 取消（0草稿/1待执行 → 5已取消）
     *
     * @param id 编号
     */
    void cancelSrvTask(Long id);

}
