package cn.iocoder.yudao.module.pms.project.service.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;

/**
 * 交付件完整性检查 Service 接口
 */
public interface DeliverableChecklistService {

    /**
     * 创建交付件检查
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createDeliverableChecklist(DeliverableChecklistSaveReqVO createReqVO);

    /**
     * 更新交付件检查
     *
     * @param updateReqVO 更新信息
     */
    void updateDeliverableChecklist(DeliverableChecklistSaveReqVO updateReqVO);

    /**
     * 删除交付件检查
     *
     * @param id 编号
     */
    void deleteDeliverableChecklist(Long id);

    /**
     * 获得交付件检查分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<DeliverableChecklistDO> getDeliverableChecklistPage(DeliverableChecklistPageReqVO pageReqVO);

    /**
     * 获得交付件检查
     *
     * @param id 编号
     * @return 交付件检查
     */
    DeliverableChecklistDO getDeliverableChecklist(Long id);

    /**
     * 提交（0草稿 → 1已提交）
     *
     * @param id 编号
     */
    void submitDeliverableChecklist(Long id);

    /**
     * 通过（1已提交 → 2已通过）
     *
     * @param id 编号
     */
    void passDeliverableChecklist(Long id);

    /**
     * 驳回（1已提交 → 3已驳回）
     *
     * @param id 编号
     */
    void rejectDeliverableChecklist(Long id);

}
