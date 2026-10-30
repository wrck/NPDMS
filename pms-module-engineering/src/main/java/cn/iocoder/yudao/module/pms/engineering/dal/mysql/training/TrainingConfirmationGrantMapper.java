package cn.iocoder.yudao.module.pms.engineering.dal.mysql.training;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingConfirmationGrantDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.*;
import org.apache.ibatis.annotations.*;
@Mapper public interface TrainingConfirmationGrantMapper extends BaseMapperX<TrainingConfirmationGrantDO> {
 TrainingConfirmationGrantDO selectCurrentGrant(@Param("query") TrainingConfirmationGrantQuery query);
 Long selectLatestIssuance(@Param("query") TrainingGrantIssuanceQuery query);
}
