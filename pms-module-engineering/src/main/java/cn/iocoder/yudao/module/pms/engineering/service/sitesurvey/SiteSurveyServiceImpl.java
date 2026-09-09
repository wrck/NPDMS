package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyWriteCondition;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 现场工勘 Service 实现（FR-ENG-001）。
 * 状态流转：0 草稿 → 1 已确认 / 2 已驳回；1 已确认 → 3 已归档。
 */
@Service
@Validated
public class SiteSurveyServiceImpl implements SiteSurveyService {

    @Resource
    private SiteSurveyMapper siteSurveyMapper;
    @Resource
    private EngineeringLocationFactService locationFactService;
    @Resource
    private ProjectScopeApi projectScopeApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSiteSurvey(SiteSurveySaveReqVO createReqVO) {
        if (createReqVO.getId() != null) throw exception(BAD_REQUEST, "新增工勘不得指定主键");
        Actor actor = currentActor();
        lockManageScope(createReqVO.getProjectId(), actor);
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode());
        SiteSurveyDO survey = BeanUtils.toBean(createReqVO, SiteSurveyDO.class);
        // 生命周期和首版本只由服务端初始化，不接受客户端终态或版本。
        survey.setStatus(0);
        survey.setVersion(0);
        survey.setTenantId(actor.tenantId());
        survey.setCreator(String.valueOf(actor.userId()));
        survey.setUpdater(String.valueOf(actor.userId()));
        if (createReqVO.getLocationMaintenance() == null) {
            applyLocation(survey, createReqVO.getLocation(), null, 0);
        }
        requireOne(siteSurveyMapper.insert(survey));
        if (createReqVO.getLocationMaintenance() != null) {
            applyLocation(survey, createReqVO.getLocation(), createReqVO.getLocationMaintenance(), 0);
            // 新建地点快照属于首版本，不能通过updateById让版本提前变成1。
            requireOne(siteSurveyMapper.initializeLocationIfMatch(condition(survey, actor), survey));
        }
        return survey.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSiteSurvey(SiteSurveySaveReqVO updateReqVO) {
        Actor actor = currentActor();
        SiteSurveyDO existing = lockWritableSurvey(updateReqVO.getId(), updateReqVO.getVersion(), actor);
        validateStatus(existing, 0, 2);
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())
                || !Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(BAD_REQUEST, "工勘所属项目和编码不可修改");
        }
        if (updateReqVO.getStatus() != null && !Objects.equals(existing.getStatus(), updateReqVO.getStatus())) {
            throw exception(SITE_SURVEY_STATUS_INVALID);
        }
        SiteSurveyDO update = BeanUtils.toBean(updateReqVO, SiteSurveyDO.class);
        update.setId(existing.getId());
        update.setProjectId(existing.getProjectId());
        update.setCode(existing.getCode());
        update.setStatus(existing.getStatus());
        update.setVersion(existing.getVersion());
        copyLocation(existing, update);
        if (updateReqVO.getLocationMaintenance() != null
                || updateReqVO.getLocation() != null && !Objects.equals(existing.getLocation(), updateReqVO.getLocation())) {
            String location = updateReqVO.getLocation() == null ? existing.getLocation() : updateReqVO.getLocation();
            applyLocation(update, location, updateReqVO.getLocationMaintenance(), existing.getVersion() + 1);
        }
        requireOne(siteSurveyMapper.updateDraftIfMatch(condition(existing, actor), update));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSiteSurvey(Long id, Integer expectedVersion) {
        Actor actor = currentActor();
        SiteSurveyDO survey = lockWritableSurvey(id, expectedVersion, actor);
        validateStatus(survey, 0);
        requireOne(siteSurveyMapper.deleteDraftIfMatch(condition(survey, actor)));
    }

    @Override
    public SiteSurveyDO getSiteSurvey(Long id) {
        // 保留旧入口已有的平台租户隔离只读行为；旧pms_project不冒充proj_project。
        return siteSurveyMapper.selectById(id);
    }

    @Override
    public PageResult<SiteSurveyDO> getSiteSurveyPage(SiteSurveyPageReqVO pageReqVO) {
        return siteSurveyMapper.selectPage(BeanUtils.toBean(pageReqVO, SiteSurveyPageQuery.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSiteSurvey(Long id, Integer expectedVersion) {
        updateStatus(id, expectedVersion, 0, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectSiteSurvey(Long id, Integer expectedVersion) {
        updateStatus(id, expectedVersion, 0, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveSiteSurvey(Long id, Integer expectedVersion) {
        updateStatus(id, expectedVersion, 1, 3);
    }

    private SiteSurveyDO lockWritableSurvey(Long id, Integer expectedVersion, Actor actor) {
        if (id == null || id <= 0) throw exception(BAD_REQUEST, "更新工勘必须提供有效主键");
        if (expectedVersion == null || expectedVersion < 0 || expectedVersion == Integer.MAX_VALUE) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
        SiteSurveyRowQuery query = new SiteSurveyRowQuery(actor.tenantId(), id);
        SiteSurveyDO located = siteSurveyMapper.selectByRow(query);
        if (located == null) throw exception(SITE_SURVEY_NOT_EXISTS);
        // 先项目范围锁，再原行锁，最后才允许AST写入。历史项目无真实MANAGE范围则拒绝写入。
        lockManageScope(located.getProjectId(), actor);
        SiteSurveyDO locked = siteSurveyMapper.selectForUpdate(query);
        if (locked == null) throw exception(SITE_SURVEY_NOT_EXISTS);
        if (!Objects.equals(locked.getId(), id) || !Objects.equals(locked.getTenantId(), actor.tenantId())
                || !Objects.equals(locked.getProjectId(), located.getProjectId())
                || !Objects.equals(locked.getCode(), located.getCode())
                || !Objects.equals(locked.getVersion(), expectedVersion)) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
        return locked;
    }

    private void lockManageScope(Long projectId, Actor actor) {
        if (projectId == null || projectId <= 0) throw exception(BAD_REQUEST, "项目编号无效");
        ProjectScopeResult current = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.userId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        requireScope(current, projectId);
        ProjectScopeResult locked = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                actor.tenantId(), actor.userId(), projectId, ProjectScopeApi.ACTION_MANAGE, current.treeVersion()));
        requireScope(locked, projectId);
        if (!Objects.equals(current.rootProjectId(), locked.rootProjectId())
                || !Objects.equals(current.treeVersion(), locked.treeVersion())) throw exception(FORBIDDEN);
    }

    private void requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.rootProjectId() == null || scope.treeVersion() == null
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(FORBIDDEN);
        }
    }

    private Actor currentActor() {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (tenantId == null || tenantId < 0 || userId == null || userId <= 0) throw exception(FORBIDDEN);
        return new Actor(tenantId, userId);
    }

    private void validateCodeUnique(Long projectId, String code) {
        if (siteSurveyMapper.selectByProjectIdAndCode(projectId, code) != null) {
            throw exception(SITE_SURVEY_CODE_DUPLICATE);
        }
    }

    private void validateStatus(SiteSurveyDO survey, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(survey.getStatus(), allowed)) return;
        }
        throw exception(SITE_SURVEY_STATUS_INVALID);
    }

    private void updateStatus(Long id, Integer expectedVersion, int oldStatus, int newStatus) {
        Actor actor = currentActor();
        SiteSurveyDO survey = lockWritableSurvey(id, expectedVersion, actor);
        validateStatus(survey, oldStatus);
        requireOne(siteSurveyMapper.updateStatusIfMatch(condition(survey, actor), newStatus));
    }

    private SiteSurveyWriteCondition condition(SiteSurveyDO survey, Actor actor) {
        return new SiteSurveyWriteCondition(actor.tenantId(), survey.getId(), survey.getProjectId(), survey.getCode(),
                survey.getVersion(), survey.getStatus(), String.valueOf(actor.userId()));
    }

    private void requireOne(int affectedRows) {
        if (affectedRows != 1) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
    }

    private void applyLocation(SiteSurveyDO survey, String fallbackLocation,
                               LocationMaintenanceCommand command, Integer sourceVersion) {
        survey.setLocation(fallbackLocation);
        if (command == null) {
            unresolvedLocation(survey, fallbackLocation);
            return;
        }
        EngineeringLocationFactService.LocationFact fact = locationFactService.maintain(survey.getProjectId(),
                "SITE_SURVEY", survey.getId(), sourceVersion, fallbackLocation, command);
        if (fact == null) throw exception(SITE_SURVEY_LOCATION_INVALID);
        if ("UNRESOLVED".equals(fact.resolutionStatus())) {
            unresolvedLocation(survey, fallbackLocation);
            return;
        }
        if (!"RESOLVED".equals(fact.resolutionStatus())) throw exception(SITE_SURVEY_LOCATION_INVALID);
        survey.setAddressId(fact.addressId());
        survey.setAddressVersion(fact.addressVersion());
        survey.setSiteId(fact.siteId());
        survey.setSiteVersion(fact.siteVersion());
        survey.setSiteLocationId(fact.siteLocationId());
        survey.setSiteLocationVersion(fact.siteLocationVersion());
        survey.setLocationResolutionStatus(fact.resolutionStatus());
        survey.setAddressSnapshot(fact.addressSnapshot());
        survey.setLocationSnapshot(fact.locationSnapshot());
    }

    private void unresolvedLocation(SiteSurveyDO survey, String fallbackLocation) {
        if (fallbackLocation == null || fallbackLocation.isBlank()) throw exception(SITE_SURVEY_LOCATION_REQUIRED);
        survey.setLocationResolutionStatus("UNRESOLVED");
        survey.setAddressId(null);
        survey.setAddressVersion(null);
        survey.setSiteId(null);
        survey.setSiteVersion(null);
        survey.setSiteLocationId(null);
        survey.setSiteLocationVersion(null);
        survey.setAddressSnapshot(null);
        survey.setLocationSnapshot(null);
    }

    private void copyLocation(SiteSurveyDO source, SiteSurveyDO target) {
        target.setLocation(source.getLocation());
        target.setAddressId(source.getAddressId());
        target.setAddressVersion(source.getAddressVersion());
        target.setSiteId(source.getSiteId());
        target.setSiteVersion(source.getSiteVersion());
        target.setSiteLocationId(source.getSiteLocationId());
        target.setSiteLocationVersion(source.getSiteLocationVersion());
        target.setLocationResolutionStatus(source.getLocationResolutionStatus());
        target.setAddressSnapshot(source.getAddressSnapshot());
        target.setLocationSnapshot(source.getLocationSnapshot());
    }

    private record Actor(Long tenantId, Long userId) { }
}
