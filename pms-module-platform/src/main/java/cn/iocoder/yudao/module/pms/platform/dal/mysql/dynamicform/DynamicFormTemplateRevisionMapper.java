package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormDraftCreateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionPublishUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DynamicFormTemplateRevisionMapper {

    int insert(@Param("row") DynamicFormTemplateRevisionDO row);

    DynamicFormTemplateRevisionDO selectByRow(@Param("query") DynamicFormRevisionRowQuery query);

    DynamicFormTemplateRevisionDO selectForUpdate(@Param("query") DynamicFormRevisionLockQuery query);

    DynamicFormTemplateRevisionDO selectDraftForUpdate(@Param("query") DynamicFormDraftCreateQuery query);

    DynamicFormTemplateRevisionDO selectCurrentPublishedForUpdate(@Param("query") DynamicFormDraftCreateQuery query);

    int updateDraftIfMatch(@Param("row") DynamicFormTemplateRevisionDO row);

    int publishIfMatch(@Param("update") DynamicFormRevisionPublishUpdate update);
}
