package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectVerifiedOperationScope;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dependency-free behavioral cases; also invoked by the JUnit bridge. No framework substitutes. */
public final class ProjectVerifiedOperationScopeCases {
    private static int count;
    private ProjectVerifiedOperationScopeCases() { }
    public static void main(String[] args) throws Exception { run(); }
    @SuppressWarnings("try")
    public static void run() throws Exception {
        count = 0;
        var selection = new ProjectBusinessExecutionSelection(null,
                new ProjectStageExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,true));
        var frame = new ProjectVerifiedOperationScope.Frame(1L,2L,3L,"SOL","SITE_SURVEY","CONFIRM",1,"11",selection);
        check(ProjectVerifiedOperationScope.current() == null,"initially empty");
        try (var outer = ProjectVerifiedOperationScope.open(frame)) {
            check(match(selection,"CONFIRM",1,"11"),"exact operation matches");
            check(!match(selection,"REJECT",1,"11"),"different operation rejected");
            check(!match(selection,"CONFIRM",2,"11"),"different operation version rejected");
            check(!match(selection,"CONFIRM",1,"12"),"different object rejected");
            check(!match(selection,"CONFIRM",1,null),"missing object is not a wildcard");
            check(!match(selection,null,1,"11"),"missing operation rejected");
            check(!match(selection,"CONFIRM",null,"11"),"missing version rejected");
            check(!ProjectVerifiedOperationScope.matches(1L,2L,3L,"SOL","SITE_SURVEY",selection),"legacy matching is not a permit");
            check(!ProjectVerifiedOperationScope.matches(9L,2L,3L,"SOL","SITE_SURVEY",selection,"CONFIRM",1,"11"),"tenant mismatch rejected");
            check(!ProjectVerifiedOperationScope.matches(1L,9L,3L,"SOL","SITE_SURVEY",selection,"CONFIRM",1,"11"),"actor mismatch rejected");
            var next = new ProjectBusinessExecutionSelection(null,
                    new ProjectStageExecutionContext(3L,1,4L,1,5L,1,6L,8L,1,2,true));
            check(!match(next,"CONFIRM",1,"11"),"different round rejected");
            var innerFrame = new ProjectVerifiedOperationScope.Frame(1L,2L,3L,"SOL","SITE_SURVEY","REJECT",1,"12",selection);
            try (var inner = ProjectVerifiedOperationScope.open(innerFrame)) {
                check(!match(selection,"CONFIRM",1,"11"),"outer frame cannot authorize inner operation");
                check(match(selection,"REJECT",1,"12"),"inner exact frame matches");
            }
            check(match(selection,"CONFIRM",1,"11"),"outer frame restored");
            var leaked = new AtomicBoolean(true);
            Thread other = new Thread(() -> leaked.set(ProjectVerifiedOperationScope.current() != null));
            other.start(); other.join(); check(!leaked.get(),"scope not inherited by another thread");
        }
        check(ProjectVerifiedOperationScope.current() == null,"scope cleaned up");
        var legacy = new WriteRequest(3L,"SOL","SITE_SURVEY",selection);
        check(legacy.operationCode() == null && legacy.operationVersion() == null && legacy.objectId() == null,"legacy constructor cannot assert identity");
        try (var create = ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(
                1L,2L,3L,"SOL","SITE_SURVEY","CREATE",1,null,selection))) {
            check(match(selection,"CREATE",1,null),"create matches only absent target");
            check(!match(selection,"CREATE",1,"11"),"create cannot authorize existing object");
        }
        System.out.println("ProjectVerifiedOperationScopeCases: " + count + " passed");
    }
    private static boolean match(ProjectBusinessExecutionSelection selection,String operation,Integer version,String object) {
        return ProjectVerifiedOperationScope.matches(1L,2L,3L,"SOL","SITE_SURVEY",selection,operation,version,object);
    }
    private static void check(boolean value,String label) {
        if (!value) throw new AssertionError(label);
        count++;
    }
}
