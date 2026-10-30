package cn.iocoder.yudao.module.pms.platform.dal.mysql.evidence;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence.EvidenceResultRefDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EvidenceResultRefMapper extends BaseMapperX<EvidenceResultRefDO> {

    default List<EvidenceResultRefDO> selectByResultId(String resultId) {
        return selectList(new LambdaQueryWrapperX<EvidenceResultRefDO>()
                .eq(EvidenceResultRefDO::getResultId, resultId));
    }

    default List<EvidenceResultRefDO> selectByEvidenceId(Long evidenceId) {
        return selectList(new LambdaQueryWrapperX<EvidenceResultRefDO>()
                .eq(EvidenceResultRefDO::getEvidenceId, evidenceId));
    }
}
