package cn.iocoder.yudao.module.pms.engineering.service.stageplan.business;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.business.StagePlanBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.business.StagePlanBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemRespVO;
import cn.iocoder.yudao.module.pms.engineering.service.stageplan.StagePlanBatchService;
import cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.business.BusinessPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Default root/aggregate persistence; estimation and BPM effectiveness retain the original domain commands. */
@Service @RequiredArgsConstructor
public class StagePlanBusinessService extends DefaultProjectBusinessService<StagePlanBusinessMapper,StagePlanBusinessDO> {
    private final StagePlanBatchMapper batches;
    private final StagePlanItemMapper items;
    private final ConstructionPlanMapper durations;
    private final ConstructionPlanRevisionMapper revisions;
    private final ProjectStagePlanApi projectPlans;
    private final StagePlanBatchService nativeService;
    private final cn.iocoder.yudao.module.pms.engineering.service.stageplan.StagePlanBusinessResultSource results;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.engineering.service.constructionplan.business.DurationBusinessService durationPages;

    @Override protected int initialPageSize(){return 100;}
    @Override protected PageResult<StagePlanBusinessDO> readInitialPage(BusinessPageQuery query){
        var first=page(query);var rows=new ArrayList<>(first.getList());
        for(int pageNo=2;rows.size()<first.getTotal();pageNo++){
            query.setPageNo(pageNo);var next=page(query);if(next.getList().isEmpty())break;rows.addAll(next.getList());
        }
        query.setPageNo(1);return new PageResult<>(rows,first.getTotal());
    }
    @Override protected List<Long> initialFormIds(List<StagePlanBusinessDO> rows,Long selected){
        var ids=new LinkedHashSet<Long>();
        if(selected!=null)ids.add(selected);
        else rows.stream().filter(row->Objects.equals(row.getStatus(),0)||Objects.equals(row.getStatus(),3))
                .findFirst().or(()->rows.stream().findFirst()).ifPresent(row->ids.add(row.getId()));
        rows.stream().filter(row->Objects.equals(row.getStatus(),2)).max(Comparator.comparing(StagePlanBusinessDO::getId))
                .ifPresent(row->ids.add(row.getId()));
        return List.copyOf(ids);
    }
    @Override protected List<BusinessPageQueryApi.Page> initialRelatedPages(BusinessPageQueryApi.Request request){
        return List.of(durationPages.initializePage(new BusinessPageQueryApi.Request(request.projectId(),null,null,null),List.of()).page());
    }
    private ConstructionPlanRespVO initialDuration(){
        return initialRelatedPages().stream().filter(page->"SOL_CONSTRUCTION_PLAN".equals(page.model().stableCode()))
                .flatMap(page->page.forms().stream()).map(form->form.context().get("duration"))
                .filter(ConstructionPlanRespVO.class::isInstance).map(ConstructionPlanRespVO.class::cast).findFirst().orElse(null);
    }
    private StagePlanBusinessDO initialEffective(){
        return initialPageRows().stream().filter(row->Objects.equals(row.getStatus(),2))
                .max(Comparator.comparing(StagePlanBusinessDO::getEffectiveAt,Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(StagePlanBusinessDO::getId)).orElse(null);
    }
    @Override protected Map<String,Object> initialPageContext(BusinessPageQueryApi.Request request){
        try{return Map.of("overdue",initialPageCoversProject()?initialOverdue(request.projectId()):nativeService.getOverdueStages(request.projectId()),"overdueFailed",false);}
        catch(RuntimeException unavailable){return Map.of("overdue",List.of(),"overdueFailed",true);}
    }
    private List<cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO> initialOverdue(Long projectId){
        var current=initialEffective();if(current==null)return List.of();
        var statuses=projectPlans.listStages(actor().tenantId(),projectId).stream()
                .collect(Collectors.toMap(ProjectStagePlanApi.StagePlanFact::stageId,ProjectStagePlanApi.StagePlanFact::status));
        var today=java.time.LocalDate.now();var rows=new ArrayList<cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO>();
        for(var item:current.getItems()){
            String status=statuses.get(item.getPhaseId());
            if(item.getPlanEnd()==null || !item.getPlanEnd().isBefore(today) || "2".equals(status) || "3".equals(status))continue;
            var row=new cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO();
            row.setProjectId(projectId);row.setBatchId(current.getId());row.setPhaseId(item.getPhaseId());row.setPhaseName(item.getPhaseName());
            row.setPlanEnd(item.getPlanEnd());row.setOverdueDays(java.time.temporal.ChronoUnit.DAYS.between(item.getPlanEnd(),today));
            row.setPhaseStatus(status==null?null:Integer.valueOf(status));rows.add(row);
        }
        rows.sort(Comparator.comparing(cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO::getOverdueDays).reversed());
        return rows;
    }

    @Override protected List<BusinessOperationDescriptor> configureOperations(List<BusinessOperationDescriptor> operations){return operations.stream().filter(op->!"delete".equals(op.code())).toList();}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(
            new BusinessOperationDescriptor("estimate",1,"重新推算",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update"),
            new BusinessOperationDescriptor("submit",1,"提交计划审批",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":submit"));}
    @Override protected void beforeCreate(StagePlanBusinessDO row){
        var root=durations.selectByProjectId(actor().tenantId(),row.getProjectId());
        if(root!=null)root=durations.selectForUpdate(new ConstructionPlanLockQuery(actor().tenantId(),root.getId()));
        if(root==null)throw invalid("请先录入生效工期");
        if(batches.selectListByProject(row.getProjectId()).stream().anyMatch(batch->Objects.equals(batch.getStatus(),0)||Objects.equals(batch.getStatus(),1)))throw invalid("项目已有草稿或审批中的计划");
        if(root.getCurrentDurationRevisionId()==null)throw invalid("请先录入生效工期");
        var baseline=revisions.selectById(new ConstructionPlanRevisionLockQuery(actor().tenantId(),root.getId(),root.getCurrentDurationRevisionId()));
        if(baseline==null)throw invalid("生效工期版本不存在");
        var facts=projectPlans.listStages(actor().tenantId(),row.getProjectId());
        if(facts.isEmpty())throw invalid("项目没有可推算的阶段");
        var calculation=projectPlans.calculateSchedule(actor().tenantId(),row.getProjectId(),baseline.getStartDate(),baseline.getEndDate());
        if(calculation==null)throw invalid("项目计划推算输入不可用");
        var known=facts.stream().collect(Collectors.toMap(ProjectStagePlanApi.StagePlanFact::stageId,value->value));
        var details=new ArrayList<StagePlanItemDO>();
        for(var dates:calculation.stages()){
            var stage=known.get(dates.stageId());if(stage==null)throw invalid("冻结路径阶段实例缺失");
            var item=new StagePlanItemDO();item.setTenantId(actor().tenantId());item.setPhaseId(stage.stageId());item.setPhaseCode(stage.code());item.setPhaseName(stage.name());item.setSort(details.size());
            item.setSuggestedStart(dates.planStartTime());item.setSuggestedEnd(dates.planEndTime());item.setPlanStart(dates.planStartTime());item.setPlanEnd(dates.planEndTime());details.add(item);
        }
        row.setItems(details);row.setStatus(0);row.setDurationRevisionId(baseline.getId());row.setCalculatedStart(calculation.start());row.setCalculatedEnd(calculation.end());row.setInputSnapshot(calculation.inputSnapshot());
        var codes=details.stream().map(StagePlanItemDO::getPhaseCode).collect(Collectors.toSet());
        row.setTaskPlansJson(JsonUtils.toJsonString(projectPlans.listTaskPlans(actor().tenantId(),row.getProjectId()).stream().filter(task->codes.contains(task.stageCode())).toList()));
    }
    @Override protected void afterRead(StagePlanBusinessDO row){row.setItems(items.selectListByBatchId(row.getId()));}
    @Override protected void beforeUpdate(StagePlanBusinessDO before,StagePlanBusinessDO row){editable(before);if(!Objects.equals(before.getProjectId(),row.getProjectId()))throw invalid("计划不能迁移到其他项目");}
    @Override protected void beforeFormWrite(StagePlanBusinessDO before,StagePlanBusinessDO row,Map<String,Object> body){
        if(body.keySet().stream().anyMatch(key->!Set.of("items","tasks").contains(key)))throw invalid("计划明细字段无效");
        if(before==null){if(!body.isEmpty())throw invalid("先生成计划，再调整阶段与任务日期");return;}
        if(body.containsKey("items")){
            var updates=JsonUtils.parseArray(JsonUtils.toJsonString(body.get("items")),StagePlanItemUpdateReqVO.Item.class);
            if(updates==null)throw invalid("阶段计划明细不能为空");
            var known=row.getItems().stream().collect(Collectors.toMap(StagePlanItemDO::getId,value->value));var seen=new HashSet<Long>();
            for(var update:updates){if(update==null || update.getId()==null || !seen.add(update.getId()) || !known.containsKey(update.getId()))throw invalid("阶段明细不属于当前计划或重复");
                var item=known.get(update.getId());item.setPlanStart(update.getPlanStart());item.setPlanEnd(update.getPlanEnd());if(update.getRemark()!=null)item.setRemark(update.getRemark());}
        }
        if(body.containsKey("tasks")){
            var proposed=JsonUtils.parseArray(JsonUtils.toJsonString(body.get("tasks")),ProjectStagePlanApi.TaskPlan.class);
            var known=readTasks(before).stream().collect(Collectors.toMap(ProjectStagePlanApi.TaskPlan::taskId,value->value));var seen=new HashSet<Long>();
            if(proposed==null)throw invalid("任务计划不能为空");
            for(var task:proposed){var original=task==null?null:known.get(task.taskId());
                if(original==null || !seen.add(task.taskId()) || !Objects.equals(original.stageCode(),task.stageCode()) || !Objects.equals(original.parentTaskId(),task.parentTaskId()) || !Objects.equals(original.version(),task.version()) || !Objects.equals(original.acceptanceTime(),task.acceptanceTime()) || !Objects.equals(original.name(),task.name()))throw invalid("任务冻结身份不能修改");}
            if(!seen.equals(known.keySet()))throw invalid("不得删除计划内任务");row.setTaskPlansJson(JsonUtils.toJsonString(proposed));
        }
    }
    @Override protected void afterBodyPersisted(String operation,StagePlanBusinessDO before,StagePlanBusinessDO row){
        if(!Set.of("create","save","save-form").contains(operation))return;
        for(var item:row.getItems()){
            if(before==null){item.setBatchId(row.getId());if(items.insert(item)!=1)throw invalid("阶段计划生成失败");}
            else if(mapper.updateDetail(new StagePlanBusinessMapper.DetailUpdate(actor().tenantId(),row.getId(),item.getId(),item.getPlanStart(),item.getPlanEnd(),item.getRemark(),actor().userId().toString()))!=1)throw invalid("阶段计划明细已变化");
        }
    }
    @Override protected void beforeDelete(StagePlanBusinessDO row){throw invalid("施工计划须保留历史版本");}
    @Override protected void validateDelivery(StagePlanBusinessDO row,boolean write){if(write)editable(row);}
    @Override protected Map<String,Object> formContext(StagePlanBusinessDO row){
        // form() already authorized and loaded this aggregate. Preserve the native getBatch
        // presentation while keeping its approved baseline and frozen task snapshot intact.
        var view=BeanUtils.toBean(row,StagePlanBatchRespVO.class);
        view.setAdjustmentReasonRequired(initialPageCoversProject()?initialEffective()!=null:batches.selectListByProject(row.getProjectId()).stream()
                .anyMatch(batch->Objects.equals(batch.getStatus(),StagePlanBatchDO.STATUS_EFFECTIVE)));
        if(row.getDurationRevisionId()!=null){
            var initial=initialDuration();var plan=initial==null?durations.selectByProjectId(actor().tenantId(),row.getProjectId()):null;
            if(initial==null && plan==null)throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.STAGE_PLAN_DATE_INVALID,"计划引用的工期不存在");
            var current=initial==null?null:initial.getCurrentRevision();
            boolean currentBaseline=current!=null && Objects.equals(current.getRevisionId(),row.getDurationRevisionId());
            var baseline=currentBaseline?null:revisions.selectById(new ConstructionPlanRevisionLockQuery(actor().tenantId(),initial==null?plan.getId():initial.getPlanId(),row.getDurationRevisionId()));
            if(!currentBaseline && baseline==null)throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.STAGE_PLAN_DATE_INVALID,"计划引用的工期版本不存在");
            view.setBaselineStart(row.getCalculatedStart()==null?(currentBaseline?current.getStartDate():baseline.getStartDate()):row.getCalculatedStart());
            view.setBaselineEnd(row.getCalculatedEnd()==null?(currentBaseline?current.getEndDate():baseline.getEndDate()):row.getCalculatedEnd());
        }
        view.setTasks(readTasks(row));view.setItems(row.getItems().stream().map(item->BeanUtils.toBean(item,StagePlanItemRespVO.class)).toList());
        return Map.of("plan",view);
    }
    @Override protected Map<String,Object> approvalValues(StagePlanBusinessDO row){var values=new LinkedHashMap<>(super.approvalValues(row));values.put("tasks",readTasks(row));return values;}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","CONSTRUCTION_PLAN_APPROVED","当前施工计划已批准");}
    @Override protected RuntimeResult runtimeResult(StagePlanBusinessDO row){
        var query=new cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Query(row.getTenantId(),row.getProjectId(),cn.iocoder.yudao.module.pms.engineering.service.stageplan.StagePlanBusinessResultSource.TYPE,row.getId().toString(),null);
        cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Observation observed;
        if(initialPageCoversProject()){
            var duration=initialDuration();ConstructionPlanDO plan=null;
            if(duration!=null){plan=new ConstructionPlanDO();plan.setId(duration.getPlanId());plan.setTenantId(row.getTenantId());plan.setProjectId(row.getProjectId());
                plan.setVersion(duration.getPlanVersion());plan.setCurrentDurationRevisionId(duration.getCurrentRevision().getRevisionId());}
            observed=results.inspectRead(query,BeanUtils.toBean(row,StagePlanBatchDO.class),BeanUtils.toBean(initialEffective(),StagePlanBatchDO.class),plan);
        }else observed=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()?results.lockAndInspect(query):results.inspect(query);
        boolean approved=observed.result()!=null && observed.result().validity()==cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity.CURRENT;
        return new RuntimeResult(Map.of("BUSINESS_RECORD_SAVED",true,"CONSTRUCTION_PLAN_APPROVED",approved),approved,observed.result()==null?row.getUpdateTime():observed.result().formedAt(),observed.result()==null?observed.status().name():observed.result().observationVersion());
    }
    public BusinessOperationReceipt estimate(Long id,Long version,String key){return businessAction("estimate",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.autoEstimate(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt submit(Long id,Long version,String key,Long approver,String reason){
        var intent=new LinkedHashMap<String,Object>();intent.put("approverUserId",approver);intent.put("adjustmentReason",reason);
        return businessAction("submit",id,version,key,intent,ReceiptOutcome.APPROVAL_PENDING,row->{nativeService.submit(id,approver,reason);return mapper.selectById(id);});
    }
    private List<ProjectStagePlanApi.TaskPlan> readTasks(StagePlanBusinessDO row){return row.getTaskPlansJson()==null?List.of():JsonUtils.parseArray(row.getTaskPlansJson(),ProjectStagePlanApi.TaskPlan.class);}
    private void editable(StagePlanBusinessDO row){if(!Set.of(0,3).contains(row.getStatus()))throw invalid("只有草稿或已驳回计划可以调整");}
    private BusinessContractException invalid(String reason){return new BusinessContractException("STAGE_PLAN_INVALID",reason);}
}
