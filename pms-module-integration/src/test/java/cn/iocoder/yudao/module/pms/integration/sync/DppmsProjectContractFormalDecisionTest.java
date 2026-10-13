package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectContractRelationSyncTemplate.*;

class DppmsProjectContractFormalDecisionTest {
    static Row source(int id,int member,String group,boolean historical,String created,String creator,String updated,String updater) {
        var f=new LinkedHashMap<String,Object>();columns().forEach(name->f.put(name,null));
        f.put("id",id);f.put("member_id",member);f.put("group_id",member+100);f.put("contractNo","C1");
        for(String name:List.of("projectGroupCode","member_projectGroupCode","group_projectGroupCode")) f.put(name,group);
        f.put("originalProjectId",50370);f.put("originalProjectCode","P1");f.put("group_projectType","10");
        f.put("member_projectCode",historical?"P1-his":"P1");f.put("projectCandidateCount",1);
        f.put("createTime",created);f.put("createBy",creator);f.put("updateTime",updated);f.put("updateBy",updater);
        return new Row(OBJECT,id+":"+member,f,null);
    }
    static Row grouped(Row row,Row canonical,long size,Row latest) {
        var f=new LinkedHashMap<>(row.fields());
        f.putIfAbsent("projectCandidateCount",1);
        columns().forEach(name->f.put("canonical_"+name,canonical.fields().get(name)));
        f.put("formalGroupSize",size);f.put("formalStatus",canonical.fields().get("member_projectCode").toString().endsWith("-his")?"HISTORICAL":"ACTIVE");
        f.put("formalCreatedAt",DppmsProjectContractRelationSyncAdapter.auditValue(canonical.fields(),"createTime"));
        f.put("formalCreator",DppmsProjectContractRelationSyncAdapter.auditValue(canonical.fields(),"createBy"));
        f.put("formalUpdatedAt",DppmsProjectContractRelationSyncAdapter.auditValue(latest.fields(),"updateTime"));
        f.put("formalUpdater",DppmsProjectContractRelationSyncAdapter.auditValue(latest.fields(),"updateBy"));
        f.put("formalCreateConflicts",0);f.put("formalUpdateConflicts",0);
        return new Row(OBJECT,DppmsProjectContractFormalDecision.orderedSourceKey(f),f,row.targetId());
    }
    @Test void currentFactWinsAcrossOldGroupsEvenWhenHistoryIsEarlierAndUpdatedLater() {
        var current=source(20,30,"g2",false,"2022-01-01 00:00:00","sys",null,null);
        var history=source(10,11,"g1",true,"2020-01-01 00:00:00","unselected-person","2025-01-01 00:00:00","unselected-person");
        var selected=grouped(current,current,2,current);var discarded=grouped(history,current,2,current);
        var decisions=DppmsProjectContractFormalDecision.resolve(List.of(selected,discarded));
        var result=decisions.get(discarded.sourceKey());
        assertEquals("20:30",result.sourceKey());assertEquals("ACTIVE",result.status());
        assertEquals("sys",result.creator());assertNull(result.updater());assertEquals(result.createdAt(),result.updatedAt());
        assertTrue(selected.sourceKey().compareTo(discarded.sourceKey())<0);
        var fields=DppmsProjectContractFormalDecision.fieldDecisions(history.fields(),result);
        for(String name:List.of("status","createTime","createBy","updateTime","updateBy","member_createTime","member_updateBy"))
            assertTrue(fields.get(name).contains("保留同一关系的当前事实"));
    }
    @Test void sameStatusUsesEarliestCreationAndLatestNonNullUpdateFromDifferentMembers() {
        var first=source(10,11,"g1",false,"2020-01-01 00:00:00","sys",null,null);
        var latest=source(20,30,"g2",false,"2022-01-01 00:00:00","other","2025-01-01 00:00:00","异常数据处理");
        var rows=List.of(grouped(first,first,2,latest),grouped(latest,first,2,latest));
        var result=DppmsProjectContractFormalDecision.resolve(rows).get(rows.getLast().sourceKey());
        assertEquals("2020-01-01T00:00",result.createdAt().toString());assertEquals("2025-01-01T00:00",result.updatedAt().toString());
        assertEquals("sys",result.creator());assertEquals("异常数据处理",result.updater());
        assertTrue(DppmsProjectContractFormalDecision.fieldDecisions(latest.fields(),result).get("createBy").startsWith("不独立迁入"));
    }
    @Test void historicalOnlyGroupKeepsHistoryAndUsesMemberAuditWhenContractAuditIsMissing() {
        var raw=source(1,2,"g1",true,null,null,null,null);
        raw.fields().put("member_createTime","2020-01-01 00:00:00");raw.fields().put("member_createBy","sys");
        var row=grouped(raw,raw,1,raw);var result=DppmsProjectContractFormalDecision.resolve(List.of(row)).get(row.sourceKey());
        assertEquals("HISTORICAL",result.status());assertEquals("sys",result.creator());assertEquals(result.createdAt(),result.updatedAt());
    }
    @Test void tiedDifferentAuditorsAndIncompleteOrInconsistentFactsAreRejected() {
        var raw=source(1,2,"g1",false,"2020-01-01 00:00:00",null,null,null);
        for(String field:List.of("formalCreateConflicts","formalUpdateConflicts")) {
            var row=grouped(raw,raw,2,raw);row.fields().put(field,1);
            assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(List.of(row)));
        }
        var incomplete=grouped(raw,raw,1,raw);incomplete.fields().remove("canonical_group_updateBy");
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(List.of(incomplete)));
        var wrongIdentity=grouped(raw,raw,1,raw);wrongIdentity.fields().put("canonical_originalProjectId",999);
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(List.of(wrongIdentity)));
        var wrongUpdate=grouped(raw,raw,1,raw);wrongUpdate.fields().put("formalUpdater","sys");
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(List.of(wrongUpdate)));
    }
    @Test void partialOldGroupCannotSupplyDifferentFormalDecisionsOrTakeOverAnExistingTarget() {
        var raw=source(1,2,"g1",false,"2020-01-01 00:00:00",null,null,null);
        var second=source(3,4,"g2",false,"2021-01-01 00:00:00",null,null,null);
        var a=grouped(raw,raw,2,raw);var b=grouped(second,raw,3,raw);
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(List.of(a,b)));
        var takeover=grouped(second,raw,2,raw);takeover.fields().put("reviewedBefore",Map.of("id",1));
        assertThrows(IllegalArgumentException.class,()->DppmsProjectContractFormalDecision.resolve(List.of(takeover)));
    }
}
