package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleSecurityReviewDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleSecurityReviewQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InspectionRuleSecurityReviewMapper extends BaseMapperX<InspectionRuleSecurityReviewDO> {

    InspectionRuleSecurityReviewDO selectLatestByRevisionAndDigest(
            @Param("query") InspectionRuleSecurityReviewQuery query);
}
