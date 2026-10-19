package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractCompletionApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DppmsContractCompletionMapper {
    record ScopeRow(String kind,Long id) {}
    record ScopeBatch(Long tenantId,List<ScopeRow> rows) {}
    record OrganizationRow(String kind,Long id,String code,String name) {}
    record OrganizationBatch(Long tenantId,List<OrganizationRow> rows) {}
    record Query(Long tenantId) {}
    void createScope();
    void insertScope(@Param("query") ScopeBatch query);
    void createCompanies();
    void createDepartments();
    void insertCompanies(@Param("query") OrganizationBatch query);
    void insertDepartments(@Param("query") OrganizationBatch query);
    void createUniqueContracts(@Param("query") Query query);
    List<Long> selectContractsForUpdate(@Param("query") Query query);
    long countValidInputs(@Param("query") Query query);
    List<String> selectAffectedContractNumbers(@Param("query") Query query);
    List<String> selectCompanyChangeNumbers(@Param("query") Query query);
    List<String> selectFieldChangeNumbers(@Param("query") Query query);
    long countShipmentChanges(@Param("query") Query query);
    long countPackageChanges(@Param("query") Query query);
    int updateContractCompanies(@Param("query") Query query);
    int updateShipmentReferences(@Param("query") Query query);
    int updatePackageReferences(@Param("query") Query query);
    void createShipmentFields(@Param("query") Query query);
    void createReceivableFields(@Param("query") Query query);
    int updateContractFields(@Param("query") Query query);
    List<DppmsContractCompletionApi.Projection> selectProjection(@Param("query") DppmsContractCompletionApi.ProjectionQuery query);
    void dropReceivableFields();
    void dropShipmentFields();
    void dropUniqueContracts();
    void dropDepartments();
    void dropCompanies();
    void dropScope();
}
