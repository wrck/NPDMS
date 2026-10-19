package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShipmentSourceFactsMapper {
    List<ResolvedFact> selectFacts(@Param("query") Query query);
    record ResolvedFact(String packageSourceKey,LocalDateTime shipmentTime,String contractNo,boolean parentConflict,String contractType) {
        public ResolvedFact(String packageSourceKey,LocalDateTime shipmentTime,String contractNo,boolean parentConflict) {this(packageSourceKey,shipmentTime,contractNo,parentConflict,null);}
    }
    record Query(Long tenantId,List<String> packageSourceKeys) {}
}
