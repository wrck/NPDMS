<template>
  <div v-loading="loading">
    <!-- ============ 顶部项目档案区 ============ -->
    <ContentWrap>
      <div class="project-header">
        <div class="project-header-left">
          <div class="project-title-row">
            <span class="project-code">{{ detail?.projectCode || '—' }}</span>
            <h2 class="project-name">{{ detail?.projectName || '未选择项目' }}</h2>
            <dict-tag
              v-if="detail?.status"
              :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE"
              :value="detail.status ?? ''"
            />
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
          <button
            v-for="step in overviewSteps"
            :key="step.key"
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === step.key }"
            @click="switchTab(step.key)"
          >
            <Icon :icon="step.icon" class="rail-icon" />
            <span class="rail-label">{{ step.label }}</span>
          </button>
        </div>
        <div class="rail-stage">
          <div class="rail-stage-title">交付准备</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'duration' }"
            @click="switchTab('duration')"
            v-hasPermi="['pms:construction-plan:query', 'pms:construction-plan:duration-manage']"
          >
            <Icon icon="ep:calendar" class="rail-icon" />
            <span class="rail-label">项目工期</span>
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
          <div class="rail-stage-title">项目树</div>
          <button
            class="rail-item"
            :class="{ 'rail-item--active': activeTab === 'tree' }"
            @click="switchTab('tree')"
          >
            <Icon icon="ep:share" class="rail-icon" />
            <span class="rail-label">项目树</span>
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
              <dict-tag
                :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE"
                :value="detail.status ?? ''"
              />
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
        <ContentWrap v-show="activeTab === 'members'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:user-filled" /> 成员区间（服务经理）</span>
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

        <ProjectTaskPanel
          v-if="detail?.id && visitedTabs.has('tasks')"
          v-show="activeTab === 'tasks'"
          :project-id="detail.id"
          @tree-version="treeVersion = $event"
        />

        <ProjectDurationPanel
          v-if="detail?.id && visitedTabs.has('duration')"
          v-show="activeTab === 'duration'"
          :project="detail"
        />

        <ProjectSplitWizard
          v-if="detail?.id && visitedTabs.has('split')"
          v-show="activeTab === 'split'"
          :project-id="detail.id"
          @applied="treeRefreshKey++"
        />
        <ProjectTreePanel
          v-if="detail?.id && visitedTabs.has('tree')"
          :key="treeRefreshKey"
          v-show="activeTab === 'tree'"
          :project-id="detail.id"
          @tree-version="treeVersion = $event"
        />
        <ProjectProgressPanel
          v-if="detail?.id && visitedTabs.has('progress')"
          v-show="activeTab === 'progress'"
          :project-id="detail.id"
          :tree-version="treeVersion"
        />
        <ProjectClosureGuardPanel
          v-if="detail?.id && visitedTabs.has('closure')"
          v-show="activeTab === 'closure'"
          :project-id="detail.id"
          :tree-version="treeVersion"
        />
        <ProjectAuthorizationPanel
          v-if="detail?.id && visitedTabs.has('authorization')"
          v-show="activeTab === 'authorization'"
          :project-id="detail.id"
        />
        <ProjectGovernancePanel
          v-if="detail?.id && visitedTabs.has('governance')"
          v-show="activeTab === 'governance'"
          :project="detail"
          @updated="loadAll"
        />
        <ProjectServiceManagerPanel
          v-if="detail?.id && visitedTabs.has('service-managers')"
          v-show="activeTab === 'service-managers'"
          :project-id="detail.id"
        />
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
import ProjectSplitWizard from './components/ProjectSplitWizard.vue'
import ProjectTreePanel from './components/ProjectTreePanel.vue'
import ProjectProgressPanel from './components/ProjectProgressPanel.vue'
import ProjectClosureGuardPanel from './components/ProjectClosureGuardPanel.vue'
import ProjectAuthorizationPanel from './components/ProjectAuthorizationPanel.vue'
import ProjectGovernancePanel from './components/ProjectGovernancePanel.vue'
import ProjectServiceManagerPanel from './components/ProjectServiceManagerPanel.vue'
import ProjectAttributePanel from './components/ProjectAttributePanel.vue'
import ProjectTemplateMatchHistoryPanel from './components/ProjectTemplateMatchHistoryPanel.vue'
import ProjectTaskPanel from './components/ProjectTaskPanel.vue'
import ProjectDurationPanel from './components/ProjectDurationPanel.vue'
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
const treeVersion = ref<number>()
const treeRefreshKey = ref(0)
const historyRefreshKey = ref(0)

const requestedTab = ['tasks', 'duration'].includes(String(route.query.tab))
  ? String(route.query.tab)
  : 'base'
const activeTab = ref(requestedTab)
const visitedTabs = ref(new Set([requestedTab]))
const overviewSteps = [
  { key: 'base', label: '基本信息', icon: 'ep:document' },
  { key: 'attributes', label: '属性判定', icon: 'ep:edit' },
  { key: 'match-history', label: '匹配历史', icon: 'ep:clock' },
  { key: 'instances', label: '生命周期实例', icon: 'ep:tickets' },
  { key: 'members', label: '成员区间', icon: 'ep:user-filled' },
  { key: 'tasks', label: '项目任务', icon: 'ep:list' }
]

const dimLabel = (value?: string | null, dict?: DICT_TYPE) =>
  value ? getDictLabel(dict!, value) : '不限'
const formatDateTime = (v?: any) => (v ? formatDate(v) : '-')

const switchTab = (key: string) => {
  activeTab.value = key
  visitedTabs.value = new Set([...visitedTabs.value, key])
}

// ============ 实例视图 ============
const instTasks = (code: string) => instances.value?.tasks.filter((t) => t.stageCode === code) || []
const instMilestones = (code: string) =>
  instances.value?.milestones.filter((m) => m.stageCode === code) || []
const instDeliverables = (code: string) =>
  instances.value?.deliverables.filter((d) => d.stageCode === code) || []
const instGates = (code: string) => instances.value?.gates.filter((g) => g.stageCode === code) || []

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
const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadDetail(), loadInstances(), loadMembers()])
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/pms/project-management/projects')

onMounted(() => {
  loadAll()
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
