package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;

/** Authoring metadata for the existing Owner commands; does not enable all-entry execution control. */
@Component
public class SiteSurveyOperationProvider implements ProjectBusinessOperationProvider {
    @Override
    public List<ProjectBusinessOperationDescriptor> operations() {
        return List.of(
                new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.CREATE", 1, "SOL", "SITE_SURVEY", "创建工勘", "CREATE",
                        Set.of("PRE", "POST"), SiteSurveyEntityService.class, "createSiteSurveyEntity"),
                new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.UPDATE", 1, "SOL", "SITE_SURVEY", "保存工勘", "UPDATE",
                        Set.of("PRE", "POST"), SiteSurveyEntityService.class, "updateSiteSurveyEntity"),
                new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.DELETE", 1, "SOL", "SITE_SURVEY", "删除草稿", "DELETE",
                        Set.of("PRE", "POST"), SiteSurveyEntityService.class, "deleteSiteSurveyEntity"),
                new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.CONFIRM", 1, "SOL", "SITE_SURVEY", "确认工勘", "CONFIRM",
                        Set.of("PRE", "POST"), SiteSurveyEntityService.class, "confirmSiteSurveyEntity"),
                new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.REJECT", 1, "SOL", "SITE_SURVEY", "驳回工勘", "REJECT",
                        Set.of("PRE", "POST"), SiteSurveyEntityService.class, "rejectSiteSurveyEntity"),
                new ProjectBusinessOperationDescriptor("SOL.SITE_SURVEY.ARCHIVE", 1, "SOL", "SITE_SURVEY", "归档工勘", "ARCHIVE",
                        Set.of("PRE", "POST"), SiteSurveyEntityService.class, "archiveSiteSurveyEntity"));
    }

    /** Native functional permissions; actual Owner methods still authorize every command. */
    @Override
    public java.util.Map<String, String> permissionCodes() {
        return java.util.Map.of(
                "SOL.SITE_SURVEY.CREATE", "pms:eng-site-survey:create",
                "SOL.SITE_SURVEY.UPDATE", "pms:eng-site-survey:update",
                "SOL.SITE_SURVEY.DELETE", "pms:eng-site-survey:delete",
                "SOL.SITE_SURVEY.CONFIRM", "pms:eng-site-survey:update",
                "SOL.SITE_SURVEY.REJECT", "pms:eng-site-survey:update",
                "SOL.SITE_SURVEY.ARCHIVE", "pms:eng-site-survey:update");
    }
}
