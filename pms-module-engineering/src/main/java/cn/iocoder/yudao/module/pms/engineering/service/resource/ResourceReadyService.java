package cn.iocoder.yudao.module.pms.engineering.service.resource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.resource.ResourceReadyDO;
import jakarta.validation.Valid;

/**
 * PMS 资源与备件就绪 Service 接口（FR-ENG-018）。
 * <p>
 * 资源编码在项目内唯一；未就绪资源阻断后续实施动作（如硬件安装）。
 */
public interface ResourceReadyService {

    /**
     * 创建资源就绪记录
     *
     * @param createReqVO 创建信息
     * @return 资源编号
     */
    Long createResourceReady(@Valid ResourceReadySaveReqVO createReqVO);

    /**
     * 更新资源就绪记录
     *
     * @param updateReqVO 更新信息
     */
    void updateResourceReady(@Valid ResourceReadySaveReqVO updateReqVO);

    /**
     * 删除资源就绪记录
     *
     * @param id 资源编号
     */
    void deleteResourceReady(Long id);

    /**
     * 查询资源就绪详情
     *
     * @param id 资源编号
     * @return 资源对象
     */
    ResourceReadyDO getResourceReady(Long id);

    /**
     * 校验资源就绪记录存在
     *
     * @param id 资源编号
     * @return 资源对象
     */
    ResourceReadyDO validateResourceReadyExists(Long id);

    /**
     * 分页查询资源就绪记录
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<ResourceReadyDO> getResourceReadyPage(ResourceReadyPageReqVO pageReqVO);

    /**
     * 标记就绪（0未就绪 → 1已就绪）
     *
     * @param id 资源编号
     */
    void markReady(Long id);

    /**
     * 标记异常（0未就绪 / 1已就绪 → 2异常）
     *
     * @param id 资源编号
     */
    void markAbnormal(Long id);

    /**
     * 重置为未就绪（1已就绪 / 2异常 → 0未就绪）
     *
     * @param id 资源编号
     */
    void resetToNotReady(Long id);

    /**
     * 校验项目下资源全部就绪，供同模块 ENG-C 调用阻断后续实施动作。
     * <p>
     * 若存在 ready_status != 1 的资源，抛 ENG_RESOURCE_NOT_READY。
     *
     * @param projectId 项目编号
     */
    void validateProjectResourceReady(Long projectId);
}
