package cn.iocoder.yudao.module.pms.engineering.service.solution.business;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.business.SolutionBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.business.SolutionBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionCustomerDocumentService;
import cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class SolutionBusinessService extends DefaultProjectBusinessService<SolutionBusinessMapper,SolutionBusinessDO> {
    private final EngineeringRecordCodeGenerator codes;
    private final SolutionMapper nativeMapper;
    private final SolutionTieredReviewService reviews;
    private final SolutionCustomerDocumentService customerDocuments;
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(
            operation("review-submit","提交方案审核",prefix+":update"),operation("review-refresh","刷新方案审核",prefix+":update"),
            operation("copy-version","复制为新方案版本",prefix+":create"),operation("attach-customer-files","登记客户方案",prefix+":update"));}
    private BusinessOperationDescriptor operation(String code,String name,String permission){return new BusinessOperationDescriptor(code,1,name,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,permission);}
    @Override protected void beforeCreate(SolutionBusinessDO row){row.setCode(codes.next(row.getProjectId(),EngineeringRecordCodeGenerator.SOLUTION,nativeMapper));row.setStatus(0);row.setReviewLevel(0);if(row.getSolutionType()==null)row.setSolutionType("IMPLEMENTATION");}
    @Override protected void beforeUpdate(SolutionBusinessDO before,SolutionBusinessDO row){editable(before);if(!Objects.equals(before.getProjectId(),row.getProjectId()))throw invalid("方案不能迁移到其他项目");}
    @Override protected void beforeSave(SolutionBusinessDO before,SolutionBusinessDO row,String operation){if(SolutionCustomerDocumentService.hasNativePointer(row.getRemark()))customerDocuments.validatePointer(before==null?null:before.getId(),row.getRemark());}
    @Override protected void beforeDelete(SolutionBusinessDO row){editable(row);}
    @Override protected void validateDelivery(SolutionBusinessDO row,boolean write){if(write && !Set.of(0,3).contains(row.getStatus()))throw invalid("审批过程中的方案不能修改交付件");}
    @Override protected Map<String,Object> formContext(SolutionBusinessDO row){var result=new LinkedHashMap<String,Object>();result.put("review",reviews.read(selection(row)));result.put("policy",reviews.policy(row.getProjectId()));result.put("templateSnapshot",row.getTemplateSnapshot());return result;}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","IMPLEMENTATION_PLAN_APPROVED","实施方案已批准");}
    @Override protected Map<String,Boolean> runtimeFacts(SolutionBusinessDO row){return Map.of("BUSINESS_RECORD_SAVED",true,"IMPLEMENTATION_PLAN_APPROVED",runtimeHandlingCompleted(row));}
    @Override protected boolean runtimeHandlingCompleted(SolutionBusinessDO row){return Integer.valueOf(3).equals(row.getStatus())&&row.getApprovedTime()!=null&&row.getBaselineVersion()!=null;}
    @Override protected java.time.LocalDateTime runtimeFormedAt(SolutionBusinessDO row){return runtimeHandlingCompleted(row)?row.getApprovedTime():super.runtimeFormedAt(row);}
    public BusinessOperationReceipt submitReview(Long id,Long version,String key,String definition){return businessAction("review-submit",id,version,key,Map.of("processDefinitionId",definition),ReceiptOutcome.APPROVAL_PENDING,row->{reviews.start(new SolutionTieredReviewService.Start(row.getProjectId(),id,Math.toIntExact(version),definition));return mapper.selectById(id);});}
    public BusinessOperationReceipt refreshReview(Long id,Long version,String key){return businessAction("review-refresh",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{reviews.refresh(selection(row));return mapper.selectById(id);});}
    public BusinessOperationReceipt copyVersion(Long id,Long version,String key){return businessAction("copy-version",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->mapper.selectById(reviews.revise(selection(row))));}
    public BusinessOperationReceipt attachCustomerFiles(Long id,Long version,String key,List<Long> references){return businessAction("attach-customer-files",id,version,key,Map.of("referenceIds",references),ReceiptOutcome.SAVED,row->{customerDocuments.attach(id,new SolutionCustomerDocumentService.Attach(Math.toIntExact(version),references));return mapper.selectById(id);});}
    private SolutionTieredReviewService.Selection selection(SolutionBusinessDO row){return new SolutionTieredReviewService.Selection(row.getProjectId(),row.getId());}
    private void editable(SolutionBusinessDO row){if(!Integer.valueOf(0).equals(row.getStatus()))throw invalid("只有草稿可以修改；已提交和批准版本必须保留");}
    private BusinessContractException invalid(String message){return new BusinessContractException("SOLUTION_INVALID",message);}
}
