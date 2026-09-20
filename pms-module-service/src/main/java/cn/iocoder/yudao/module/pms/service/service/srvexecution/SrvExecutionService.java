package cn.iocoder.yudao.module.pms.service.service.srvexecution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution.SrvExecutionRetiredDO;

/**
 * 巡检执行记录 Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface SrvExecutionService {

    /**
     * 创建巡检执行记录
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvExecutionRetired(SrvExecutionSaveReqVO createReqVO);

    /**
     * 更新巡检执行记录
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvExecutionRetired(SrvExecutionSaveReqVO updateReqVO);

    /**
     * 删除巡检执行记录
     *
     * @param id 编号
     */
    void deleteSrvExecutionRetired(Long id);

    /**
     * 获得巡检执行记录分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvExecutionRetiredDO> getSrvExecutionPageRetired(SrvExecutionPageReqVO pageReqVO);

    /**
     * 获得巡检执行记录
     *
     * @param id 编号
     * @return 巡检执行记录
     */
    SrvExecutionRetiredDO getSrvExecutionRetired(Long id);

    /**
     * 开始执行（0待执行 → 1执行中）
     *
     * @param id 编号
     */
    void startExecutionRetired(Long id);

    /**
     * 完成执行（1执行中 → 2已完成）
     *
     * @param id 编号
     */
    void completeExecutionRetired(Long id);

    /**
     * 标记异常（0待执行/1执行中 → 3异常）
     *
     * @param id 编号
     */
    void markAbnormalRetired(Long id);

}
