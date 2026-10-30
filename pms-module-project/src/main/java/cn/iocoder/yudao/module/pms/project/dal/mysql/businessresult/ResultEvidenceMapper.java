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
    boolean hasInvalidatedItems(@Param("query") HistoricalComparison query);
    /** 只读展示查询：当前订阅检查点对应的最新扫描；无记录返回null。 */
    ResultEvidenceScanDO selectScanForSubscription(@Param("query") SubscriptionScan query);
    /** 只读展示查询：单次扫描内去重的不合格原因码，按limit有界返回，不解释为完整集合。 */
    java.util.List<String> selectDistinctReasons(@Param("query") ScanReasons query);

    record ScanIdentity(Long tenantId, Long projectId, Long subscriptionId, Integer subscriptionVersion) { }
    record SubscriptionScan(Long tenantId, Long projectId, Long subscriptionId, Integer subscriptionVersion) { }
    record ScanReasons(Long tenantId, Long projectId, Long scanId, int limit) { }
    record ScanId(Long tenantId, Long projectId, Long id) { }
    record Progress(Long tenantId, Long projectId, Long id, int expectedVersion, long afterCandidateId, String accumulator, String status) { }
    record Items(Long tenantId, Long projectId, Long scanId, long afterId, int limit) { }
    record HistoricalComparison(Long tenantId, Long projectId, Long originalScanId, Long currentScanId) { }
}
