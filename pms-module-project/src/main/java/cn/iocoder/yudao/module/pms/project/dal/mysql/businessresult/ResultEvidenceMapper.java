package cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ResultEvidenceMapper {
    ResultEvidenceScanDO selectScanForUpdate(@Param("query") ScanIdentity query);
    ResultEvidenceScanDO selectById(@Param("query") ScanId query);
    int insertScan(@Param("row") ResultEvidenceScanDO row);
    int advance(@Param("query") Progress query);
    int insertItem(@Param("row") ResultEvidenceItemDO row);
    List<ResultEvidenceItemDO> selectItems(@Param("query") Items query);

    record ScanIdentity(Long tenantId, Long projectId, Long subscriptionId, Integer subscriptionVersion) { }
    record ScanId(Long tenantId, Long projectId, Long id) { }
    record Progress(Long tenantId, Long projectId, Long id, int expectedVersion, long afterCandidateId, String accumulator, String status) { }
    record Items(Long tenantId, Long projectId, Long scanId, long afterId, int limit) { }
}
