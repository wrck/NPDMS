package cn.iocoder.yudao.module.pms.project.dal.mysql.completioncertificate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.completioncertificate.vo.CompletionCertificatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.completioncertificate.CompletionCertificateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompletionCertificateMapper extends BaseMapperX<CompletionCertificateDO> {

    default CompletionCertificateDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<CompletionCertificateDO>()
                .eq(CompletionCertificateDO::getProjectId, projectId)
                .eq(CompletionCertificateDO::getCode, code));
    }

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
