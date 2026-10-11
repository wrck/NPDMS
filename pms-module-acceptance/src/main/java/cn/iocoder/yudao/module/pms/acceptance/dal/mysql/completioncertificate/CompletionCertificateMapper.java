package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo.CompletionCertificatePageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompletionCertificateMapper extends ProjectScopedCodeMapper<CompletionCertificateDO> {
    int replacePrintBindings(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionPrintBindingUpdate query);
    java.util.List<CompletionCertificateDO> selectPairedWorkflowRows(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionWorkflowPageQuery query);
    Long countPairedWorkflowRows(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionWorkflowPageQuery query);
    default PageResult<CompletionCertificateDO> selectWorkflowPage(
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionWorkflowPageQuery query) {
        if(query.getVisibleProjectIds()==null || query.getVisibleProjectIds().isEmpty())return new PageResult<>(java.util.List.of(),0L);
        if(query.isPairedOnly())return new PageResult<>(selectPairedWorkflowRows(query),countPairedWorkflowRows(query));
        return selectPage(query,new LambdaQueryWrapperX<CompletionCertificateDO>()
                .eq(CompletionCertificateDO::getTenantId,query.getTenantId())
                .in(CompletionCertificateDO::getProjectId,query.getVisibleProjectIds())
                .eqIfPresent(CompletionCertificateDO::getProjectId,query.getProjectId())
                .likeIfPresent(CompletionCertificateDO::getName,query.getName())
                .eqIfPresent(CompletionCertificateDO::getStatus,query.getStatus())
                .orderByDesc(CompletionCertificateDO::getId));
    }
    CompletionCertificateDO selectDeliveryOwnerForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery query);


    default PageResult<CompletionCertificateDO> selectPage(CompletionCertificatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CompletionCertificateDO>()
                .eqIfPresent(CompletionCertificateDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(CompletionCertificateDO::getCode, reqVO.getCode())
                .likeIfPresent(CompletionCertificateDO::getName, reqVO.getName())
                .eqIfPresent(CompletionCertificateDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CompletionCertificateDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CompletionCertificateDO::getCompletionDate, reqVO.getCompletionDate())
                .orderByDesc(CompletionCertificateDO::getId));
    }

}
