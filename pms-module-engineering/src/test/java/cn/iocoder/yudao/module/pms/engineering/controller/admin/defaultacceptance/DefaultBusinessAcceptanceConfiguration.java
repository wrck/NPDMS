package cn.iocoder.yudao.module.pms.engineering.controller.admin.defaultacceptance;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

/** Opt-in real-server acceptance assembly. Never included in the production application artifact. */
@Configuration(proxyBeanMethods=false)
@Profile("default-business-acceptance")
public class DefaultBusinessAcceptanceConfiguration {
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_default_sheet")
    @ProjectBusinessModel(ownerModule="IT",entityType="defaultSheet",stableCode="IT_DEFAULT_SHEET",name="默认聚合验收单",permissionPrefix="pms:default-acceptance",
            completionCondition="{\"kind\":\"ALL\",\"children\":[{\"kind\":\"FIELD\",\"fieldCode\":\"confirmed\",\"operator\":\"EQ\",\"values\":[true]},{\"kind\":\"DELIVERY\",\"fieldCode\":\"ATTACHMENT\",\"operator\":\"EQ\",\"values\":[true]}]}")
    public static class Sheet extends BaseProjectBusinessEntity {
        @BusinessModelField(name="名称") @jakarta.validation.constraints.NotBlank private String title;
        @BusinessModelField(name="已确认") private Boolean confirmed;
        @BusinessModelField(name="说明") @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark;
        @BusinessModelField(name="所属明细") @TableField(exist=false) @BusinessOwnedDetails(mapper=SheetLineMapper.class) private List<SheetLine> lines;
    }
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_default_sheet_line")
    public static class SheetLine extends OwnedBusinessDetailEntity {
        @BusinessModelField(name="明细名称") @jakarta.validation.constraints.NotBlank private String description;
        @BusinessModelField(name="数量") private java.math.BigDecimal quantity;
        @BusinessModelField(name="启用") private Boolean enabled;
    }
    public interface SheetMapper extends BusinessMapper<Sheet>{}
    public interface SheetLineMapper extends BusinessDetailMapper<SheetLine>{}
    @Service @Profile("default-business-acceptance")
    public static class SheetService extends DefaultProjectBusinessService<SheetMapper,Sheet>{}
    @RestController @Profile("default-business-acceptance") @RequestMapping("/api/v1/pms/default-acceptance/sheets")
    public static class SheetController extends ProjectBusinessController<SheetService,Sheet>{}

    // A second, previously absent business: only its own entity, mappings and thin inheritance.
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_default_memo")
    @ProjectBusinessModel(ownerModule="IT",entityType="defaultMemo",stableCode="IT_DEFAULT_MEMO",name="默认版本备忘",permissionPrefix="pms:default-acceptance")
    public static class Memo extends BaseProjectBusinessEntity {
        @BusinessModelField(name="标题") @jakarta.validation.constraints.NotBlank private String title;
        @BusinessModelField(name="正文") @TableField(updateStrategy=FieldStrategy.ALWAYS) private String content;
        @BusinessModelField(name="启用") private Boolean enabled;
    }
    @Data @lombok.experimental.Accessors(chain=false) @EqualsAndHashCode(callSuper=true) @TableName("it_default_memo_revision")
    public static class MemoRevision extends Memo implements MutableEntityRevision {
        private Long entityId; private Integer revisionNo; private Long sourceRevisionId; private Long baseEffectiveRevisionId;
        private Integer baseEntityVersion; private String changeReason; private Long frozenBy; private LocalDateTime frozenAt;
        @TableField("revision_state") private String revisionStateText; private Boolean effective;
        public EntityRef entityRef(){return new EntityRef(getTenantId(),"IT","defaultMemo",entityId);}
        public EntityVersionProvider.Revision.State revisionState(){return EntityVersionProvider.Revision.State.valueOf(revisionStateText);}
        public void setRevisionState(EntityVersionProvider.Revision.State state){revisionStateText=state.name();}
        public boolean effective(){return Boolean.TRUE.equals(effective);}
        public void setEffective(boolean value){effective=value;}
    }
    public interface MemoMapper extends BusinessMapper<Memo>{}
    public interface MemoRevisionMapper extends BusinessRevisionMapper<MemoRevision>{}
    @Service @Profile("default-business-acceptance")
    public static class MemoService extends DefaultVersionedProjectBusinessService<MemoMapper,Memo,MemoRevisionMapper,MemoRevision>{}
    @RestController @Profile("default-business-acceptance") @RequestMapping("/api/v1/pms/default-acceptance/memos")
    public static class MemoController extends VersionedProjectBusinessController<MemoService,Memo>{}

    @Bean SheetMapper acceptanceSheetMapper(SqlSessionTemplate sessions){return mapper(sessions,SheetMapper.class);}
    @Bean SheetLineMapper acceptanceSheetLineMapper(SqlSessionTemplate sessions){return mapper(sessions,SheetLineMapper.class);}
    @Bean MemoMapper acceptanceMemoMapper(SqlSessionTemplate sessions){return mapper(sessions,MemoMapper.class);}
    @Bean MemoRevisionMapper acceptanceMemoRevisionMapper(SqlSessionTemplate sessions){return mapper(sessions,MemoRevisionMapper.class);}
    private static <T>T mapper(SqlSessionTemplate sessions,Class<T> type){sessions.getConfiguration().addMapper(type);return sessions.getMapper(type);}
}
