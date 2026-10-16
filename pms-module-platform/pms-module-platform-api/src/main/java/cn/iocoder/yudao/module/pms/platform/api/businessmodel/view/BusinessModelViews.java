package cn.iocoder.yudao.module.pms.platform.api.businessmodel.view;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Features;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.util.Map;
/** The existing public model representation, shared by generic and bound business routes. */
public abstract class BusinessModelViews {
    /** 操作执行请求：身份由服务端确定；幂等键与并发依据由客户端携带。 */
    @Data
    public static class OperationExecuteReqVO {

        @NotBlank
        private String idempotencyKey;

        private Long concurrencyBasis;

        @Positive
        private Long revisionId;

        private OperationEntryKind entryKind;

        @Pattern(regexp = "[A-Za-z0-9_.:-]{0,128}")
        private String entryCorrelationId;

        private Map<String, Object> input;

        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new BusinessContractException("REQUEST_FIELD_UNKNOWN",
                    "不支持的操作请求字段: " + name);
        }
    }

    @Data
    public static class PageQueryReqVO {

        private String sceneCode;

        private List<FieldFilterVO> filters;

        @NotNull
        @Positive
        private Integer pageSize;

        private String cursor;

        @Data
        public static class FieldFilterVO {
            @NotBlank
            private String fieldCode;
            @NotNull
            private BusinessFieldFilter.Operator operator;
            private List<Object> values;
        }
    }

    public record ModelSummaryVO(String ownerModule, String entityType, String stableCode,
                                 String title, String viewCode) {
        public static ModelSummaryVO of(BusinessModelDescriptor descriptor) {
            return new ModelSummaryVO(descriptor.ownerModule(), descriptor.entityType(),
                    descriptor.stableCode(), descriptor.title(), descriptor.viewCode());
        }
    }

    public record OperationVO(String code, String name, int version, String kind,
                              boolean executable, String reason) {
    }

    public record FieldVO(String code, String name, String type, boolean required,
                          boolean readable, boolean writable, int displayOrder, boolean listVisible,
                          boolean searchable, boolean sortable, List<FieldVO> children,boolean formVisible,boolean tabVisible) {
        public FieldVO { children=children==null?List.of():List.copyOf(children); }
        public FieldVO(String code,String name,String type,boolean required,boolean readable,boolean writable,int displayOrder,boolean listVisible,boolean searchable,boolean sortable,List<FieldVO> children){
            this(code,name,type,required,readable,writable,displayOrder,listVisible,searchable,sortable,children,readable,readable);
        }
        public FieldVO(String code,String name,String type,boolean required,boolean readable,boolean writable,int displayOrder,boolean listVisible,boolean searchable,boolean sortable){
            this(code,name,type,required,readable,writable,displayOrder,listVisible,searchable,sortable,List.of());
        }
        public FieldVO(String code,String name,String type,boolean required,boolean readable,boolean writable) {
            this(code,name,type,required,readable,writable,0,readable,
                    readable && !List.of("TEXT_LIST","OBJECT_LIST").contains(type),
                    readable && !List.of("TEXT_LIST","OBJECT_LIST").contains(type));
        }
    }

    public record CapabilityVO(String type, String configRef, boolean enabled) {
    }

    public record ModelDetailVO(String ownerModule, String entityType, String stableCode,
                                String title, String viewCode, List<FieldVO> fields,
                                List<OperationVO> operations,
                                List<CapabilityVO> capabilities, Features features, Long formRevisionId) {

        public ModelDetailVO(String ownerModule,String entityType,String stableCode,String title,String viewCode,
                             List<FieldVO> fields,List<OperationVO> operations,List<CapabilityVO> capabilities,Features features) {
            this(ownerModule,entityType,stableCode,title,viewCode,fields,operations,capabilities,features,null);
        }

        public ModelDetailVO(String ownerModule,String entityType,String stableCode,String title,String viewCode,
                             List<FieldVO> fields,List<OperationVO> operations,List<CapabilityVO> capabilities) {
            this(ownerModule,entityType,stableCode,title,viewCode,fields,operations,capabilities,new Features(false,false));
        }

        public static ModelDetailVO of(BusinessModelDescriptor descriptor, EntityActor actor,
                                BusinessAccessGuard guard) {
            List<OperationVO> operations = descriptor.operations().stream()
                    .map(operation -> executableOf(descriptor, operation, actor, guard))
                    .toList();
            return withOperations(descriptor, operations);
        }

        /** List validation needs field metadata, without evaluating write-button availability. */
        public static ModelDetailVO forQuery(BusinessModelDescriptor descriptor) {
            return withOperations(descriptor, List.of());
        }

        private static ModelDetailVO withOperations(BusinessModelDescriptor descriptor, List<OperationVO> operations) {
            List<FieldVO> fields = descriptor.fields().stream()
                    .filter(field -> field.readable() || field.writable())
                    .map(field -> new FieldVO(field.code(), field.name(), field.type().name(),
                            field.required(), field.readable(), field.writable(),field.displayOrder(),field.listVisible(),field.searchable(),field.sortable()))
                    .sorted(java.util.Comparator.comparingInt(FieldVO::displayOrder))
                    .toList();
            List<CapabilityVO> capabilities = descriptor.capabilities().stream()
                    .map(capability -> new CapabilityVO(capability.type().name(), capability.configRef(),
                            capability.enabled()))
                    .toList();
            return new ModelDetailVO(descriptor.ownerModule(), descriptor.entityType(),
                    descriptor.stableCode(), descriptor.title(), descriptor.viewCode(), fields, operations, capabilities);
        }

        private static OperationVO executableOf(BusinessModelDescriptor descriptor,
                                                BusinessOperationDescriptor operation,
                                                EntityActor actor, BusinessAccessGuard guard) {
            try {
                guard.requireWritable(descriptor, actor, "operation:" + operation.code());
                return new OperationVO(operation.code(), operation.name(), operation.version(),
                        operation.kind().name(), true, null);
            } catch (BusinessContractException ex) {
                return new OperationVO(operation.code(), operation.name(), operation.version(),
                        operation.kind().name(), false, ex.getMessage());
            }
        }
    }
}
