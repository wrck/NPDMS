package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.CompletionSatisfactionBundleDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CompletionSatisfactionBundleMapper extends BaseMapperX<CompletionSatisfactionBundleDO> {
    int revokeActiveGrants(@Param("query") CompletionGrantRevocation query);
    default CompletionSatisfactionBundleDO selectByCertificate(CompletionBundleCertificateQuery query) {
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CompletionSatisfactionBundleDO>()
                .eq(CompletionSatisfactionBundleDO::getTenantId, query.tenantId())
                .eq(CompletionSatisfactionBundleDO::getCertificateId, query.certificateId()));
    }
    default CompletionSatisfactionBundleDO selectByCollection(CompletionSatisfactionBundleQuery query) {
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CompletionSatisfactionBundleDO>()
                .eq(CompletionSatisfactionBundleDO::getTenantId, query.tenantId())
                .eq(CompletionSatisfactionBundleDO::getCollectionKey, query.collectionKey()));
    }
    CompletionSatisfactionBundleDO selectByCollectionForUpdate(@Param("query") CompletionSatisfactionBundleQuery query);
    int confirmPendingCertificate(@Param("query") CompletionSatisfactionConfirmUpdate query);
}
