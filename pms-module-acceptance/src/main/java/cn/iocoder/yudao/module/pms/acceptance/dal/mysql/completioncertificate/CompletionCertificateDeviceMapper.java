package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDeviceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CompletionCertificateDeviceMapper extends BaseMapperX<CompletionCertificateDeviceDO> {

    default List<CompletionCertificateDeviceDO> selectListByCertificateId(Long certificateId) {
        return selectList(new LambdaQueryWrapperX<CompletionCertificateDeviceDO>()
                .eq(CompletionCertificateDeviceDO::getCertificateId, certificateId)
                .orderByAsc(CompletionCertificateDeviceDO::getSort)
                .orderByAsc(CompletionCertificateDeviceDO::getId));
    }

    default void deleteByCertificateId(Long certificateId) {
        delete(new LambdaQueryWrapperX<CompletionCertificateDeviceDO>()
                .eq(CompletionCertificateDeviceDO::getCertificateId, certificateId));
    }

}
