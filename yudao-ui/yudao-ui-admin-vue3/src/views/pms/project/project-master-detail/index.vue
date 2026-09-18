<template>
  <div v-loading="loading">
    <!-- ============ 顶部项目身份卡（设计稿 phead-slim：单卡两行） ============ -->
    <ContentWrap>
      <div class="project-header">
        <div class="project-header-left">
          <div class="project-title-row">
            <ProjectStatusTag :project="detail" />
            <h2 class="project-name">{{ detail?.projectName || '未选择项目' }}</h2>
            <el-tag v-if="detail?.majorProjectLevel" size="small" type="warning">
              {{ getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, detail.majorProjectLevel) }}
            </el-tag>
            <el-tag v-if="detail?.projectCategory" size="small" effect="plain">
              {{ getDictLabel(DICT_TYPE.PMS_PROJECT_CATEGORY, detail.projectCategory) }}
            </el-tag>
            <el-tag v-if="detail?.lifecycleTemplateId" size="small" type="info" effect="plain">
              模板 #{{ detail.lifecycleTemplateId }} v{{ detail.lifecycleTemplateRevisionNo }}
            </el-tag>
          </div>
          <div class="project-meta-row">
            <span class="meta-item">
              编码 <span class="meta-code">{{ detail?.projectCode || '—' }}</span>
            </span>
            <span v-if="detail?.parentId" class="meta-item">
              父项目 <span class="meta-code">#{{ detail.parentId }}</span>
            </span>
            <span class="meta-item">
              <Icon icon="ep:office-building" />{{ detail?.customerName || '-' }}
            </span>
            <span v-if="primaryContact" class="meta-item">
              <Icon icon="ep:user" />主联系人 {{ primaryContact.name }}
            </span>
            <span class="meta-item">
              <Icon icon="ep:files" />
              {{ dimLabel(detail?.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD) }} /
              {{ dimLabel(detail?.implementationMode, DICT_TYPE.PMS_IMPLEMENTATION_METHOD) }}
            </span>
            <span class="meta-item"
              ><Icon icon="ep:calendar" />{{ formatDateTime(detail?.createTime) }}</span
            >
          </div>
        </div>
        <div class="project-header-right">
          <template v-if="instances">
            <span class="header-stat">进度 <b class="num">{{ overallTaskStats.progress }}%</b></span>
            <span class="header-stat"
              >任务 <b class="num">{{ overallTaskStats.done }}/{{ overallTaskStats.total }}</b></span
            >
          </template>
          <span v-if="riskCount !== undefined" class="header-stat"
            >风险 <b class="num">{{ riskCount }}</b></span
          >
          <el-button plain @click="goBack"><Icon icon="ep:back" />返回列表</el-button>
        </div>
      </div>
    </ContentWrap>

    <!-- ============ 阶段推进轨（节点 + 门禁菱形 + 超期红标） ============ -->
    <StageProgressRail
      :stages="instances?.stages || []"
      :gates="instances?.gates || []"
      @select="selectStage"
    />

    <!-- ============ 主体：左侧导航 + 右侧内容区 ============ -->
    <div class="detail-body">
      <!-- 左侧导航 -->
      <ContentWrap class="rail-wrap">
        <div class="rail-stage">
          <div class="rail-stage-title">项目概览</div>
          <button
            v-for="step in overviewSteps"
            :key="step.key"
            v-show="!step.permission || checkPermi(step.permission)"
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === step.key }"
            @click="switchTab(step.key)"
          >
            <Icon :icon="step.icon" class="rail-icon" />
            <span class="rail-label">{{ step.label }}</span>
            <el-tag v-if="step.pending" type="warning" size="small">待确认</el-tag>
          </button>
        </div>

        <!-- 业务中心四组（工程实施/方案计划/实施部署/验收闭环），配置与 /pms/project-detail 同口径 -->
        <div class="rail-stage">
          <div class="rail-stage-title">业务中心</div>
          <div v-for="group in businessGroups" :key="group.key" class="rail-group">
            <div class="rail-group-title">{{ group.title }}</div>
            <button
              v-for="step in group.flowSteps"
              :key="step.key"
              class="rail-item"
              :class="{ 'rail-item--active': activeTab === step.key }"
              @click="switchTab(step.key)"
            >
              <Icon :icon="step.icon" class="rail-icon" />
              <span class="rail-label">{{ step.label }}</span>
            </button>
          </div>
        </div>

        <div class="rail-stage">
          <div class="rail-stage-title">阶段和任务</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'stage-status' }"
            @click="switchTab('stage-status')"
          >
            <Icon icon="ep:grid" class="rail-icon" />
            <span class="rail-label">阶段状态</span>
          </button>
          <template v-for="stage in sortedStages" :key="stage.stageCode">
            <button
              class="rail-item stage-nav-item"
              :class="{
                'rail-item--active': activeTab === 'stage' && expandedStage === stage.stageCode
              }"
              @click="toggleStage(stage.stageCode)"
            >
              <Icon
                :icon="expandedStage === stage.stageCode ? 'ep:arrow-down' : 'ep:arrow-right'"
                class="stage-caret"
              />
              <i class="stage-dot" :class="stageDotClass(stage)"></i>
              <span class="rail-label">{{ stage.stageCode }} {{ stage.name }}</span>
              <span class="stage-prog">{{ stageProgress(stage) }}</span>
            </button>
            <div
              v-if="detail?.id && expandedStage === stage.stageCode"
              class="nav-task-tree"
              :data-testid="`project-nav-tasks-${stage.stageCode}`"
            >
              <ProjectTaskTree
                :project-id="detail.id"
                :stage-code="stage.stageCode"
                :refresh-token="stageTreeToken"
                compact
                @select="(task) => onTaskSelect(task, stage.stageCode)"
                @version="treeVersion = $event"
              />
            </div>
          </template>
        </div>

        <div class="rail-stage">
          <div class="rail-stage-title">交付准备</div>
          <button
class="rail-item" :class="{ 'rail-item--active': activeTab === 'customer-contacts' }"
            @click="switchTab('customer-contacts')" v-hasPermi="['pms:customer-contact:query']">
            <Icon icon="ep:phone" class="rail-icon" /><span class="rail-label">用户联系人</span>
          </button>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'duration' }"
            @click="switchTab('duration')"
            v-hasPermi="['pms:construction-plan:query', 'pms:construction-plan:duration-manage']"
          >
            <Icon icon="ep:calendar" class="rail-icon" />
            <span class="rail-label">项目工期</span>
          </button>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'preparation' }"
            @click="switchTab('preparation')"
            v-hasPermi="['pms:sol-site-survey:query']"
          >
            <Icon icon="ep:compass" class="rail-icon" />
            <span class="rail-label">工勘准备</span>
          </button>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'requirement-analysis' }"
            @click="switchTab('requirement-analysis')"
            v-hasPermi="['pms:requirement-analysis:query', 'pms:requirement-analysis:manage']"
          >
            <Icon icon="ep:edit-pen" class="rail-icon" />
            <span class="rail-label">需求分析</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">验收交维</div>
          <button
class="rail-item" :class="{ 'rail-item--active': activeTab === 'satisfaction' }"
            @click="switchTab('satisfaction')" v-hasPermi="['pms:acceptance:satisfaction:query']">
            <Icon icon="ep:chat-dot-round" class="rail-icon" />
            <span class="rail-label">满意度</span>
          </button>
          <button
class="rail-item" :class="{ 'rail-item--active': activeTab === 'acceptance-reports' }"
            @click="switchTab('acceptance-reports')" v-hasPermi="['pms:acceptance:report:query']">
            <Icon icon="ep:document-checked" class="rail-icon" />
            <span class="rail-label">验收报告</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">项目拆分</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'split' }"
            @click="switchTab('split')"
          >
            <Icon icon="ep:operation" class="rail-icon" />
            <span class="rail-label">拆分方案</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">进度汇总</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'progress' }"
            @click="switchTab('progress')"
          >
            <Icon icon="ep:data-analysis" class="rail-icon" />
            <span class="rail-label">进度汇总</span>
          </button>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'closure' }"
            @click="switchTab('closure')"
          >
            <Icon icon="ep:circle-check" class="rail-icon" />
            <span class="rail-label">闭环守卫</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">异常治理</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'governance' }"
            @click="switchTab('governance')"
            v-hasPermi="['pms:project:governance:query']"
          >
            <Icon icon="ep:warning-filled" class="rail-icon" />
            <span class="rail-label">异常治理</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">项目权限</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'authorization' }"
            @click="switchTab('authorization')"
            v-hasPermi="['pms:project:authorization:query']"
          >
            <Icon icon="ep:key" class="rail-icon" />
            <span class="rail-label">项目授权</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">服务经理</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'service-managers' }"
            @click="switchTab('service-managers')"
            v-hasPermi="['pms:project:assign']"
          >
            <Icon icon="ep:user-filled" class="rail-icon" />
            <span class="rail-label">责任分布</span>
          </button>
        </div>
      </ContentWrap>

      <!-- 右侧内容区 -->
      <div class="canvas">
        <!-- ============ 项目概览：基本信息 ============ -->
        <ContentWrap v-show="activeTab === 'base'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:document" /> 基本信息</span>
          </div>
          <el-descriptions v-if="detail" :column="descriptionColumns" border size="small">
            <el-descriptions-item label="项目编码">{{ detail.projectCode }}</el-descriptions-item>
            <el-descriptions-item label="编码命名空间">
              根 #{{ detail.codeRootId }} · 序号 {{ detail.projectSequence }} · 规则
              {{ detail.codeRuleVersion }}
            </el-descriptions-item>
            <el-descriptions-item label="项目名称" :span="2">{{
              detail.projectName
            }}</el-descriptions-item>
            <el-descriptions-item label="签约方式">
              <dict-tag :type="DICT_TYPE.PMS_SIGNING_METHOD" :value="detail.signingMethod ?? ''" />
            </el-descriptions-item>
            <el-descriptions-item label="项目类别">
              <dict-tag
                :type="DICT_TYPE.PMS_PROJECT_CATEGORY"
                :value="detail.projectCategory ?? ''"
              />
            </el-descriptions-item>
            <el-descriptions-item label="实施方式">
              <dict-tag
                :type="DICT_TYPE.PMS_IMPLEMENTATION_METHOD"
                :value="detail.implementationMode ?? ''"
              />
            </el-descriptions-item>
            <el-descriptions-item label="重大项目级别">
              <dict-tag
                v-if="detail.majorProjectLevel"
                :type="DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL"
                :value="detail.majorProjectLevel ?? ''"
              />
              <span v-else>不限</span>
            </el-descriptions-item>
            <el-descriptions-item label="业务层级">
              {{ detail.businessLevelName || detail.businessLevelCode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="结构深度">{{
              detail.treeDepth ?? '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="父项目"
              >#{{ detail.parentId ?? '-' }}</el-descriptions-item
            >
            <el-descriptions-item label="客户"
              >{{ detail.customerName || '-' }}（{{
                detail.customerCode || '-'
              }}）</el-descriptions-item
            >
            <el-descriptions-item label="合同号">{{
              detail.contractNo || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="实施地点">{{
              detail.implementationLocation || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <ProjectStatusTag :project="detail" />
            </el-descriptions-item>
            <el-descriptions-item label="创建来源">
              <dict-tag
                :type="DICT_TYPE.PMS_PROJECT_SOURCE_TYPE"
                :value="detail.sourceType ?? ''"
              />
            </el-descriptions-item>
            <el-descriptions-item label="创建原因" :span="2">{{
              detail.creationReason || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="2">{{
              formatDateTime(detail.createTime)
            }}</el-descriptions-item>
            <el-descriptions-item label="项目结束日期（工勘要求）" :span="2">{{ detail.projectEndDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目主联系人" :span="2">
              <template v-if="primaryContact">{{ primaryContact.name }} · {{ primaryContact.mobile || primaryContact.phone || primaryContact.email }}</template>
              <span v-else>暂未设置</span><el-tag v-if="primaryContactPending" type="warning">展示待刷新</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </ContentWrap>

        <!-- ============ 项目概览：客户信息（与 pms-inheritance/project-detail 同一组件） ============ -->
        <ProjectCustomerOverview
          v-if="detail?.id && activeTab === 'customer'"
          :project="detail"
          @changed="handleContactsChanged"
        />

        <!-- ============ 项目概览：设备清单（与 pms-inheritance/project-detail 同一配置驱动表格） ============ -->
        <div v-if="detail?.id && visitedTabs.has('equipment')" v-show="activeTab === 'equipment'" class="min-w-0" data-testid="project-pane-equipment">
          <DeliveryModuleTable :config="deliveryModules['equipment']" :project-id="detail.id" />
        </div>
        <!-- 序列号详情（ast_device 按项目过滤，同 pms-inheritance/project-detail） -->
        <div v-if="detail?.id && visitedTabs.has('sn-result')" v-show="activeTab === 'sn-result'" class="min-w-0" data-testid="project-pane-sn-result">
          <DeliveryModuleTable :config="deliveryModules['sn-result']" :project-id="detail.id" />
        </div>

        <!-- ============ 业务中心四组模块面板（配置驱动，行操作/新增/编辑/删除同 /pms/project-detail） ============ -->
        <template v-for="item in businessNavItems" :key="item.key">
          <div
            v-if="detail?.id && visitedTabs.has(item.key)"
            v-show="activeTab === item.key"
            class="min-w-0"
            :data-testid="`project-pane-${item.key}`"
          >
            <!-- 现场工勘直接复用既有工勘准备面板（与 preparation 页签同一界面） -->
            <ProjectSiteSurveyPanel
              v-if="item.key === 'site-survey'"
              :key="detail.id"
              :project-id="detail.id"
              @saved="loadDetail"
            />
            <!-- 需求分析：列表接新需求分析工作区数据源，新增/编辑接 EntityForm 表单（本地面板） -->
            <BusinessRequirementPanel
              v-else-if="item.key === 'requirement'"
              :key="detail.id"
              :project="detail"
            />
            <DeliveryModuleTable v-else :config="businessModuleConfigs[item.key]" :project-id="detail.id" />
          </div>
        </template>

        <!-- ============ 项目概览：实施范围（设计稿【待确认】占位结构） ============ -->
        <ContentWrap v-show="activeTab === 'scope'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:location" /> 实施范围</span>
            <el-tag type="warning" size="small">待确认</el-tag>
          </div>
          <el-empty description="实施范围数据结构与确认流程未在已读规格中定义" :image-size="80" />
          <p class="pending-hint">
            【待确认】实施范围的数据结构与确认流程以对应 Feature Spec 为准，本区为占位结构。
          </p>
        </ContentWrap>

        <ProjectAttributePanel
          v-if="detail?.id && activeTab === 'attributes'"
          :project="detail"
          @updated="handleAttributeUpdated"
        />
        <ProjectTemplateMatchHistoryPanel
          v-if="detail?.id && activeTab === 'match-history'"
          :key="historyRefreshKey"
          :project-id="detail.id"
        />

        <!-- ============ 项目概览：生命周期实例 ============ -->
        <ContentWrap v-show="activeTab === 'instances'">
          <div class="panel-header">
            <span class="panel-title"
              ><Icon icon="ep:tickets" /> 生命周期实例（阶段/任务/里程碑/交付件/门禁）</span
            >
          </div>
          <el-collapse v-if="instances">
            <el-collapse-item
              v-for="stage in instances.stages"
              :key="stage.stageCode"
              :name="stage.stageCode"
            >
              <template #title>
                <span class="stage-title">{{ stage.stageCode }} {{ stage.name }}</span>
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_STAGE_STATUS" :value="stage.status" />
              </template>
              <div class="preview-block">
                <div class="preview-block-title"
                  >任务（{{ instTasks(stage.stageCode).length }}）</div
                >
                <el-table :data="instTasks(stage.stageCode)" size="small" border>
                  <el-table-column prop="taskCode" label="任务码" width="120" />
                  <el-table-column prop="name" label="任务名称" min-width="140" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <dict-tag :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS" :value="row.status" />
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="preview-block">
                <div class="preview-block-title"
                  >里程碑（{{ instMilestones(stage.stageCode).length }}）</div
                >
                <div
                  v-for="m in instMilestones(stage.stageCode)"
                  :key="m.milestoneCode"
                  class="preview-line"
                >
                  <el-tag size="small" type="warning">{{ m.milestoneCode }}</el-tag> {{ m.name }}
                  <span class="text-12px text-gray-400">{{ m.timing }}</span>
                </div>
              </div>
              <div class="preview-block">
                <div class="preview-block-title"
                  >交付件（{{ instDeliverables(stage.stageCode).length }}）</div
                >
                <div
                  v-for="d in instDeliverables(stage.stageCode)"
                  :key="d.deliverableCode"
                  class="preview-line"
                >
                  <el-tag size="small" :type="d.required ? 'danger' : 'info'">{{
                    d.deliverableCode
                  }}</el-tag>
                  {{ d.name }}
                </div>
              </div>
              <div class="preview-block">
                <div class="preview-block-title"
                  >门禁（{{ instGates(stage.stageCode).length }}）</div
                >
                <div v-for="g in instGates(stage.stageCode)" :key="g.gateCode" class="preview-line">
                  <el-tag size="small" :type="g.gateType === 'ENTRY' ? 'success' : 'primary'">
                    {{ g.gateType === 'ENTRY' ? '准入' : '准出' }}
                  </el-tag>
                  {{ g.name }}
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-else description="暂无实例数据" />
        </ContentWrap>

        <!-- ============ 项目概览：团队成员 ============ -->
          <ProjectMembersPanel v-if="detail?.id && activeTab === 'members'" :project="detail" @updated="handleMembersUpdated" />
        <ContentWrap v-show="activeTab === 'members'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:user-filled" /> 成员责任历史</span>
          </div>
          <el-table v-if="members.length" :data="members" size="small" border>
            <el-table-column prop="memberName" label="姓名" width="100" />
            <el-table-column label="角色" width="130">
              <template #default="{ row }">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_MEMBER_ROLE" :value="row.memberRole" />
              </template>
            </el-table-column>
            <el-table-column label="生效时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.effectiveFrom) }}</template>
            </el-table-column>
            <el-table-column label="失效时间" width="160">
              <template #default="{ row }">
                <span v-if="row.effectiveTo">{{ formatDateTime(row.effectiveTo) }}</span>
                <el-tag v-else type="success" size="small">当前有效</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无成员" />
        </ContentWrap>

        <div v-if="detail?.id && visitedTabs.has('tasks')" v-show="activeTab === 'tasks'" class="min-w-0" data-testid="project-pane-tasks">
          <ProjectTaskPanel
            :project-id="detail.id"
            :project="detail"
            @tree-version="treeVersion = $event"
            @updated="loadAll"
          />
        </div>

        <!-- ============ 阶段和任务：阶段状态总览 ============ -->
        <div v-if="detail?.id && visitedTabs.has('stage-status')" v-show="activeTab === 'stage-status'" class="min-w-0" data-testid="project-pane-stage-gates">
          <ProjectStageStatusPanel
            :project-id="detail.id"
            :project="detail"
            :key="detail.id"
            @changed="loadAll"
          />
        </div>

        <!-- ============ 阶段和任务：阶段工作区（中栏执行内容 + 右栏门禁与阶段历史） ============ -->
        <div v-if="detail?.id && visitedTabs.has('stage')" v-show="activeTab === 'stage'" class="min-w-0" data-testid="project-pane-stage-workspace">
          <div class="stage-ws">
            <div class="stage-ws-main">
              <ProjectFlowPanel
                ref="flowRef"
                :project-id="detail.id"
                :project="detail"
                :selection="stageSelection"
                :show-stage-gates="false"
                show-responsibilities
                @changed="handleFlowChanged"
              />
            </div>
            <aside class="stage-ws-side" aria-label="门禁与阶段历史">
              <ContentWrap>
                <StageGateResultsPanel
                  :project-id="detail.id"
                  :stage-code="stageSelection?.stageCode || ''"
                  :project-version="detail.version"
                  @changed="loadAll"
                />
              </ContentWrap>
              <ContentWrap>
                <div class="panel-header">
                  <span class="panel-title"><Icon icon="ep:clock" /> 阶段历史</span>
                  <el-button link type="primary" @click="historyVisible = true"
                    >执行历史与规则结果</el-button
                  >
                </div>
                <div v-if="stageHistory.length" class="stage-history">
                  <div v-for="stage in stageHistory" :key="stage.stageCode" class="history-row">
                    <span class="history-date num">{{ formatDate(stage.actualStartTime) }}</span>
                    <span class="history-body">
                      进入 {{ stage.stageCode }} {{ stage.name }}
                      <dict-tag :type="DICT_TYPE.PMS_PROJECT_STAGE_STATUS" :value="stage.status" />
                    </span>
                  </div>
                </div>
                <el-empty v-else description="暂无已开始的阶段" :image-size="60" />
              </ContentWrap>
            </aside>
          </div>
          <ProjectExecutionHistory v-model="historyVisible" :project-id="detail.id" />
        </div>

        <div v-if="detail?.id && visitedTabs.has('customer-contacts')" v-show="activeTab === 'customer-contacts'" class="min-w-0" data-testid="project-pane-customer-contacts">
          <ProjectCustomerContacts :key="`contacts-${detail.id}`" :project-id="detail.id" @changed="handleContactsChanged" />
        </div>
        <div v-if="detail?.id && visitedTabs.has('duration')" v-show="activeTab === 'duration'" class="min-w-0" data-testid="project-pane-duration">
          <ProjectDurationPanel
            :project="detail"
          />
        </div>

        <div v-if="detail?.id && visitedTabs.has('preparation')" v-show="activeTab === 'preparation'" class="min-w-0" data-testid="project-pane-preparation">
          <ProjectSiteSurveyPanel
            :key="detail.id"
            :project-id="detail.id"
            @saved="loadDetail"
          />
        </div>

        <div v-if="detail?.id && visitedTabs.has('requirement-analysis')" v-show="activeTab === 'requirement-analysis'" class="min-w-0" data-testid="project-pane-requirement-analysis">
          <ProjectRequirementAnalysisPanel
            :project="detail"
          />
        </div>

        <div v-if="detail?.id && visitedTabs.has('satisfaction')" v-show="activeTab === 'satisfaction'" class="min-w-0" data-testid="project-pane-satisfaction">
          <SatisfactionWorkbench
            :key="`satisfaction-${detail.id}`"
            ref="satisfactionRef"
            :project-id="detail.id"
          />
        </div>

        <div v-if="detail?.id && visitedTabs.has('acceptance-reports')" v-show="activeTab === 'acceptance-reports'" class="min-w-0" data-testid="project-pane-acceptance-reports">
          <AcceptanceReportWorkbench
            :key="`acceptance-${detail.id}`"
            ref="acceptanceReportRef"
            :project-id="detail.id"
            :project-name="detail.projectName"
          />
        </div>

        <div v-if="detail?.id && visitedTabs.has('split')" v-show="activeTab === 'split'" class="min-w-0" data-testid="project-pane-split">
          <ProjectSplitWizard
            :project-id="detail.id"
            @applied="treeRefreshKey++"
          />
        </div>
        <div v-if="detail?.id && visitedTabs.has('tree')" v-show="activeTab === 'tree'" class="min-w-0" data-testid="project-pane-tree">
          <ProjectTreePanel
            :key="treeRefreshKey"
            :project-id="detail.id"
            @tree-version="treeVersion = $event"
            @updated="loadAll"
          />
        </div>
        <div v-if="detail?.id && visitedTabs.has('progress')" v-show="activeTab === 'progress'" class="min-w-0" data-testid="project-pane-progress">
          <ProjectProgressPanel
            :project-id="detail.id"
            :tree-version="treeVersion"
          />
        </div>
        <div v-if="detail?.id && visitedTabs.has('closure')" v-show="activeTab === 'closure'" class="min-w-0" data-testid="project-pane-closure">
          <ProjectNormalClosurePanel
            :project-id="detail.id"
            @updated="loadAll"
          />
          <ProjectClosureGuardPanel
            :project-id="detail.id"
            :project-name="detail.projectName"
            :tree-version="treeVersion"
          />
        </div>
        <div v-if="detail?.id && visitedTabs.has('authorization')" v-show="activeTab === 'authorization'" class="min-w-0" data-testid="project-pane-authorization">
          <ProjectAuthorizationPanel
            :project-id="detail.id"
          />
        </div>
        <div v-if="detail?.id && visitedTabs.has('governance')" v-show="activeTab === 'governance'" class="min-w-0" data-testid="project-pane-governance">
          <ProjectGovernancePanel
            :project="detail"
            @updated="loadAll"
          />
        </div>
        <div v-if="detail?.id && visitedTabs.has('service-managers')" v-show="activeTab === 'service-managers'" class="min-w-0" data-testid="project-pane-service-managers">
          <ProjectServiceManagerPanel
            :project-id="detail.id"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import * as RiskApi from '@/api/pms/project/project-risk'
import ProjectSplitWizard from './components/ProjectSplitWizard.vue'
import ProjectTreePanel from '@/views/pms/project/inheritance/tree/ProjectTreePanel.vue'
import ProjectProgressPanel from './components/ProjectProgressPanel.vue'
import ProjectClosureGuardPanel from './components/ProjectClosureGuardPanel.vue'
import ProjectNormalClosurePanel from './components/ProjectNormalClosurePanel.vue'
import ProjectStatusTag from '../projects/ProjectStatusTag.vue'
import ProjectAuthorizationPanel from './components/ProjectAuthorizationPanel.vue'
import ProjectGovernancePanel from './components/ProjectGovernancePanel.vue'
import ProjectServiceManagerPanel from './components/ProjectServiceManagerPanel.vue'
import ProjectMembersPanel from '@/views/pms/project/inheritance/members/ProjectMembersPanel.vue'
import DeliveryModuleTable, { type DeliveryModuleConfig } from '@/views/pms/project/inheritance/detail/DeliveryModuleTable.vue'
import ProjectCustomerOverview from './components/ProjectCustomerOverview.vue'
import ProjectAttributePanel from './components/ProjectAttributePanel.vue'
import ProjectTemplateMatchHistoryPanel from './components/ProjectTemplateMatchHistoryPanel.vue'
import ProjectTaskPanel from './components/ProjectTaskPanel.vue'
import ProjectStageStatusPanel from './components/ProjectStageStatusPanel.vue'
import ProjectDurationPanel from './components/ProjectDurationPanel.vue'
import StageProgressRail from './components/StageProgressRail.vue'
import ProjectFlowPanel from './components/ProjectFlowPanel.vue'
import StageGateResultsPanel from './components/StageGateResultsPanel.vue'
import ProjectTaskTree from './components/ProjectTaskTree.vue'
import ProjectExecutionHistory from './components/ProjectExecutionHistory.vue'
import { businessGroups, businessNavItems, businessModuleConfigs } from './components/businessModules'
import type { ProjectFlowSelection } from './components/project-flow'
import type { TaskNode } from '@/api/pms/project/task-workbench'
import ProjectSiteSurveyPanel from '@/views/pms/delivery-business/site-survey/index.vue'
import BusinessRequirementPanel from './components/BusinessRequirementPanel.vue'
import ProjectCustomerContacts from '@/views/pms/customer/contacts/index.vue'
import SatisfactionWorkbench from '@/views/pms/acceptance/satisfaction/index.vue'
import AcceptanceReportWorkbench from '@/views/pms/acceptance/acceptance-report/index.vue'
import * as ContactsApi from '@/api/pms/customer/contacts'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import { getDeliveryScopePage } from '@/api/pms/commerce'
import { checkPermi } from '@/utils/permission'
import ProjectRequirementAnalysisPanel from '@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue'
import type {
  ProjectMasterVO,
  ProjectInstancesVO,
  ProjectMemberAssignmentVO
} from '@/api/pms/project/projects'

defineOptions({ name: 'PmsProjectMasterDetail' })

const route = useRoute()
const router = useRouter()
const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))

const loading = ref(false)
const detail = ref<ProjectMasterVO | null>(null)
const instances = ref<ProjectInstancesVO | null>(null)
const members = ref<ProjectMemberAssignmentVO[]>([])
// 身份卡右侧进度/任务/风险统计（设计稿 hstat）；进度与任务由新链实例视图计算，风险走 /pms/project-risk
// 注意：/pms/project-panoramic 面向旧链 pms_project，ID 空间与本页主数据不同，不能混用
const riskCount = ref<number>()
const treeVersion = ref<number>()
const treeRefreshKey = ref(0)
const historyRefreshKey = ref(0)
const satisfactionRef = ref<InstanceType<typeof SatisfactionWorkbench>>()
const acceptanceReportRef = ref<InstanceType<typeof AcceptanceReportWorkbench>>()

// 旧链接 tab=stage-gates 收敛到阶段状态页签；业务中心页签 key 取自 businessModules 分组定义
const businessTabKeys = businessNavItems.map((item) => item.key)
const requestedTab = (
  {
    'stage-gates': 'stage-status'
  } as Record<string, string>
)[String(route.query.tab)] ||
  ([
    'tasks',
    'stage-status',
    'stage',
    'customer',
    'equipment',
    'sn-result',
    'scope',
    'duration',
    'preparation',
    'customer-contacts',
    'requirement-analysis',
    'satisfaction',
    'acceptance-reports',
    'closure'
  ].includes(String(route.query.tab)) || businessTabKeys.includes(String(route.query.tab))
    ? String(route.query.tab)
    : 'base')
const activeTab = ref(requestedTab)
const visitedTabs = ref(new Set([requestedTab]))

// 项目概览子项（设计稿 F-PROJ-007 §6 六类内容 + 既有扩展）；pending 项为规格未定义的占位卡片
// permission 与 pms-inheritance/project-detail 的 overviewSteps 同口径，无权限时导航项隐藏
const overviewSteps: { key: string; label: string; icon: string; pending?: boolean; permission?: string[] }[] = [
  { key: 'base', label: '基本信息', icon: 'ep:document' },
  { key: 'customer', label: '客户信息', icon: 'ep:office-building' },
  { key: 'tree', label: '项目树', icon: 'ep:share' },
  { key: 'members', label: '项目成员', icon: 'ep:user-filled' },
  { key: 'tasks', label: '项目任务', icon: 'ep:list' },
  { key: 'equipment', label: '设备清单', icon: 'ep:cpu', permission: ['pms:commerce:scope:query'] },
  { key: 'sn-result', label: '序列号详情', icon: 'ep:cpu', permission: ['pms:device:query'] },
  { key: 'scope', label: '实施范围', icon: 'ep:location', pending: true },
  { key: 'attributes', label: '属性判定', icon: 'ep:edit' },
  { key: 'match-history', label: '匹配历史', icon: 'ep:clock' },
  { key: 'instances', label: '生命周期实例', icon: 'ep:tickets' }
]

const dimLabel = (value?: string | null, dict?: DICT_TYPE) =>
  value ? getDictLabel(dict!, value) : '不限'
const formatDateTime = (v?: any) => (v ? formatDate(v) : '-')

// ============ 交付模块表格（与 pms-inheritance/project-detail 同一 DeliveryModuleTable，配置同口径） ============
// 设备清单 = 订单信息（交付范围明细展平，无独立明细 API，内存分页）；序列号详情 = ast_device 按项目过滤
const deliveryModules: Record<string, DeliveryModuleConfig> = {
  equipment: {
    label: '设备清单',
    icon: 'ep:cpu',
    load: async (pid, pageNo, pageSize) => {
      const res = await getDeliveryScopePage({ projectId: pid, pageNo: 1, pageSize: 200, includeHistory: false })
      const rows = (res.list || []).flatMap((scope: any) => {
        const base = { orderNo: scope.orderNo, lineNo: scope.lineNo, itemCode: scope.itemCode }
        const details = scope.details || []
        if (!details.length) {
          return [{ ...base, productCode: '', deviceTypeCode: '', allocatedQuantity: scope.allocatedQuantity, status: scope.scopeStatus }]
        }
        return details.map((d: any) => ({
          ...base,
          productCode: d.productCode || '',
          deviceTypeCode: d.deviceTypeCode || '',
          allocatedQuantity: d.allocatedQuantity,
          status: d.status || scope.scopeStatus
        }))
      })
      return { list: rows.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: rows.length }
    },
    columns: [
      { prop: 'orderNo', label: '订单号', minWidth: 160 },
      { prop: 'lineNo', label: '行号', width: 140 },
      { prop: 'itemCode', label: '物料编码', minWidth: 150 },
      { prop: 'productCode', label: '产品编码', minWidth: 140 },
      { prop: 'deviceTypeCode', label: '设备类型', width: 120 },
      { prop: 'allocatedQuantity', label: '数量', width: 90 },
      { prop: 'status', label: '状态', width: 110 }
    ]
  },
  'sn-result': {
    label: '序列号详情',
    icon: 'ep:cpu',
    load: (pid, pageNo, pageSize) => DeviceArchiveApi.getDeviceArchivePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => DeviceArchiveApi.createDeviceArchive(data),
    update: (data) => DeviceArchiveApi.updateDeviceArchive(data.id, data),
    delete: (id) => DeviceArchiveApi.deleteDeviceArchive(id),
    columns: [
      { prop: 'sn', label: '序列号', width: 150 },
      { prop: 'name', label: '设备名称', minWidth: 150 },
      { prop: 'productModel', label: '产品型号', width: 120 },
      { prop: 'locationSnapshot', label: '位置快照', minWidth: 140 },
      { prop: 'warrantyStartDate', label: '维保开始', width: 110, type: 'time' },
      { prop: 'warrantyEndDate', label: '维保结束', width: 110, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    // 状态口径：ast_device String 值域（pms_device_status 字典）
    statusMap: {
      IN_STOCK: { label: '在库', tone: 'gray' },
      IN_USE: { label: '在用', tone: 'blue' },
      FAULT: { label: '故障', tone: 'yellow' },
      REPAIRING: { label: '维修中', tone: 'yellow' },
      RETIRED: { label: '已报废', tone: 'red' }
    }
  }
}

const switchTab = async (key: string) => {
  if (key !== activeTab.value && satisfactionRef.value?.requestLeave() === false) return
  if (key !== activeTab.value && (await acceptanceReportRef.value?.requestLeave()) === false) return
  if (key !== activeTab.value && (await flowRef.value?.requestLeave()) === false) return
  activeTab.value = key
  visitedTabs.value = new Set([...visitedTabs.value, key])
}

const primaryContact = ref<ContactsApi.ContactVO>()
const primaryContactPending = ref(false)
const loadPrimaryContact = async () => {
  const id = Number(route.query.projectId)
  if (!id || !checkPermi(['pms:customer-contact:query'])) return
  try {
    const page = await ContactsApi.getProjectPage(id, { pageNo: 1, pageSize: 1, status: 0 })
    primaryContact.value = page.list.find(contact => contact.primaryFlag)
    primaryContactPending.value = false
  } catch { primaryContactPending.value = true }
}
const handleContactsChanged = async () => { await Promise.all([loadDetail(), loadPrimaryContact()]) }

// ============ 实例视图 ============
const instTasks = (code: string) => instances.value?.tasks.filter((t) => t.stageCode === code) || []
// 全项目任务完成统计（身份卡 hstat，与阶段进度同一口径：status === DONE）
const overallTaskStats = computed(() => {
  const tasks = instances.value?.tasks || []
  const done = tasks.filter((task) => task.status === 'DONE').length
  return { done, total: tasks.length, progress: tasks.length ? Math.round((done / tasks.length) * 100) : 0 }
})
const instMilestones = (code: string) =>
  instances.value?.milestones.filter((m) => m.stageCode === code) || []
const instDeliverables = (code: string) =>
  instances.value?.deliverables.filter((d) => d.stageCode === code) || []
const instGates = (code: string) => instances.value?.gates.filter((g) => g.stageCode === code) || []

// ============ 阶段导航与阶段工作区 ============
type StageInstance = ProjectInstancesVO['stages'][number]
const sortedStages = computed(() =>
  [...(instances.value?.stages || [])].sort((a, b) => a.sortOrder - b.sortOrder)
)
const expandedStage = ref('')
const stageTreeToken = ref(0)
const stageSelection = ref<ProjectFlowSelection>()
const flowRef = ref<InstanceType<typeof ProjectFlowPanel>>()
const historyVisible = ref(false)

const stageDotClass = (stage: StageInstance) =>
  stage.status === 'DONE'
    ? 'done'
    : stage.status === 'ACTIVE'
      ? 'cur'
      : stage.status === 'TERMINATED'
        ? 'terminated'
        : ''

const stageProgress = (stage: StageInstance) => {
  const tasks = instTasks(stage.stageCode)
  if (!tasks.length) return '—'
  const done = tasks.filter((task) => task.status === 'DONE').length
  return `${Math.round((done / tasks.length) * 100)}%`
}

const stageHistory = computed(() =>
  sortedStages.value
    .filter((stage) => stage.actualStartTime)
    .sort((a, b) => (a.actualStartTime! < b.actualStartTime! ? 1 : -1))
)

const ensureStageSelection = () => {
  if (stageSelection.value?.stageCode && sortedStages.value.some((stage) => stage.stageCode === stageSelection.value?.stageCode)) return
  const current = sortedStages.value.find((stage) => stage.status === 'ACTIVE')
  const target = current || sortedStages.value[0]
  if (target) stageSelection.value = { kind: 'stage', stageCode: target.stageCode }
}

const selectStage = async (stageCode: string) => {
  expandedStage.value = stageCode
  stageSelection.value = { kind: 'stage', stageCode }
  await switchTab('stage')
}

const toggleStage = async (stageCode: string) => {
  const selection = stageSelection.value
  const alreadySelected =
    activeTab.value === 'stage' &&
    expandedStage.value === stageCode &&
    selection?.kind === 'stage' &&
    selection.stageCode === stageCode
  if (alreadySelected) {
    expandedStage.value = ''
    return
  }
  await selectStage(stageCode)
}

const onTaskSelect = async (task: TaskNode, stageCode: string) => {
  if (task.placeholder || !task.stageCode) return
  stageSelection.value = { kind: 'task', stageCode, taskId: task.taskId }
  await switchTab('stage')
}

const handleFlowChanged = async () => {
  stageTreeToken.value++
  await loadAll()
}

// ============ 数据加载 ============
const loadDetail = async () => {
  const id = Number(route.query.projectId)
  if (!id) return
  detail.value = await ProjectsApi.getProject(id)
}
const handleAttributeUpdated = async () => {
  await loadDetail()
  historyRefreshKey.value++
}
const handleMembersUpdated = async () => {
  await Promise.all([loadDetail(), loadMembers(), loadInstances()])
}
const loadInstances = async () => {
  const id = Number(route.query.projectId)
  if (!id) return
  instances.value = await ProjectsApi.getProjectInstances(id)
}
const loadMembers = async () => {
  const id = Number(route.query.projectId)
  if (!id) return
  members.value = (await ProjectsApi.getProjectMembers(id)) || []
}
const loadRiskCount = async () => {
  const id = Number(route.query.projectId)
  if (!id || !checkPermi(['pms:project-risk:query'])) return
  try {
    riskCount.value = ((await RiskApi.getProjectRiskListByProjectId(id)) || []).length
  } catch {
    riskCount.value = undefined
  }
}
const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadDetail(), loadInstances(), loadMembers(), loadPrimaryContact(), loadRiskCount()])
    stageTreeToken.value++
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/pms/project-management/projects')

onMounted(() => {
  loadAll()
  ensureStageSelection()
})
</script>

<style lang="scss" scoped>
/* 顶部档案区 */
.project-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.project-header-left {
  flex: 1;
  min-width: 0;
}

.project-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.project-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.project-meta-row {
  display: flex;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 设计稿 hs-sub：分隔线竖排元信息 */
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 10px;
  border-left: 1px solid var(--el-border-color-lighter);
  line-height: 1.6;

  &:first-child {
    padding-left: 0;
    border-left: none;
  }
}

.meta-code {
  padding: 1px 8px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.project-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 设计稿 hstat：进度/任务/风险统计 */
.header-stat {
  padding: 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-left: 1px solid var(--el-border-color-lighter);
  line-height: 1.6;

  &:first-child {
    padding-left: 0;
    border-left: none;
  }

  b {
    margin-left: 2px;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

/* 主体布局 */
.detail-body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 12px;
}

.canvas {
  flex: 1 1 auto;
  min-width: 0;
}

.rail-wrap {
  flex: 0 0 280px;

  :deep(.el-card__body) {
    padding: 8px 6px;
  }
}

.rail-stage {
  margin-bottom: 14px;
}

/* 业务中心分组（口径同 /pms/project-detail rail-group） */
.rail-group {
  margin-bottom: 8px;

  &:last-child {
    margin-bottom: 0;
  }
}

.rail-group-title {
  padding: 4px 10px 2px;
  margin-left: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  border-left: 2px solid var(--el-border-color);
}

.rail-stage-title {
  padding: 6px 10px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.rail-item {
  display: flex;
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 4px;
  transition: all 0.15s ease;
  align-items: center;
  gap: 8px;

  &:hover {
    background: var(--el-color-primary-light-9);
  }

  &--active {
    font-weight: 600;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.rail-icon {
  font-size: 15px;
  flex-shrink: 0;
}

.rail-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 阶段导航行 */
.stage-nav-item {
  padding-left: 10px;
}

.stage-dot {
  width: 8px;
  height: 8px;
  flex: none;
  background: var(--el-border-color);
  border: 1px solid var(--el-border-color-darker);
  border-radius: 50%;

  &.done {
    background: var(--el-color-success);
    border-color: var(--el-color-success);
  }

  &.cur {
    background: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }

  &.terminated {
    background: var(--el-color-info);
    border-color: var(--el-color-info);
  }
}

.stage-prog {
  flex: none;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.stage-caret {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.nav-task-tree {
  max-height: 320px;
  margin: 0 4px 6px;
  padding-left: 14px;
  overflow-y: auto;
  border-left: 2px solid var(--el-border-color-lighter);
  margin-left: 18px;
}

/* 阶段工作区：中栏执行内容 + 右栏门禁与阶段历史 */
.stage-ws {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px;
  align-items: start;
}

.stage-ws-main {
  min-width: 0;
}

.stage-ws-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.stage-history {
  font-size: 12.5px;
}

.history-row {
  display: flex;
  gap: 10px;
  padding: 8px 2px;
  border-bottom: 1px dashed var(--el-border-color-lighter);

  &:last-child {
    border-bottom: none;
  }
}

.history-date {
  flex: none;
  width: 80px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.history-body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  color: var(--el-text-color-primary);
}

.pending-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.panel-header {
  display: flex;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: space-between;
}

.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stage-title {
  margin-right: 8px;
  font-weight: 600;
}

.preview-block {
  margin-bottom: 10px;
}

.preview-block-title {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.preview-line {
  padding: 2px 0;
  font-size: 13px;
}

@media (width <= 1280px) {
  .rail-wrap {
    flex-basis: 240px;
  }

  .stage-ws {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (width <= 1024px) {
  .canvas {
    width: 100%;
  }

  .detail-body {
    flex-direction: column;
  }

  .rail-wrap {
    width: 100%;
    flex: 1 1 auto;

    :deep(.el-card__body) {
      display: flex;
      gap: 6px;
      overflow-x: auto;
    }
  }

  .rail-stage {
    display: flex;
    flex: 0 0 auto;
    margin-bottom: 0;
  }

  .rail-stage-title {
    display: none;
  }

  .rail-item {
    width: auto;
    white-space: nowrap;
  }

  .nav-task-tree {
    display: none;
  }
}

@media (width <= 767px) {
  .project-header-right,
  .project-header-right .el-button {
    width: 100%;
  }

  .project-meta-row {
    display: grid;
    gap: 6px;
  }

  .meta-item {
    padding-left: 0;
    border-left: none;
  }

  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .canvas {
    width: 100%;
    overflow: hidden;
  }
}
</style>
