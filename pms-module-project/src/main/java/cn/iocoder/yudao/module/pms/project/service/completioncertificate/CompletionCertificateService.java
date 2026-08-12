package cn.iocoder.yudao.module.pms.project.service.completioncertificate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo.CompletionCertificatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo.CompletionCertificateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.completioncertificate.CompletionCertificateDO;

/**
 * 电子完工证明 Service 接口
 */
public interface CompletionCertificateService {

    /**
     * 创建电子完工证明
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createCompletionCertificate(CompletionCertificateSaveReqVO createReqVO);

    /**
     * 更新电子完工证明
     *
     * @param updateReqVO 更新信息
     */
    void updateCompletionCertificate(CompletionCertificateSaveReqVO updateReqVO);

    /**
     * 删除电子完工证明
     *
     * @param id 编号
     */
    void deleteCompletionCertificate(Long id);

    /**
     * 获得电子完工证明分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<CompletionCertificateDO> getCompletionCertificatePage(CompletionCertificatePageReqVO pageReqVO);

    /**
     * 获得电子完工证明
     *
     * @param id 编号
     * @return 电子完工证明
     */
    CompletionCertificateDO getCompletionCertificate(Long id);

    /**
     * 提交（0草稿 → 1待客户确认）
     *
     * @param id 编号
     */
    void submitCompletionCertificate(Long id);

    /**
     * 客户确认（1待客户确认 → 2客户已确认）
     *
     * @param id 编号
     */
    void customerConfirm(Long id);

    /**
     * 驳回（1待客户确认 → 4已驳回）
     *
     * @param id 编号
     */
    void rejectCompletionCertificate(Long id);

    /**
     * 归档（2客户已确认 → 3已归档）
     *
     * @param id 编号
     */
    void archiveCompletionCertificate(Long id);

}
