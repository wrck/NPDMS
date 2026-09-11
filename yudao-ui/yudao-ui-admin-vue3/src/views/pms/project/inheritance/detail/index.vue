<template>
  <div v-loading="loading">
    <!-- ============ 顶部项目档案区 ============ -->
    <ContentWrap>
      <div class="project-header">
        <div class="project-header-left">
          <div class="project-title-row">
            <span class="project-code">{{ detail?.projectCode || '—' }}</span>
            <h2 class="project-name">{{ detail?.projectName || '未选择项目' }}</h2>
            <ProjectStatusTag :project="detail" />
            <el-tag v-if="detail?.lifecycleTemplateId" size="small" type="info">
              模板 #{{ detail.lifecycleTemplateId }} v{{ detail.lifecycleTemplateRevisionNo }}
            </el-tag>
          </div>
          <div class="project-meta-row">
            <span class="meta-item">
              <Icon icon="ep:office-building" />{{ detail?.customerName || '-' }}
            </span>
            <span class="meta-item">
              <Icon icon="ep:files" />
              {{ dimLabel(detail?.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD) }} /
              {{ dimLabel(detail?.projectCategory, DICT_TYPE.PMS_PROJECT_CATEGORY) }} /
              {{ dimLabel(detail?.implementationMode, DICT_TYPE.PMS_IMPLEMENTATION_METHOD) }}
            </span>
            <span class="meta-item"
              ><Icon icon="ep:calendar" />{{ formatDateTime(detail?.createTime) }}</span
            >
          </div>
        </div>
        <div class="project-header-right">
          <el-button plain @click="goBack"><Icon icon="ep:back" />返回列表</el-button>
        </div>
      </div>
    </ContentWrap>

    <!-- ============ 主体：左侧导轨 + 右侧内容区 ============ -->
    <div class="detail-body">
      <!-- 左侧导轨 -->
      <ContentWrap class="rail-wrap">
        <div class="rail-stage">
          <div class="rail-stage-title">项目概览</div>
          <template v-for="step in overviewSteps" :key="step.key">
            <button
              v-if="!step.permission || checkPermi(step.permission)"
              class="rail-item"
              :class="{ 'rail-item--active': activeTab === step.key }"
              @click="switchTab(step.key)"
            >
              <Icon :icon="step.icon" class="rail-icon" />
              <span class="rail-label">{{ step.label }}</span>
            </button>
          </template>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">交付流程</div>
          <ProjectFlowNavigation
            v-if="detail?.id"
            ref="flowNavigationRef"
            :project-id="detail.id"
            :selection="flowSelection"
            @select="handleFlowSelect"
          />
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">项目档案</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'attributes' }"
            @click="switchTab('attributes')"
          >
            <Icon icon="ep:edit" class="rail-icon" />
            <span class="rail-label">属性判定</span>
          </button>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'match-history' }"
            @click="switchTab('match-history')"
          >
            <Icon icon="ep:clock" class="rail-icon" />
            <span class="rail-label">匹配历史</span>
          </button>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'instances' }"
            @click="switchTab('instances')"
          >
            <Icon icon="ep:tickets" class="rail-icon" />
            <span class="rail-label">生命周期实例</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">交付准备</div>
          <button class="rail-item" :class="{ 'rail-item--active': activeTab === 'customer-contacts' }"
            @click="switchTab('customer-contacts')" v-hasPermi="['pms:customer-contact:query']">
            <Icon icon="ep:phone" class="rail-icon"/><span class="rail-label">用户联系人</span>
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
            v-hasPermi="['pms:eng-site-survey:query']"
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
          <button class="rail-item" :class="{ 'rail-item--active': activeTab === 'satisfaction' }"
            @click="switchTab('satisfaction')" v-hasPermi="['pms:acceptance:satisfaction:query']">
            <Icon icon="ep:chat-dot-round" class="rail-icon" />
            <span class="rail-label">满意度</span>
          </button>
          <button class="rail-item" :class="{ 'rail-item--active': activeTab === 'acceptance-reports' }"
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

        <!-- ============ 项目概览：成员区间 ============ -->
        <ProjectMembersPanel v-if="detail?.id && activeTab === 'members'" :project="detail" @updated="handleMembersUpdated" />

        <div v-if="detail?.id && visitedTabs.has('tasks')" v-show="activeTab === 'tasks'" class="min-w-0" data-testid="project-pane-tasks">
          <ProjectTaskPanel
            :project-id="detail.id"
            @tree-version="treeVersion = $event"
            @updated="loadAll"
          />
        </div>

        <div v-if="detail?.id && (visitedTabs.has('base') || visitedTabs.has('tasks') || visitedTabs.has('stage-gates'))" v-show="['base', 'tasks', 'stage-gates'].includes(activeTab)" class="min-w-0" data-testid="project-pane-stage-gates">
          <ProjectStageGatePanel
            :project-id="detail.id"
            :key="`${detail.id}:${detail.version}:${activeTab}`"
            @changed="handleStageChanged"
          />
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
        <div v-if="detail?.id && visitedTabs.has('equipment')" v-show="activeTab === 'equipment'" class="min-w-0" data-testid="project-pane-equipment">
          <ProjectEquipmentPanel :key="`equipment-${detail.id}`" :project-id="detail.id" />
        </div>
        <div v-if="detail?.id && visitedTabs.has('scope')" v-show="activeTab === 'scope'" class="min-w-0" data-testid="project-pane-scope">
          <ProjectDeliveryScopePanel :key="`scope-${detail.id}`" :project-context="scopeContext" />
        </div>
        <div v-if="detail?.id && visitedTabs.has('flow')" v-show="activeTab === 'flow'" class="min-w-0" data-testid="project-pane-flow">
          <ProjectFlowPanel ref="flowPanelRef" :project-id="detail.id" :project="detail" :instances="instances" :selection="flowSelection" @changed="handleStageChanged" />
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { isBusinessViewId, legacyOwnerId } from '@/api/pms/platform/business-view/ids'
import { useMediaQuery } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import ProjectSplitWizard from '@/views/pms/project/project-master-detail/components/ProjectSplitWizard.vue'
import ProjectTreePanel from '@/views/pms/project/inheritance/tree/ProjectTreePanel.vue'
import ProjectProgressPanel from '@/views/pms/project/project-master-detail/components/ProjectProgressPanel.vue'
import ProjectClosureGuardPanel from '@/views/pms/project/project-master-detail/components/ProjectClosureGuardPanel.vue'
import ProjectNormalClosurePanel from '@/views/pms/project/project-master-detail/components/ProjectNormalClosurePanel.vue'
import ProjectStatusTag from '@/views/pms/project/projects/ProjectStatusTag.vue'
import ProjectAuthorizationPanel from '@/views/pms/project/project-master-detail/components/ProjectAuthorizationPanel.vue'
import ProjectGovernancePanel from '@/views/pms/project/project-master-detail/components/ProjectGovernancePanel.vue'
import ProjectMembersPanel from '../members/ProjectMembersPanel.vue'
import ProjectAttributePanel from '@/views/pms/project/project-master-detail/components/ProjectAttributePanel.vue'
import ProjectTemplateMatchHistoryPanel from '@/views/pms/project/project-master-detail/components/ProjectTemplateMatchHistoryPanel.vue'
import ProjectTaskPanel from '@/views/pms/project/project-master-detail/components/ProjectTaskPanel.vue'
import ProjectFlowNavigation from '@/views/pms/project/project-master-detail/components/ProjectFlowNavigation.vue'
import ProjectFlowPanel from '@/views/pms/project/project-master-detail/components/ProjectFlowPanel.vue'
import type { ProjectFlowSelection } from '@/views/pms/project/project-master-detail/components/project-flow'
import ProjectStageGatePanel from '@/views/pms/project/project-master-detail/components/ProjectStageGatePanel.vue'
import ProjectDurationPanel from '@/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue'
import ProjectSiteSurveyPanel from '@/views/pms/engineering/site-survey/index.vue'
import ProjectCustomerContacts from '@/views/pms/customer/contacts/index.vue'
import SatisfactionWorkbench from '@/views/pms/project/satisfaction/index.vue'
import AcceptanceReportWorkbench from '@/views/pms/project/acceptance-report/index.vue'
import * as ContactsApi from '@/api/pms/customer/contacts'
import { checkPermi } from '@/utils/permission'
import ProjectRequirementAnalysisPanel from '@/views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.vue'
import ProjectEquipmentPanel from '@/views/pms/asset/equipment/index.vue'
import ProjectDeliveryScopePanel from '@/views/pms/commerce/delivery-scope/index.vue'
import type { ProjectRouteContext } from '@/views/pms/commerce/commerceInteraction'
import type {
  ProjectMasterVO,
  ProjectInstancesVO
} from '@/api/pms/project/projects'

defineOptions({ name: 'PmsProjectInheritanceDetail' })

const route = useRoute()
const projectId = () => isBusinessViewId(route.query.projectId) ? legacyOwnerId(route.query.projectId) : undefined
const router = useRouter()
const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))

const loading = ref(false)
const detail = ref<ProjectMasterVO | null>(null)
const instances = ref<ProjectInstancesVO | null>(null)
const scopeVersion = ref<number>()
const flowSelection = ref<ProjectFlowSelection>()
const flowPanelRef = ref<InstanceType<typeof ProjectFlowPanel>>()
const flowNavigationRef = ref<InstanceType<typeof ProjectFlowNavigation>>()
const scopeContext = computed<ProjectRouteContext | undefined>(() => {
  const project = detail.value
  if (!project?.id || project.version === undefined || scopeVersion.value === undefined) {
    return undefined
  }
  return {
    projectId: project.id,
    projectVersion: project.version,
    projectScopeVersion: scopeVersion.value
  }
})
const treeVersion = ref<number>()
const treeRefreshKey = ref(0)
const historyRefreshKey = ref(0)
const satisfactionRef = ref<InstanceType<typeof SatisfactionWorkbench>>()
const acceptanceReportRef = ref<InstanceType<typeof AcceptanceReportWorkbench>>()

const TAB_KEYS = [
  'base',
  'tree',
  'members',
  'tasks',
  'equipment',
  'scope',
  'flow',
  'attributes',
  'match-history',
  'instances',
  'stage-gates',
  'duration',
  'preparation',
  'customer-contacts',
  'requirement-analysis',
  'satisfaction',
  'acceptance-reports',
  'closure',
  'split',
  'progress',
  'governance',
  'authorization'
]
const requestedTab = () =>
  route.query.section === 'members'
    ? 'members'
    : TAB_KEYS.includes(String(route.query.tab))
      ? String(route.query.tab)
      : 'base'
const activeTab = ref(requestedTab())
const visitedTabs = ref(new Set([requestedTab()]))
const overviewSteps: { key: string; label: string; icon: string; permission?: string[] }[] = [
  { key: 'base', label: '基本信息', icon: 'ep:document' },
  { key: 'tree', label: '项目树', icon: 'ep:share' },
  { key: 'members', label: '项目成员', icon: 'ep:user-filled' },
  { key: 'tasks', label: '项目任务', icon: 'ep:list' },
  { key: 'equipment', label: '设备清单', icon: 'ep:cpu', permission: ['pms:equipment:query'] },
  { key: 'scope', label: '实施范围', icon: 'ep:files', permission: ['pms:commerce:scope:query'] }
]

const dimLabel = (value?: string | null, dict?: DICT_TYPE) =>
  value ? getDictLabel(dict!, value) : '不限'
const formatDateTime = (v?: any) => (v ? formatDate(v) : '-')

const switchTab = async (key: string) => {
  if (key !== activeTab.value && activeTab.value === 'flow' && (await flowPanelRef.value?.requestLeave()) === false) return false
  if (key !== activeTab.value && satisfactionRef.value?.requestLeave() === false) return
  if (key !== activeTab.value && (await acceptanceReportRef.value?.requestLeave()) === false) return
  activeTab.value = key
  visitedTabs.value = new Set([...visitedTabs.value, key])
  return true
}

let flowSwitchSequence = 0
const handleFlowSelect = async (payload: ProjectFlowSelection) => {
  const sequence = ++flowSwitchSequence
  if ((await flowPanelRef.value?.requestLeave()) === false || sequence !== flowSwitchSequence) return
  if (!(await switchTab('flow')) || sequence !== flowSwitchSequence) return
  flowSelection.value = payload
}

const primaryContact = ref<ContactsApi.ContactVO>()
const primaryContactPending = ref(false)
const loadPrimaryContact = async () => {
  const id = projectId()
  if (!id || !checkPermi(['pms:customer-contact:query'])) return
  try {
    const page = await ContactsApi.getProjectPage(id, { pageNo: 1, pageSize: 1, status: 0 })
    if (id !== projectId()) return
    primaryContact.value = page.list.find(contact => contact.primaryFlag)
    primaryContactPending.value = false
  } catch { if (id === projectId()) primaryContactPending.value = true }
}
const handleContactsChanged = async () => { await Promise.all([loadDetail(), loadPrimaryContact()]) }

// ============ 实例视图 ============
const instTasks = (code: string) => instances.value?.tasks.filter((t) => t.stageCode === code) || []
const instMilestones = (code: string) =>
  instances.value?.milestones.filter((m) => m.stageCode === code) || []
const instDeliverables = (code: string) =>
  instances.value?.deliverables.filter((d) => d.stageCode === code) || []
const instGates = (code: string) => instances.value?.gates.filter((g) => g.stageCode === code) || []

// ============ 数据加载 ============
const loadDetail = async () => {
  const id = projectId()
  if (!id) return
  const project = await ProjectsApi.getProject(id)
  if (id === projectId()) detail.value = project
}
const handleAttributeUpdated = async () => {
  await loadDetail()
  historyRefreshKey.value++
}
const handleMembersUpdated = async () => {
  await Promise.all([loadDetail(), loadInstances()])
}
const handleStageChanged = async () => {
  await Promise.all([loadDetail(), loadInstances(), flowNavigationRef.value?.reload()])
  await flowPanelRef.value?.refreshSummary()
}
const loadInstances = async () => {
  const id = projectId()
  if (!id) return
  const result = await ProjectsApi.getProjectInstances(id)
  if (id === projectId()) instances.value = result
}
const loadScopeVersion = async () => {
  const id = projectId()
  if (!id || !checkPermi(['pms:commerce:scope:query'])) return
  try {
    const scope = await ProjectsApi.queryTree(id, { queryType: 'LOCATE', pageSize: 1 })
    if (id === projectId()) scopeVersion.value = scope.updating ? undefined : scope.treeVersion
  } catch {
    if (id === projectId()) scopeVersion.value = undefined
  }
}
const loadAll = async () => {
  const refreshMounted = Boolean(detail.value)
  loading.value = true
  try {
    await Promise.all([loadDetail(), loadInstances(), loadPrimaryContact(), loadScopeVersion()])
    if (refreshMounted) await Promise.all([flowNavigationRef.value?.reload(), flowPanelRef.value?.refreshSummary()])
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/pms-inheritance/projects')

watch(() => route.query.projectId, () => {
  detail.value = null
  instances.value = null
  primaryContact.value = undefined
  scopeVersion.value = undefined
  flowSelection.value = undefined
  activeTab.value = requestedTab()
  visitedTabs.value = new Set([activeTab.value])
  loadAll()
}, { immediate: true })
watch(() => [route.query.section, route.query.tab], () => {
  switchTab(requestedTab())
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

.project-code {
  padding: 2px 8px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.project-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.project-meta-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.project-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 主体布局 */
.detail-body {
  display: flex;
  align-items: flex-start;
  gap: 15px;
}

.rail-wrap {
  flex: 0 0 220px;

  :deep(.el-card__body) {
    padding: 8px 6px;
  }
}

.rail-stage {
  margin-bottom: 14px;
}

.rail-stage-title {
  padding: 6px 10px 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--el-text-color-placeholder);
  text-transform: uppercase;
}

.rail-item {
  display: flex;
  width: 100%;
  padding: 7px 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
  transition: all 0.15s ease;
  align-items: center;
  gap: 6px;

  &:hover {
    background: var(--el-fill-color-light);
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

.canvas {
  flex: 1;
  min-width: 0;
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

@media (width >= 992px) and (width <= 1199px) {
  .rail-wrap {
    flex-basis: 180px;
  }
}

@media (width <= 991px) {
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
