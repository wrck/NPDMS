package cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query;
import java.util.Set;
public record DeviceSourceRelationshipPageQuery(Long tenantId,Long projectId,Set<String> contractNumbers,
        String deviceSn,long offset,int limit) {}
