package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverExternalItemQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverExternalDeviceUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistExternalQueryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverExternalChecklistMapper {
    Long lockItem(@Param("query") CutoverExternalItemQuery query);
    int updateDevice(@Param("command") CutoverExternalDeviceUpdate command);
    int finishQuery(@Param("result") CutoverChecklistExternalQueryDO result);
}
