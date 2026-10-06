package cn.iocoder.yudao.module.pms.engineering.service.requirement;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;import java.util.*;
@Component @RequiredArgsConstructor
public class RequirementAnalysisDeliveryEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {
 public static final String TYPE="requirementAnalysisRevision",CODE="REQUIREMENT_ANALYSIS_COMPLETED";
 private final RequirementAnalysisMapper revisions;private final RequirementAnalysisBusinessResultSource source;
 public boolean supports(String type){return TYPE.equals(type);}
 public void validateCurrent(Long tenant,Long project,String id,Long revision){identity(tenant,project,id,revision);}
 public Identity identity(Long tenant,Long project,String id,Long revision){
  Long revisionId;try{revisionId=Long.valueOf(id);}catch(RuntimeException invalid){throw denied();}
  var row=revisions.selectRevision(new RequirementRevisionQuery(tenant,revisionId));if(row==null||!Objects.equals(project,row.getProjectId())||!Objects.equals(tenant,row.getTenantId()))throw denied();
  var fact=source.lockAndInspect(new BusinessResultSource.Query(tenant,project,RequirementAnalysisBusinessResultSource.TYPE,String.valueOf(row.getEntityId()),id));
  if(fact.result()==null||fact.result().validity()!=BusinessResultSource.Validity.CURRENT||!Objects.equals(revision,row.getRevisionNo().longValue()))throw denied();
  return new Identity("SOL","requirementAnalysis",row.getEntityId(),CODE,TYPE,id,row.getRevisionNo().longValue());
 }
 public List<Alias> aliases(Long tenant,Long project,String id,Long revision){var canonical=identity(tenant,project,id,revision);var row=revisions.selectRevision(new RequirementRevisionQuery(tenant,Long.valueOf(id)));var type=RequirementAnalysisBusinessResultSource.TYPE;
  String composite=type.ownerContext()+"."+type.entityType()+"."+type.resultType()+"|"+row.getEntityId()+"|"+id+"|"+java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(row.getFrozenAt());
  return List.of(new Alias("project_business_result",composite,canonical.businessRevisionNo()),new Alias("project_business_result",composite,null));
 }
 private static BusinessContractException denied(){return new BusinessContractException("RA_RESULT_DELIVERY_INVALID","Current effective completed revision required");}
}
