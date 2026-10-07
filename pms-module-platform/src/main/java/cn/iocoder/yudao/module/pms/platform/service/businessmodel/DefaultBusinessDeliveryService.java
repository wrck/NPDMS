package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeclaredBusinessDeliveryBridge;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Ordinary delivery CRUD is part of the shared default business implementation. */
@Service @RequiredArgsConstructor
public class DefaultBusinessDeliveryService implements DefaultBusinessDeliveryApi {
    public static final String FILE_OBJECT_TYPE="DEFAULT_BUSINESS_DELIVERY";
    public static final String SOURCE=DeliveryMaterialDO.SOURCE_UPLOAD;
    private final BusinessModelCatalog catalog;
    private final cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard guard;
    private final BusinessCallerContext callers;
    private final DeclaredBusinessDeliveryBridge entities;
    private final ProjectScopeApi projects;
    private final FileUploadApplicationService uploads;
    private final FileEvidenceApi files;
    private final DeliveryMaterialMapper materials;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners> directOwners;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi> fileArtifacts;
    private cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners direct() {
        return directOwners == null ? null : directOwners.getIfAvailable();
    }

    public BusinessModelDescriptor model(String businessType) {
        var direct=direct();
        if(direct!=null) { var found=direct.byCode(businessType);if(found.isPresent())return found.get().definition(); }
        return catalog.findByStableCode(businessType).orElseThrow(()->invalid("未知业务类型"));
    }
    public static Long entityId(String key) {
        try { var id=Long.valueOf(key); if(id<=0 || !id.toString().equals(key)) throw new IllegalArgumentException(); return id; }
        catch(RuntimeException failure){throw invalid("业务实体键须为真实实体ID");}
    }
    public static void type(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_.:-]{1,64}")) throw invalid("交付件类型不合法");
    }
    private BusinessModelDescriptor authorize(Scope scope,boolean write,boolean lock) {
        if(scope==null || scope.projectId()==null || scope.projectId()<=0) throw invalid("项目ID不合法");
        type(scope.deliverableType());var model=model(scope.businessType());var caller=callers.require();
        Long id=entityId(scope.businessEntityKey());
        var direct=direct();
        if(direct!=null && direct.byCode(scope.businessType()).isPresent()) {
            var access=direct.require(caller.tenantId(),caller.userId(),model.ownerModule(),model.entityType(),id,write,lock,null);
            if(!scope.projectId().equals(access.projectId())) throw invalid("业务实体不属于指定项目");
            return model;
        }
        entities.requireDefault(caller.tenantId(),caller.userId(),model.ownerModule(),model.entityType(),id,write,lock,null);
        if(!scope.projectId().equals(entities.projectId(caller.tenantId(),model.ownerModule(),model.entityType(),id)))
            throw invalid("业务实体不属于指定项目");
        return model;
    }
    @Transactional(readOnly=true)
    public Scope context(String owner,String entityType,String key) {
        var caller=callers.require();Long id=entityId(key);var direct=direct();
        if(direct!=null) {
            var service=direct.byIdentity(owner,entityType);
            if(service.isPresent()) {
                var access=direct.require(caller.tenantId(),caller.userId(),owner,entityType,id,false,false,null);
                return new Scope(access.projectId(),service.get().definition().stableCode(),key,null);
            }
        }
        var model=catalog.require(owner,entityType);
        entities.requireDefault(caller.tenantId(),caller.userId(),owner,entityType,id,false,false,null);
        return new Scope(entities.projectId(caller.tenantId(),owner,entityType,id),model.stableCode(),key,null);
    }
    public static String objectId(BusinessModelDescriptor model,String key,Long projectId) {
        return projectId+":"+model.ownerModule()+":"+model.entityType()+":"+key;
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public Record upload(Scope scope, UploadFile file, String key) {
        if (file == null || file.content() == null || file.size() <= 0) throw invalid("上传请求不合法");
        return upload(scope, new org.springframework.web.multipart.MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return file.name(); }
            public String getContentType() { return file.mediaType(); }
            public boolean isEmpty() { return file.size() == 0; }
            public long getSize() { return file.size(); }
            public java.io.InputStream getInputStream() throws java.io.IOException {
                try { return file.content().get(); }
                catch (java.io.UncheckedIOException failure) { throw failure.getCause(); }
            }
            public byte[] getBytes() throws java.io.IOException { throw new java.io.IOException("Use the bounded common content reader"); }
            public void transferTo(java.io.File target) throws java.io.IOException { throw new java.io.IOException("Use the common storage port"); }
        }, key);
    }
    @Transactional(rollbackFor=Exception.class)
    public Record upload(Scope scope,MultipartFile file,String key) {
        var model=authorize(scope,true,true);var caller=callers.require();
        if(file==null || file.isEmpty() || file.getOriginalFilename()==null || file.getOriginalFilename().length()>255 || key==null || !key.matches("[A-Za-z0-9_.:-]{1,100}")) throw invalid("上传请求不合法");
        String slot=digest(scope.projectId()+"/"+scope.businessType()+"/"+scope.businessEntityKey()+"/"+scope.deliverableType()+"/"+caller.userId()+"/"+key);
        var initialized=uploads.initialize(new FileUploadInitializeCommand(caller.tenantId(),caller.userId(),"default-init:"+slot,
                "CREATE_ARTIFACT",null,null,"PLT",FILE_OBJECT_TYPE,objectId(model,scope.businessEntityKey(),scope.projectId()),scope.deliverableType(),
                slot,file.getOriginalFilename(),"DOCUMENT",file.getSize(),file.getContentType(),null));
        var completed=uploads.complete(new FileUploadCompleteCommand(caller.tenantId(),caller.userId(),"default-complete:"+slot,
                initialized.artifactId(),initialized.sessionId(),file,null));
        var document=files.inspectDocument(caller.tenantId(),completed.referenceId());
        if(document==null || !document.available() || !"PLT".equals(document.ownerContext())
                || !FILE_OBJECT_TYPE.equals(document.objectType()) || !objectId(model,scope.businessEntityKey(),scope.projectId()).equals(document.objectId())
                || !scope.deliverableType().equals(document.purposeCode()) || !slot.equals(document.referenceKey())) throw invalid("文件归属不匹配");
        if(!files.lockAndRevalidate(new FileEvidenceApi.Query(caller.tenantId(),document.artifactId(),document.versionNo(),document.ownerContext(),
                document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),document.sha256())).valid()) throw invalid("文件证据已失效");
        return register(scope,model,document,SOURCE,document.name(),null);
    }
    private Record register(Scope scope,BusinessModelDescriptor model,FileEvidenceApi.Document document,String sourceKind,String title,Scope source) {
        var caller=callers.require();
        var identity=digest("DEFAULT_UPLOAD:"+document.referenceId());
        var existing=materials.selectDefaultUploadIdentityForUpdate(new DeliverySourceIdentityQuery(caller.tenantId(),identity));
        if(existing!=null) {
            if(Boolean.TRUE.equals(existing.getDeleted()) || !DeliveryMaterialDO.STATUS_ACTIVE.equals(existing.getStatus())) throw invalid("原上传记录已删除，重新上传须使用新的上传请求");
            return view(existing);
        }
        var row=new DeliveryMaterialDO();row.setTenantId(caller.tenantId());row.setOwnerModule(model.ownerModule());row.setEntityType(model.entityType());
        row.setEntityId(entityId(scope.businessEntityKey()));row.setProjectId(scope.projectId());row.setBusinessTypeCode(model.stableCode());
        row.setTypeCode(scope.deliverableType());row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);row.setSourceIdentityKey(identity);
        row.setFileReferenceId(document.referenceId());row.setFileArtifactId(document.artifactId());row.setFileVersionNo(document.versionNo());
        row.setFileSha256(document.sha256());row.setFileName(document.name());row.setTitle(title);row.setSourceKind(sourceKind);
        if(source!=null){row.setSourceOwnerModule(model.ownerModule());row.setSourceEntityType(model.entityType());row.setSourceEntityId(entityId(source.businessEntityKey()));}
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);row.setArchiveStatus(DeliveryMaterialDO.ARCHIVE_NOT_REQUIRED);row.setVersion(0L);
        materials.insert(row);return view(materials.selectById(row.getId()));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public List<Record> copy(Copy request) {
        if(request==null || request.source()==null || request.target()==null || request.requestKey()==null || !request.requestKey().matches("[A-Za-z0-9_.:-]{1,100}"))throw invalid("复制交付件参数无效");
        var source=request.source();var target=request.target();
        if(!Objects.equals(source.projectId(),target.projectId()) || !Objects.equals(source.businessType(),target.businessType())
                || Objects.equals(source.businessEntityKey(),target.businessEntityKey()) || !Objects.equals(source.deliverableType(),target.deliverableType()))throw invalid("交付件复制必须保持项目和业务类型且使用不同实体键");
        String authorizationType=source.deliverableType()==null?"ATTACHMENT":source.deliverableType();
        authorize(new Scope(source.projectId(),source.businessType(),source.businessEntityKey(),authorizationType),false,true);
        var targetModel=authorize(new Scope(target.projectId(),target.businessType(),target.businessEntityKey(),authorizationType),true,true);
        var caller=callers.require();var result=new ArrayList<Record>();
        for(int page=1;;page++) {
            var records=list(source.projectId(),source.deliverableType(),source.businessType(),source.businessEntityKey(),page,200).getList();
            for(var record:records) {
                var document=file(Long.valueOf(record.id()));
                var api=fileArtifacts.getObject();
                var fact=api.inspect(new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery(document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes.READ));
                var scope=new Scope(target.projectId(),target.businessType(),target.businessEntityKey(),record.deliverableType());
                String object=objectId(targetModel,target.businessEntityKey(),target.projectId());
                String slot=UUID.nameUUIDFromBytes((caller.userId()+":"+request.requestKey()+":"+record.id()+":"+object).getBytes(StandardCharsets.UTF_8)).toString();
                var observed=projects.resolveCurrent(new ProjectCurrentScopeQuery(caller.tenantId(),caller.userId(),target.projectId(),ProjectScopeApi.ACTION_MANAGE));
                if(observed==null || observed.treeVersion()==null)throw invalid("复制目标权限失效");
                var item=new cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionItem(
                        new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery(document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes.READ,fact.fileFactVersion(),fact.scopeVersion()),
                        new cn.iocoder.yudao.module.pms.platform.api.file.dto.ExistingFileReferenceTarget("PLT",FILE_OBJECT_TYPE,object,record.deliverableType(),slot,observed.treeVersion()));
                api.attachExistingVersions(new cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand("default-copy:"+slot,List.of(item)));
                var copied=files.inspectReference(new FileEvidenceApi.Reference(caller.tenantId(),"PLT",FILE_OBJECT_TYPE,object,record.deliverableType(),slot));
                if(copied==null || !copied.available() || !Objects.equals(copied.artifactId(),document.artifactId()) || !Objects.equals(copied.versionNo(),document.versionNo()) || !Objects.equals(copied.sha256(),document.sha256()))throw invalid("复制文件事实不一致");
                result.add(register(scope,targetModel,copied,DeliveryMaterialDO.SOURCE_ASSOCIATED,record.title(),source));
            }
            if(records.size()<200)return List.copyOf(result);
        }
    }

    @Transactional(readOnly=true)
    public PageResult<Record> list(Long projectId,String deliverableType,String businessType,String businessEntityKey,int pageNo,int pageSize) {
        return listRecords(projectId,deliverableType,businessType,businessEntityKey,pageNo,pageSize,false);
    }
    @Override @Transactional(readOnly=true)
    public PageResult<Record> history(Scope scope,int pageNo,int pageSize) {
        if(scope==null || scope.businessType()==null || scope.businessEntityKey()==null)throw invalid("历史查询必须指定业务类型和实体键");
        return listRecords(scope.projectId(),scope.deliverableType(),scope.businessType(),scope.businessEntityKey(),pageNo,pageSize,true);
    }
    private PageResult<Record> listRecords(Long projectId,String deliverableType,String businessType,String businessEntityKey,int pageNo,int pageSize,boolean includeInactive) {
        if(projectId==null || projectId<=0 || pageNo<1 || pageSize<1 || pageSize>200) throw invalid("查询参数不合法");
        if(deliverableType!=null) type(deliverableType);
        if((businessType==null)!=(businessEntityKey==null)) throw invalid("业务类型和实体键须同时指定");
        var caller=callers.require();var scope=projects.resolveCurrent(new ProjectCurrentScopeQuery(caller.tenantId(),caller.userId(),projectId,ProjectScopeApi.ACTION_VIEW));
        if(scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(projectId)) throw invalid("项目不可见");
        if(businessType!=null) authorize(new Scope(projectId,businessType,businessEntityKey,deliverableType==null?"ATTACHMENT":deliverableType),false,false);
        var query=new DefaultDeliveryListQuery();query.setTenantId(caller.tenantId());query.setProjectId(projectId);query.setDeliverableType(deliverableType);
        query.setIncludeInactive(includeInactive);query.setBusinessType(businessType);query.setEntityId(businessEntityKey==null?null:entityId(businessEntityKey));query.setPageNo(pageNo);query.setPageSize(pageSize);
        var actor=new cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor(caller.tenantId(),caller.userId(),"DELIVERY_COLLECTION");
        var readableTypes=new java.util.HashSet<String>();
        var allModels=new java.util.LinkedHashMap<String,BusinessModelDescriptor>();
        catalog.all().forEach(model->allModels.put(model.stableCode(),model));
        var direct=direct();if(direct!=null)direct.definitions().forEach(model->allModels.put(model.stableCode(),model));
        for(var model:allModels.values()) {
            boolean inherited=direct!=null && direct.byCode(model.stableCode()).isPresent();
            if(!inherited && !entities.supports(model.ownerModule(),model.entityType())) continue;
            try {
                if(inherited)direct.requireReadableModel(model.stableCode());else guard.requireReadable(model,actor,"delivery");
                readableTypes.add(model.stableCode());}
            catch(BusinessContractException denied) { /* Fail closed for this business type. */ }
        }
        query.setReadableBusinessTypes(Set.copyOf(readableTypes));
        if(readableTypes.isEmpty())return new PageResult<>(List.of(),0L);
        if(businessType==null){
            // Owner state can hide individual records even when the model is readable. Apply
            // pagination after those checks, otherwise a private first row hides later public rows.
            long offset=(long)(pageNo-1)*pageSize,visibleCount=0;
            var selected=new ArrayList<Record>();var decisions=new HashMap<Scope,Boolean>();
            query.setPageNo(1);query.setPageSize(200);
            while(true){
                var candidates=materials.selectDefaultDeliveryList(query);
                for(var row:candidates){
                    if(!decisions.computeIfAbsent(scope(row),ignored->readable(row)))continue;
                    if(visibleCount>=offset && selected.size()<pageSize)selected.add(view(row,!includeInactive));
                    visibleCount++;
                }
                if(candidates.size()<200)break;
                query.setPageNo(Math.incrementExact(query.getPageNo()));
            }
            return new PageResult<>(selected,visibleCount);
        }
        var page=materials.selectDefaultDeliveryPage(query);
        // A project-wide collection must not reveal rows from business types the actor cannot read.
        var visible=page.getList().stream().filter(row->readable(row)).map(row->view(row,!includeInactive)).toList();
        return new PageResult<>(visible,(long)visible.size()==page.getList().size()?page.getTotal():visible.size());
    }
    private boolean readable(DeliveryMaterialDO row) {
        try {authorize(scope(row),false,false);return true;} catch(BusinessContractException denied){return false;}
    }
    @Override @Transactional(readOnly=true)
    public Completion completion(Scope scope) {
        authorize(scope,false,false);int page=1;
        while(true) {
            var records=list(scope.projectId(),scope.deliverableType(),scope.businessType(),scope.businessEntityKey(),page++,200);
            for(var record:records.getList()) {
                var file=files.inspectDocument(callers.require().tenantId(),Long.valueOf(record.fileReferenceId()));
                if(file!=null && file.available()) return new Completion(true,record);
            }
            if(records.getList().size()<200) return new Completion(false,null);
        }
    }
    @Transactional(readOnly=true)
    public FileEvidenceApi.Document file(Long id) {
        var row=require(id,false,false);
        var document=files.inspectDocument(callers.require().tenantId(),row.getFileReferenceId());
        if(document==null || !document.available()) throw invalid("文件已失效");
        return document;
    }
    @Transactional(readOnly=true)
    public Record get(Long id) {return view(require(id,false,false));}
    private DeliveryMaterialDO require(Long id,boolean write,boolean lock) {
        var caller=callers.require();var row=materials.selectById(id);
        if(row==null || !caller.tenantId().equals(row.getTenantId()) || !Set.of(SOURCE,DeliveryMaterialDO.SOURCE_ASSOCIATED).contains(row.getSourceKind())) throw invalid("交付件不存在");
        authorize(scope(row),write,lock);
        var document=files.inspectDocument(caller.tenantId(),row.getFileReferenceId());
        if(document==null || !FILE_OBJECT_TYPE.equals(document.objectType()) || !"PLT".equals(document.ownerContext())) throw invalid("材料由原业务管理");
        return row;
    }
    @Transactional(rollbackFor=Exception.class)
    public Record edit(Long id,Long version,String title) {
        var row=require(id,true,true);if(title==null || title.isBlank() || title.length()>255) throw invalid("标题不合法");
        mutate(row,version,title,false);return view(materials.selectById(id));
    }
    @Transactional(rollbackFor=Exception.class)
    public void delete(Long id,Long version) {var row=require(id,true,true);mutate(row,version,null,true);}
    private void mutate(DeliveryMaterialDO row,Long version,String title,boolean delete) {
        if(version==null || version<0) throw invalid("材料版本必填");
        if(materials.mutateDefaultDelivery(new DefaultDeliveryMutationQuery(callers.require().tenantId(),row.getId(),version,title,delete,
                callers.require().userId().toString()))!=1) throw invalid("材料已改变或已被归档/引用，不能修改");
    }
    private static Scope scope(DeliveryMaterialDO row) {return new Scope(row.getProjectId(),row.getBusinessTypeCode(),row.getEntityId().toString(),row.getTypeCode());}
    private static Record view(DeliveryMaterialDO row) {return view(row,true);}
    private static Record view(DeliveryMaterialDO row,boolean editable) {return new Record(row.getId().toString(),row.getProjectId(),row.getBusinessTypeCode(),
            row.getEntityId().toString(),row.getTypeCode(),row.getTitle(),row.getFileName(),row.getFileReferenceId().toString(),
            row.getFileArtifactId().toString(),row.getFileVersionNo(),row.getCreateTime(),row.getVersion(),row.getStatus(),row.getOwnerModule(),row.getEntityType(),
            row.getMaterialKind(),row.getSourceKind(),editable && !Boolean.TRUE.equals(row.getDeleted()) && DeliveryMaterialDO.STATUS_ACTIVE.equals(row.getStatus()));}
    private static String digest(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static BusinessContractException invalid(String message) {return new BusinessContractException("DEFAULT_DELIVERY_INVALID",message);}
}
