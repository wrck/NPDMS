package cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.BusinessResultChannelDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.BusinessResultChangeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BusinessResultJournalMapper {
    int ensureChannel(@Param("query") ChannelScope query);
    BusinessResultChannelDO selectChannelForUpdate(@Param("query") ChannelScope query);
    BusinessResultChannelDO selectChannel(@Param("query") ChannelScope query);
    BusinessResultChangeDO selectEvent(@Param("query") EventLookup query);
    BusinessResultChangeDO selectFormation(@Param("query") FormationLookup query);
    int advanceSequence(@Param("query") Advance query);
    int insertChange(@Param("row") BusinessResultChangeDO row);
    List<BusinessResultChangeDO> selectChanges(@Param("query") ChangePage query);

    record ChannelScope(Long tenantId, Long projectId, String ownerContext, String entityType, String resultType) { }
    record EventLookup(Long tenantId, Long channelId, String sourceEventId) { }
    record FormationLookup(Long tenantId, Long channelId, String objectId, String resultId) { }
    record Advance(Long tenantId, Long channelId, long expectedSequence, long nextSequence) { }
    record ChangePage(Long tenantId, Long channelId, long afterSequence, long throughSequence, int limit) { }
}
