package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionPdfBindingDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionPdfBindingQuery;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface CompletionPdfBindingMapper extends BaseMapperX<CompletionPdfBindingDO> {
    private LambdaQueryWrapperX<CompletionPdfBindingDO> scope(CompletionPdfBindingQuery query) {
        return new LambdaQueryWrapperX<CompletionPdfBindingDO>()
                .eq(CompletionPdfBindingDO::getTenantId,query.tenantId())
                .eq(CompletionPdfBindingDO::getCertificateId,query.certificateId())
                .eq(CompletionPdfBindingDO::getCertificateVersion,query.certificateVersion())
                .eq(CompletionPdfBindingDO::getDocumentKind,query.documentKind());
    }
    default CompletionPdfBindingDO selectBinding(CompletionPdfBindingQuery query) {
        return selectOne(scope(query).eq(CompletionPdfBindingDO::getLayoutKey,query.layoutKey()));
    }
    default boolean hasBaseline(CompletionPdfBindingQuery query) { return selectCount(scope(query))>0; }
}
