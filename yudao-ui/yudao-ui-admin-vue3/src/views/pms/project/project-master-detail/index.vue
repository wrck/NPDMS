<template>
  <div v-loading="loading">
    <!-- ============ 顶部项目身份卡（设计稿 phead-slim：单卡两行） ============ -->
    <ContentWrap>
      <div class="project-header">
        <div class="project-header-left">
          <div class="project-title-row">
            <ProjectStatusTag :project="detail" />
            <h2 class="project-name">{{ detail?.projectName || '未选择项目' }}</h2>
            <el-tag v-if="majorProjectLevelLabel" size="small" type="warning">
              {{ majorProjectLevelLabel }}
            </el-tag>
            <el-tag v-if="detail?.projectCategory" size="small" effect="plain">
              {{ getDictLabel(DICT_TYPE.PMS_PROJECT_CATEGORY, detail.projectCategory) }}
            </el-tag>
            <ProjectCurrentPlanTag v-if="detail?.id && checkPermi(['pms:project-plan:manage'])"
              :project-id="detail.id" :refresh-token="detail.version" />
          </div>
          <div class="project-meta-row">
            <span class="meta-item">
              编码 <span class="meta-code">{{ detail?.projectCode || '—' }}</span>
            </span>
            <span v-if="detail?.parentId" class="meta-item">
              父项目
              <router-link v-if="parentProject?.projectName" class="parent-project-link" :to="{ path: '/pms/project-management/project-master-detail', query: { projectId: String(detail.parentId) } }">{{ parentProject.projectName }}</router-link>
              <span v-else>{{ parentProjectLoading ? '加载中' : '信息不可见' }}</span>
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

    <ContentWrap v-if="narrowScreen && detail?.id" class="mobile-task-navigation">
      <details ref="mobileTaskNavigation">
        <summary>选择任务<span v-if="stageSelection"> · {{ stageSelection.stageCode }}</span></summary>
        <div class="mobile-stage-options" aria-label="选择任务所属阶段">
          <el-button v-for="stage in sortedStages" :key="stage.stageCode"
            :type="expandedStage === stage.stageCode ? 'primary' : 'default'"
            :aria-pressed="expandedStage === stage.stageCode" @click="selectStage(stage.stageCode)">
            {{ stage.stageCode }} {{ stage.name }}
          </el-button>
        </div>
        <ProjectTaskTree v-if="expandedStage" :project-id="detail.id" :stage-code="expandedStage"
          :refresh-token="stageTreeToken" compact @select="onMobileTaskSelect" @version="treeVersion = $event" />
        <p v-else>请先选择阶段，再选择任务。</p>
      </details>
    </ContentWrap>

    <!-- ============ 主体：左侧导航 + 右侧内容区 ============ -->
    <div class="detail-body">
      <!-- 左侧导航：限高吸顶，内部独立滚动（导航过长不撑高整页），悬停显示细滚动条 -->
      <ContentWrap class="rail-wrap" :body-style="{ padding: '8px 6px', overflowY: 'auto' }">
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
                v-if="stageHasTasks(stage)"
                :icon="expandedStage === stage.stageCode ? 'ep:arrow-down' : 'ep:arrow-right'"
                class="stage-caret"
              />
              <i class="stage-dot" :class="stageDotClass(stage)"></i>
              <span class="rail-label">{{ stage.stageCode }} {{ stage.name }}</span>
              <span class="stage-prog">{{ stageProgress(stage) }}</span>
            </button>
            <div
              v-if="detail?.id && expandedStage === stage.stageCode && stageHasTasks(stage)"
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
          <div class="rail-stage-title">全流程业务</div>
          <BusinessFlowNav :groups="businessFlowGroups" :active="activeTab" @select="switchTab" />
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
          <ProjectBaseInfo v-if="detail?.id" :project="detail" />
          <details v-if="detail?.lifecycleTemplateId && checkRole(['admin'])" class="template-source-history">
            <summary>历史信息</summary>
            <p>原始模板来源：#{{ detail.lifecycleTemplateId }} · 模板 v{{ detail.lifecycleTemplateRevisionNo }}</p>
            <p>此处保留项目最初绑定的模板来源；当前实际执行配置以顶部的生效计划为准。</p>
          </details>
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
        <!-- 序列号详情（DemoV2 1.1.1 十八列明细：设备列表 + 档案位置关联 + 在网版本/维保逐台补齐） -->
        <div v-if="detail?.id && visitedTabs.has('sn-result')" v-show="activeTab === 'sn-result'" class="min-w-0" data-testid="project-pane-sn-result">
          <ProjectSerialPanel :project-id="detail.id" :active="activeTab === 'sn-result'" @open-config-log="openConfigLogForDeviceId" />
        </div>

        <!-- ============ 全流程业务模块面板（配置驱动，行操作/新增/编辑/删除同通用模块面板） ============ -->
        <template v-for="item in flowModuleItems" :key="item.key">
          <div
            v-if="detail?.id && visitedTabs.has(item.key)"
            v-show="activeTab === item.key"
            class="min-w-0"
            :data-testid="`project-pane-${item.key}`"
          >
            <ProjectDeliverablesPanel v-if="item.key === 'deliverable-checklist'" :project-id="detail.id" @changed="loadInstances" />
            <DeliveryModuleTable v-else :config="businessModuleConfigs[item.key]" :project-id="detail.id" />
          </div>
        </template>

        <!-- ============ 全流程业务内嵌工作台（Demo 操作界面：方案九章/物料选择/到货/安装/配置/联调/割接） ============ -->
        <template v-for="(comp, embedKey) in flowEmbedComponents" :key="embedKey">
          <div
            v-if="detail?.id && visitedTabs.has(embedKey)"
            v-show="activeTab === embedKey"
            class="min-w-0"
            :data-testid="`project-pane-${embedKey}`"
          >
            <component
              :is="comp"
              :key="`embed-${embedKey}-${detail.id}`"
              :project-id="detail.id"
              v-bind="embedKey === 'config-log' ? { active: activeTab === embedKey } : {}"
            />
          </div>
        </template>

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
                  <el-tag size="small" :type="d.status === 'ACCEPTED' ? 'success' : 'info'">{{ d.status === 'ACCEPTED' ? '已满足' : '待满足' }}</el-tag>
                  <el-button v-if="d.id" link type="primary" @click="deliverableRef?.open(d.id)">提交与查看</el-button>
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
            :refresh-token="stageTreeToken"
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
                :instances="instances"
                :selection="stageSelection"
                :show-stage-gates="false"
                show-responsibilities
                @changed="handleFlowChanged"
              />
            </div>
            <aside class="stage-ws-side" aria-label="完成条件与阶段历史">
              <ContentWrap>
                <!-- 侧栏按「完成条件」口径展示：准入/准出/交付件/审批分节；选中任务时由面板追加当前任务交付件 -->
                <StageGateResultsPanel
                  ref="stageGateRef"
                  title="完成条件"
                  :project-id="detail.id"
                  :stage-code="stageSelection?.stageCode || ''"
                  :project-version="detail.version"
                  :instances="instances"
                  :task-deliverables="stageSelection?.kind === 'task' ? flowTaskDeliverables : undefined"
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

        <div v-if="detail?.id && visitedTabs.has('preparation')" v-show="activeTab === 'preparation'" class="min-w-0" data-testid="project-pane-preparation">
          <ProjectSiteSurveyPanel
            :key="detail.id"
            :project-id="detail.id"
            @saved="loadDetail"
          />
        </div>

        <!-- 施工计划整合工作台（3.1 项目工期 + 3.2 阶段施工计划 + 3.4 工期倒排计算视图 + 冻结历史） -->
        <div v-if="detail?.id && visitedTabs.has('schedule')" v-show="activeTab === 'schedule'" class="min-w-0" data-testid="project-pane-schedule">
          <ProjectSchedulePanel ref="scheduleRef" :project="detail" @open-task="onTaskSelect" @changed="handleScheduleChanged" />
        </div>

        <div v-if="detail?.id && visitedTabs.has('requirement-analysis')" v-show="activeTab === 'requirement-analysis'" class="min-w-0" data-testid="project-pane-requirement-analysis">
          <ProjectRequirementAnalysisPanel
            ref="requirementRef"
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
            :refresh-token="treeRefreshKey"
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
        <!-- 交付件汇总（6.4/ACC-04，六类来源视图）：嵌入汇总面板并按当前项目自动加载 -->
        <div v-if="detail?.id && visitedTabs.has('deliverable-summary')" v-show="activeTab === 'deliverable-summary'" class="min-w-0" data-testid="project-pane-deliverable-summary">
          <DeliverableSummaryPanel :key="`deliverable-summary-${detail.id}`" :project-id="detail.id" />
        </div>

        <!-- 现场培训（6.1/ACC-01）：内嵌完整工作台（新建/编辑/生成培训记录表/外发/作废） -->
        <div v-if="detail?.id && visitedTabs.has('training')" v-show="activeTab === 'training'" class="min-w-0" data-testid="project-pane-training">
          <TrainingWorkbench :key="`training-${detail.id}`" :project-id="detail.id" :readonly="detail.lifecycleStatus !== 'ACTIVE'" />
        </div>


      </div>
    </div>
  </div>
  <ProjectDeliverableDialog v-if="detail?.id" ref="deliverableRef" :project-id="detail.id" @changed="loadInstances" />
  <ProjectApprovalDrawer ref="approvalDrawerRef" />
  <ProjectDeviceConfigLogDrawer v-if="detail?.id" ref="configLogDrawerRef" :project-id="detail.id" />
</template>

<script setup lang="ts">
import ProjectBaseInfo from './components/ProjectBaseInfo.vue'
import ProjectCurrentPlanTag from './components/ProjectCurrentPlanTag.vue'
import BusinessFlowNav from './components/BusinessFlowNav.vue'
import { computed, onMounted, provide, ref, type Component } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import ProjectApprovalDrawer from './components/ProjectApprovalDrawer.vue'
import { projectApprovalDetailKey } from './components/projectApprovalDetail'
import { useRoute, useRouter } from 'vue-router'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import * as RiskApi from '@/api/pms/project/project-risk'
import ProjectSplitWizard from './components/ProjectSplitPanel.vue'
import ProjectTreePanel from '@/views/pms/project/inheritance/tree/ProjectTreePanel.vue'
import ProjectProgressPanel from './components/ProjectProgressSummaryPanel.vue'
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
import ProjectSchedulePanel from './components/ProjectSchedulePanel.vue'
import ProjectSerialPanel from './components/ProjectSerialPanel.vue'
import ProjectDeviceConfigLogDrawer from './components/ProjectDeviceConfigLogDrawer.vue'
import StageProgressRail from './components/StageProgressRail.vue'
import ProjectFlowPanel from './components/ProjectFlowPanel.vue'
import ProjectDeliverableDialog from './components/ProjectDeliverableDialog.vue'
import ProjectDeliverablesPanel from './components/ProjectDeliverablesPanel.vue'
import StageGateResultsPanel from './components/StageGateResultsPanel.vue'
import ProjectTaskTree from './components/ProjectTaskTree.vue'
import ProjectExecutionHistory from './components/ProjectExecutionHistory.vue'
import { businessFlowGroups, businessNavItems, businessModuleConfigs, flowEmbedKeys } from './components/businessModules'
import type { ProjectFlowSelection } from './components/project-flow'
import type { TaskNode } from '@/api/pms/project/task-workbench'
import ProjectSiteSurveyPanel from '@/views/pms/delivery-business/site-survey/index.vue'
import DeliverableSummaryPanel from '@/views/pms/engineering/deliverable/summary/index.vue'
import TrainingWorkbench from '@/views/pms/engineering/training/index.vue'
import ArrivalAcceptanceWorkbench from './components/ProjectArrivalReceiptPanel.vue'
import InstallationWorkbench from '@/views/pms/engineering/installation/ProjectInstallationWorkbench.vue'
import ConfigurationWorkbench from '@/views/pms/engineering/configuration/with-device-logs.vue'
import JointTestWorkbench from '@/views/pms/engineering/joint-test/with-device-logs.vue'
import SolutionWorkbench from '@/views/pms/engineering/solution-reviewed/index.vue'
import MaterialExchWorkbench from '@/views/pms/engineering/material-exch/index.vue'
import ProjectCutoverTasks from '@/views/pms/cutover/project-tasks/index.vue'
import ProjectDeviceLogsPanel from './components/ProjectDeviceLogsPanel.vue'
import SatisfactionWorkbench from '@/views/pms/acceptance/satisfaction/index.vue'
import AcceptanceReportWorkbench from '@/views/pms/acceptance/acceptance-report/index.vue'
import * as ContactsApi from '@/api/pms/customer/contacts'
import { loadProjectEquipmentList } from '@/api/pms/commerce/projectEquipment'
import { checkPermi, checkRole } from '@/utils/permission'
import { isBusinessViewId, legacyOwnerId } from '@/api/pms/platform/business-view/ids'
import ProjectRequirementAnalysisPanel from '@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue'
import type { ProjectMasterVO, ProjectInstancesVO } from '@/api/pms/project/projects'

defineOptions({ name: 'PmsProjectMasterDetail' })

const narrowScreen = useMediaQuery('(max-width: 1024px)')
const mobileTaskNavigation = ref<HTMLDetailsElement>()

const route = useRoute()
const projectId = () => isBusinessViewId(route.query.projectId) ? legacyOwnerId(route.query.projectId) : undefined
const router = useRouter()
const approvalDrawerRef = ref<InstanceType<typeof ProjectApprovalDrawer>>()
provide(projectApprovalDetailKey, (id) => approvalDrawerRef.value?.open(id))

const loading = ref(false)
const detail = ref<ProjectMasterVO | null>(null)
const parentProject = ref<ProjectMasterVO | null>(null)
const parentProjectLoading = ref(false)
const majorProjectLevelLabel = computed(() => detail.value?.majorProjectLevel
  ? getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, detail.value.majorProjectLevel).trim() : '')
const instances = ref<ProjectInstancesVO | null>(null)
// 身份卡右侧进度/任务/风险统计（设计稿 hstat）；进度与任务由新链实例视图计算，风险走 /pms/project-risk
// 注意：/pms/project-panoramic 面向旧链 pms_project，ID 空间与本页主数据不同，不能混用
const riskCount = ref<number>()
const treeVersion = ref<number>()
const treeRefreshKey = ref(0)
const historyRefreshKey = ref(0)
const satisfactionRef = ref<InstanceType<typeof SatisfactionWorkbench>>()
const scheduleRef = ref<InstanceType<typeof ProjectSchedulePanel>>()
const acceptanceReportRef = ref<InstanceType<typeof AcceptanceReportWorkbench>>()

// 旧链接 tab=stage-gates 收敛到阶段状态页签；业务中心页签 key 取自 businessModules 分组定义
const businessTabKeys = businessNavItems.map((item) => item.key)
// 全流程导航中的模块页签（DeliveryModuleTable 渲染）；内嵌整页 key（config-log/training/solution/material-exch/arrival/installation/configuration/joint-test/cutover/completion-certificate）与工作台 key（base/members/schedule 等，含已迁入项目概览的 config-log）由专用面板承接
const flowModuleItems = businessNavItems.filter(
  (item) => !flowEmbedKeys.has(item.key) && !!businessModuleConfigs[item.key]
)
// 内嵌工作台组件（Demo 操作界面；均复用既有页面并锁定项目上下文）
const flowEmbedComponents: Record<string, Component> = {
  'config-log': ProjectDeviceLogsPanel,
  'material-exch': MaterialExchWorkbench,
  solution: SolutionWorkbench,
  arrival: ArrivalAcceptanceWorkbench,
  installation: InstallationWorkbench,
  configuration: ConfigurationWorkbench,
  'joint-test': JointTestWorkbench,
  cutover: ProjectCutoverTasks
}
const requestedTab = (
    {
      'stage-gates': 'stage-status',
      'completion-certificate': 'satisfaction',
      // 施工计划整合前的旧页签 key（3.1/3.2/3.4）统一落到整合工作台
      duration: 'schedule',
      'stage-plan': 'schedule',
      'schedule-backward': 'schedule',
      // 基本信息组移除后，旧深链接落到客户信息（其面板内嵌完整联系人工作台）
      'customer-contacts': 'customer',
      acceptance: 'acceptance-reports'
    } as Record<string, string>
  )[String(route.query.tab)] ||
  ([
    'stage-status',
    'stage',
    'customer',
    'equipment',
    'sn-result',
    'config-log',
    'members',
    'schedule',
    'preparation',
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
// 配置Log 自全流程业务基本信息组迁入（1.1.1.1，从属 1.1.1 序列号详情；原组成员与项目概览重复已移除）
const overviewSteps: { key: string; label: string; icon: string; pending?: boolean; permission?: string[] }[] = [
  { key: 'base', label: '基本信息', icon: 'ep:document' },
  { key: 'customer', label: '客户信息', icon: 'ep:office-building' },
  { key: 'tree', label: '项目树', icon: 'ep:share' },
  { key: 'members', label: '项目成员', icon: 'ep:user-filled' },
  { key: 'equipment', label: '设备清单', icon: 'ep:cpu', permission: ['pms:device:query'] },
  { key: 'sn-result', label: '序列号详情', icon: 'ep:cpu', permission: ['pms:device:query'] },
  { key: 'config-log', label: '配置Log', icon: 'ep:document-copy' }
  // 属性判定/匹配历史/生命周期实例三项按需求方 2026-09-30 要求从概览导航隐藏；对应内容面板保留，深链回退基本信息
]

const dimLabel = (value?: string | null, dict?: DICT_TYPE) =>
  value ? getDictLabel(dict!, value) : '不限'
const formatDateTime = (v?: any) => (v ? formatDate(v) : '-')

// ============ 交付模块表格（与 pms-inheritance/project-detail 同一 DeliveryModuleTable） ============
// 设备清单保留全部产品，分别显示订单数量和设备数量；逐台记录见「序列号详情」。
const deliveryModules: Record<string, DeliveryModuleConfig> = {
  equipment: {
    label: '设备清单',
    tableSize: 'default',
    icon: 'ep:cpu',
    load: async (pid, pageNo, pageSize) => {
      const equipment = await loadProjectEquipmentList(pid)
      if (!equipment) return { list: [], total: 0 }
      const rows = equipment.list
      return {
        list: rows.slice((pageNo - 1) * pageSize, pageNo * pageSize).map((r, i) => ({ ...r, no: (pageNo - 1) * pageSize + i + 1 })),
        total: rows.length
      }
    },
    columns: [
      { prop: 'no', label: '序号', width: 46 },
      { prop: 'productCode', label: '产品编码', minWidth: 92 },
      { prop: 'productModel', label: '产品型号', minWidth: 110 },
      { prop: 'productName', label: '产品名称', minWidth: 130 },
      { prop: 'orderNo', label: '订单号', minWidth: 100 },
      { prop: 'projectQty', label: '项目数量', width: 76 },
      { prop: 'deliveredQty', label: '发货数量', width: 76 },
      { prop: 'openQty', label: '未发货数量', width: 88 },
      { prop: 'deviceCount', label: '设备数量', width: 76 }
    ],
    // 该产品的逐台序列号与运维明细在「序列号详情」页签
    actions: [
      { label: '序列号详情', type: 'primary', run: async () => { await switchTab('sn-result') } }
    ]
  }
}

// 序列号行在当前工作台打开设备日志抽屉。
const configLogDrawerRef = ref<InstanceType<typeof ProjectDeviceConfigLogDrawer>>()
const openConfigLogForDeviceId = (deviceId: number, serialNumber: string) => {
  configLogDrawerRef.value?.open(deviceId, serialNumber)
}

const requirementRef = ref<InstanceType<typeof ProjectRequirementAnalysisPanel>>()
const switchTab = async (key: string) => {
  if (key !== activeTab.value && activeTab.value === 'requirement-analysis' && (await requirementRef.value?.requestLeave()) === false) return
  if (key !== activeTab.value && activeTab.value === 'schedule' && (await scheduleRef.value?.requestLeave()) === false) return
  if (key !== activeTab.value && satisfactionRef.value?.requestLeave() === false) return
  if (key !== activeTab.value && (await acceptanceReportRef.value?.requestLeave()) === false) return
  if (key !== activeTab.value && (await flowRef.value?.requestLeave()) === false) return
  if (key !== activeTab.value && activeTab.value === 'stage' && (await stageGateRef.value?.requestLeave()) === false) return
  activeTab.value = key
  visitedTabs.value = new Set([...visitedTabs.value, key])
}

const primaryContact = ref<ContactsApi.ContactVO>()
const primaryContactPending = ref(false)
const loadPrimaryContact = async () => {
  const id = projectId()
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
const stageGateRef = ref<InstanceType<typeof StageGateResultsPanel>>()
const deliverableRef = ref<InstanceType<typeof ProjectDeliverableDialog>>()
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

// 无子任务的阶段：导航不渲染展开箭头、不展开任务树（阶段工作台同样不显示任务表格）
const stageHasTasks = (stage: StageInstance) => instTasks(stage.stageCode).length > 0

const stageHistory = computed(() =>
  sortedStages.value
    .filter((stage) => stage.actualStartTime)
    .sort((a, b) => (a.actualStartTime! < b.actualStartTime! ? 1 : -1))
)

// 侧栏「完成条件」的任务口径：选中任务时展示该任务绑定的交付件（含状态），门禁部分由阶段门禁面板承载
const flowTaskDeliverables = computed(() => {
  const selection = stageSelection.value
  const taskCode = selection?.task?.taskCode
  if (selection?.kind !== 'task' || !taskCode) return []
  return (instances.value?.deliverables || []).filter((item) => item.taskCode === taskCode)
})


const ensureStageSelection = () => {
  if (stageSelection.value?.stageCode && sortedStages.value.some((stage) => stage.stageCode === stageSelection.value?.stageCode)) return
  const current = sortedStages.value.find((stage) => stage.status === 'ACTIVE')
  const target = current || sortedStages.value[0]
  if (target) stageSelection.value = { kind: 'stage', stageCode: target.stageCode }
}

const selectStage = async (stageCode: string) => {
  // 同页签内切换阶段不经过 switchTab 守卫，先保护工作台与门禁侧栏未保存的修改
  if (activeTab.value === 'stage' && stageSelection.value?.stageCode !== stageCode) {
    if ((await flowRef.value?.requestLeave()) === false) return
    if ((await stageGateRef.value?.requestLeave()) === false) return
  }
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
  if (activeTab.value === 'stage' && stageSelection.value?.taskId !== task.taskId) {
    if ((await flowRef.value?.requestLeave()) === false) return
    if ((await stageGateRef.value?.requestLeave()) === false) return
  }
  // 携带导航任务快照：工作台返回前面板头部即时呈现所选任务，不出现占位标题
  stageSelection.value = { kind: 'task', stageCode, taskId: task.taskId, task }
  await switchTab('stage')
}

const onMobileTaskSelect = async (task: TaskNode) => {
  await onTaskSelect(task, expandedStage.value)
  if (stageSelection.value?.taskId === task.taskId && mobileTaskNavigation.value) {
    mobileTaskNavigation.value.open = false
  }
}

const handleFlowChanged = async () => {
  stageTreeToken.value++
  await loadAll()
}

// ============ 数据加载 ============
const loadDetail = async () => {
  const id = projectId()
  if (!id) return
  detail.value = await ProjectsApi.getProject(id)
  parentProject.value = null
  const parentId = detail.value.parentId
  parentProjectLoading.value = Boolean(parentId)
  if (parentId) {
    try {
      parentProject.value = await ProjectsApi.getProject(parentId)
    } catch {
      parentProject.value = null
    } finally {
      parentProjectLoading.value = false
    }
  }
}
const handleAttributeUpdated = async () => {
  await loadDetail()
  historyRefreshKey.value++
}
const handleMembersUpdated = async () => {
  await Promise.all([loadDetail(), loadInstances()])
}
const handleScheduleChanged = async () => {
  await Promise.all([loadDetail(), loadInstances()])
  stageTreeToken.value++
}
const loadInstances = async () => {
  const id = projectId()
  if (!id) return
  instances.value = await ProjectsApi.getProjectInstances(id)
}
const loadRiskCount = async () => {
  const id = projectId()
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
    await Promise.all([loadDetail(), loadInstances(), loadPrimaryContact(), loadRiskCount()])
    stageTreeToken.value++
    // 深链直达阶段和任务页签时实例尚未就绪，首帧补选必然空转；
    // 在实例加载完成后回填默认选中，避免中栏空白、侧栏完成条件以空 stageCode 请求门禁报错
    ensureStageSelection()
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/pms/project-management/projects')

onMounted(() => {
  void loadAll()
})
</script>

<style lang="scss" scoped>
.template-source-history {
  margin-top: 20px;
  color: var(--el-text-color-secondary);

  summary { cursor: pointer; font-weight: 600; }
}
.parent-project-link, .parent-project-link:hover {
  color: var(--el-color-primary);
  text-decoration: none;
}
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

.mobile-task-navigation summary {
  cursor: pointer;
  color: var(--el-color-primary);
}
.mobile-stage-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.mobile-stage-options :deep(.el-button + .el-button) {
  margin-left: 0;
}

.canvas {
  flex: 1 1 auto;
  min-width: 0;
}

.rail-wrap {
  flex: 0 0 280px;
  /* 吸附在滚动容器（ElScrollbar）顶部：限高为视口减去固定头部与上下留白，
     导航过长时仅导航内部滚动，不把整页撑高 */
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  max-height: calc(
    100vh - var(--top-tool-height) - var(--tags-view-height) - var(--app-content-padding) * 2
  );

  :deep(.el-card__body) {
    flex: 1 1 auto;
    min-height: 0;
    /* 滚动条平时隐藏，悬停导航栏时显示 */
    scrollbar-width: thin;
    scrollbar-color: transparent transparent;

    &:hover {
      scrollbar-color: var(--el-border-color-darker) transparent;
    }

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background-color: transparent;
      border-radius: 3px;
    }

    &:hover::-webkit-scrollbar-thumb {
      background-color: var(--el-border-color-darker);
    }
  }
}

.rail-stage {
  margin-bottom: 14px;
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
    flex: 0 0 auto;
    /* 窄屏下导航改为横向滚动条布局，取消吸顶限高 */
    position: static;
    max-height: none;

    :deep(.el-card__body) {
      display: flex;
      align-items: center;
      gap: 6px;
      overflow-x: auto;
    }

    :deep(.flow-nav),
    :deep(.flow-group) {
      display: flex;
      align-items: center;
      flex: 0 0 auto;
      gap: 6px;
      margin: 0;
    }

    :deep(.flow-group-title) {
      display: none;
    }

    :deep(.flow-item) {
      flex: 0 0 auto;
      width: auto;
      white-space: nowrap;
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
