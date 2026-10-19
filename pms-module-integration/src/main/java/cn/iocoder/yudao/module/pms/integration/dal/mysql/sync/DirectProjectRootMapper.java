package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import lombok.Data;
import java.util.List;

@Mapper public interface DirectProjectRootMapper {
    record RootQuery(Long tenantId,Long projectId) {}
    @Data class Version {
        private Long id;
        private Long tenantId;
        private Long rootProjectId;
        private String actor;
        private String changeBatchId;
    }
    record SelfPath(Long id,Long tenantId,Long projectId,String actor) {}
    DirectProjectRow lockRoot(@Param("query") RootQuery query);
    Long selectActiveVersion(@Param("query") RootQuery query);
    List<DirectProjectRow> selectRootNodes(@Param("query") RootQuery query);
    @Options(useGeneratedKeys=true,keyProperty="row.id") int insertBuilding(@Param("row") Version row);
    int insertSelfPath(@Param("row") SelfPath row);
    int activate(@Param("row") Version row);
}
