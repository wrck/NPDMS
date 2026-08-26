package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DynamicFormInstanceMapper {
    int insert(@Param("row") DynamicFormInstanceDO row);
    DynamicFormInstanceDO selectForUpdate(@Param("query") DynamicFormRowQuery query);
    DynamicFormInstanceDO selectByItemForUpdate(@Param("query") DynamicFormItemQuery query);
    List<DynamicFormInstanceDO> selectListForUpdate(@Param("query") PreparationChildrenQuery query);
    List<DynamicFormInstanceDO> selectListByItemIds(@Param("query") DynamicFormItemListQuery query);
    int updateDraftIfMatch(@Param("update") DynamicFormDraftUpdate update);
    int freezeIfMatch(@Param("update") DynamicFormFreezeUpdate update);
}
