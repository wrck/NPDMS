package cn.iocoder.yudao.module.pms.project.domain.template;

/**
 * 项目模板状态与生命周期规则（F-PM03 / PM-03）
 * <p>
 * BR-1 状态集合：DRAFT草稿/ACTIVE生效/RETIRED停用（字符串状态码）；
 * BR-3 已发布版本不可原位修改，调整走草稿→再发布；
 * BR-5 停用仅阻新项目匹配，不解除已建项目绑定；
 * BR-8 系统保留编码不得删除/复用/改义。
 */
public final class TemplateRules {

    /** 模板状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 模板状态：生效 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 模板状态：停用 */
    public static final String STATUS_RETIRED = "RETIRED";

    /** 版本状态：草稿工作副本 */
    public static final String REVISION_STATUS_DRAFT = "DRAFT";
    /** 版本状态：已发布（冻结只读） */
    public static final String REVISION_STATUS_PUBLISHED = "PUBLISHED";

    /** 草稿版本固定版本号（发布时递增冻结） */
    public static final int DRAFT_REVISION_NO = 0;

    private TemplateRules() {
    }

    /**
     * BR-3/BR-5 发布前置：模板未停用且存在草稿工作副本。
     * RETIRED 模板不得再发布（重新供给需新建模板）。
     */
    public static boolean canPublish(String templateStatus, boolean draftRevisionExists) {
        if (!draftRevisionExists) {
            return false;
        }
        return STATUS_DRAFT.equals(templateStatus) || STATUS_ACTIVE.equals(templateStatus);
    }

    /**
     * BR-5 停用前置：仅生效模板可停用；停用只阻新项目。
     */
    public static boolean canDisable(String templateStatus) {
        return STATUS_ACTIVE.equals(templateStatus);
    }

    /**
     * 删除前置：仅无已发布版本且非系统保留的模板可删除（BR-8 / 留痕要求）。
     */
    public static boolean canDelete(boolean systemReserved, boolean hasPublishedRevision) {
        return !systemReserved && !hasPublishedRevision;
    }

    /**
     * BR-3 草稿可编辑前置：模板未停用，且目标版本仍是草稿工作副本。
     * 已发布（PUBLISHED）版本应用层只读。
     */
    public static boolean canEditDraft(String templateStatus, String revisionStatus) {
        if (STATUS_RETIRED.equals(templateStatus)) {
            return false;
        }
        return REVISION_STATUS_DRAFT.equals(revisionStatus);
    }

    /**
     * BR-1 模板编码租户内唯一且创建后不可修改（编码即业务标识）。
     */
    public static boolean isCodeUnchanged(String currentCode, String updatedCode) {
        return currentCode != null && currentCode.equals(updatedCode);
    }
}
