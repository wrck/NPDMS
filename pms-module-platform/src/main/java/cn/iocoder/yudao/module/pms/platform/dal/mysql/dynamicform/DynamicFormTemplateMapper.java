package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplatePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateVersionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DynamicFormTemplateMapper {

    int insert(@Param("row") DynamicFormTemplateDO row);

    List<DynamicFormTemplateDO> selectPage(@Param("query") DynamicFormTemplatePageQuery query);

    long selectCountPage(@Param("query") DynamicFormTemplatePageQuery query);

    DynamicFormTemplateDO selectByRow(@Param("query") DynamicFormTemplateRowQuery query);

    DynamicFormTemplateDO selectForUpdate(@Param("query") DynamicFormTemplateLockQuery query);

    int updateIfMatch(@Param("update") DynamicFormTemplateVersionUpdate update);
}
