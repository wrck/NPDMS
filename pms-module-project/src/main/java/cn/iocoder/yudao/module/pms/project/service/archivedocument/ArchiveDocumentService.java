package cn.iocoder.yudao.module.pms.project.service.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.archivedocument.ArchiveDocumentDO;

/**
 * 交付资料归档 Service 接口
 */
public interface ArchiveDocumentService {

    /**
     * 创建归档文档
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createArchiveDocument(ArchiveDocumentSaveReqVO createReqVO);

    /**
     * 更新归档文档
     *
     * @param updateReqVO 更新信息
     */
    void updateArchiveDocument(ArchiveDocumentSaveReqVO updateReqVO);

    /**
     * 删除归档文档
     *
     * @param id 编号
     */
    void deleteArchiveDocument(Long id);

    /**
     * 获得归档文档分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<ArchiveDocumentDO> getArchiveDocumentPage(ArchiveDocumentPageReqVO pageReqVO);

    /**
     * 获得归档文档
     *
     * @param id 编号
     * @return 归档文档
     */
    ArchiveDocumentDO getArchiveDocument(Long id);

    /**
     * 提交（0草稿 → 1待归档）
     *
     * @param id 编号
     */
    void submitArchiveDocument(Long id);

    /**
     * 归档（1待归档 → 2已归档）
     * 归档后版本不可覆盖
     *
     * @param id 编号
     */
    void archiveArchiveDocument(Long id);

}
