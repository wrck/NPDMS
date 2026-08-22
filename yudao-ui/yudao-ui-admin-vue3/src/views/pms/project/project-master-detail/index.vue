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
              :value="detail.status!"
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
        </div>
      </ContentWrap>

      <!-- 右侧内容区 -->
      <div class="canvas">
        <!-- ============ 项目概览：基本信息 ============ -->
        <ContentWrap v-show="activeTab === 'base'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:document" /> 基本信息</span>
          </div>
          <el-descriptions v-if="detail" :column="2" border size="small">
            <el-descriptions-item label="项目编码">{{ detail.projectCode }}</el-descriptions-item>
            <el-descriptions-item label="编码命名空间">
              根 #{{ detail.codeRootId }} · 序号 {{ detail.projectSequence }} · 规则
              {{ detail.codeRuleVersion }}
            </el-descriptions-item>
            <el-descriptions-item label="项目名称" :span="2">{{
              detail.projectName
            }}</el-descriptions-item>
            <el-descriptions-item label="签约方式">
              <dict-tag :type="DICT_TYPE.PMS_SIGNING_METHOD" :value="detail.signingMethod!" />
            </el-descriptions-item>
            <el-descriptions-item label="项目类别">
              <dict-tag :type="DICT_TYPE.PMS_PROJECT_CATEGORY" :value="detail.projectCategory!" />
            </el-descriptions-item>
            <el-descriptions-item label="实施方式">
              <dict-tag
                :type="DICT_TYPE.PMS_IMPLEMENTATION_METHOD"
                :value="detail.implementationMode!"
              />
            </el-descriptions-item>
            <el-descriptions-item label="重大项目级别">
              <dict-tag
                v-if="detail.majorProjectLevel"
                :type="DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL"
                :value="detail.majorProjectLevel"
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
              <dict-tag :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE" :value="detail.status!" />
            </el-descriptions-item>
            <el-descriptions-item label="创建来源">
              <dict-tag :type="DICT_TYPE.PMS_PROJECT_SOURCE_TYPE" :value="detail.sourceType!" />
            </el-descriptions-item>
            <el-descriptions-item label="创建原因" :span="2">{{
              detail.creationReason || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="2">{{
              formatDateTime(detail.createTime)
            }}</el-descriptions-item>
          </el-descriptions>
        </ContentWrap>

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

        <!-- ============ 项目树 ============ -->
        <ContentWrap v-show="activeTab === 'tree'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:share" /> 项目树（直接下级，按需展开）</span>
            <div class="panel-header-actions">
              <el-button
                type="primary"
                size="small"
                @click="openCreateChild"
                v-hasPermi="['pms:project:create']"
              >
                <Icon icon="ep:plus" />下挂子项目
              </el-button>
              <el-button
                type="warning"
                size="small"
                @click="openMove"
                v-hasPermi="['pms:project:update']"
              >
                <Icon icon="ep:rank" />子树移动
              </el-button>
            </div>
          </div>
          <el-table
            :data="children"
            row-key="id"
            :tree-props="{ children: 'children' }"
            default-expand-all
            size="small"
            empty-text="暂无直接下级项目"
          >
            <el-table-column prop="projectCode" label="项目编码" min-width="180" />
            <el-table-column
              prop="projectName"
              label="项目名称"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE" :value="row.status" />
              </template>
            </el-table-column>
            <el-table-column prop="treeDepth" label="深度" width="70" />
            <el-table-column label="业务层级" width="110">
              <template #default="{ row }">{{
                row.businessLevelName || row.businessLevelCode || '-'
              }}</template>
            </el-table-column>
          </el-table>
        </ContentWrap>

        <!-- ============ 进度汇总 ============ -->
        <ContentWrap v-show="activeTab === 'progress'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:data-analysis" /> 进度汇总</span>
            <el-button v-if="children.length" type="primary" plain @click="openWeights">
              <Icon icon="ep:edit" />设置权重
            </el-button>
          </div>
          <el-descriptions :column="1" border size="small" class="mb-12px">
            <el-descriptions-item label="汇总进度">
              <el-progress
                :percentage="Number(progress?.aggregate ?? 0)"
                :stroke-width="12"
                :format="(p) => `${p}%`"
              />
            </el-descriptions-item>
          </el-descriptions>
          <el-table
            v-if="progress?.children?.length"
            :data="progress.children"
            size="small"
            border
            empty-text="暂无直接子项目"
          >
            <el-table-column prop="projectCode" label="子项目编码" min-width="180" />
            <el-table-column
              prop="projectName"
              label="子项目名称"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column label="进度" width="140">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.progress ?? 0)" :stroke-width="6" />
              </template>
            </el-table-column>
            <el-table-column label="权重" width="110">
              <template #default="{ row }">
                {{ ((row.normalizedWeight ?? 0) * 100).toFixed(2) }}%
              </template>
            </el-table-column>
            <el-table-column label="权重来源" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.weightSource === 'MANUAL' ? 'primary' : 'info'">
                  {{ row.weightSource === 'MANUAL' ? '人工' : '等权' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无直接子项目" />
        </ContentWrap>
      </div>
    </div>

    <!-- ============ 下挂子项目弹窗 ============ -->
    <Dialog v-model="createChildVisible" title="下挂子项目" width="520px">
      <el-form
        ref="createChildFormRef"
        :model="createChildForm"
        :rules="createChildRules"
        label-width="100px"
      >
        <el-form-item label="父项目">
          <el-input :model-value="detail?.projectCode + ' ' + detail?.projectName" disabled />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="createChildForm.projectName" placeholder="子项目名称" />
        </el-form-item>
        <el-form-item label="创建原因" prop="creationReason">
          <el-input
            v-model="createChildForm.creationReason"
            type="textarea"
            :rows="2"
            placeholder="BR-2 必填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createChildVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreateChild">保存</el-button>
      </template>
    </Dialog>

    <!-- ============ 子树移动弹窗 ============ -->
    <Dialog v-model="moveVisible" title="子树移动" width="520px">
      <el-form ref="moveFormRef" :model="moveForm" :rules="moveRules" label-width="110px">
        <el-form-item label="待移动项目">
          <el-input :model-value="detail?.projectCode + ' ' + detail?.projectName" disabled />
        </el-form-item>
        <el-form-item label="目标父项目" prop="newParentId">
          <PmsEntitySelect
            v-model="moveForm.newParentId"
            :api="ProjectsApi.getProjectPage"
            label-field="projectName"
            value-field="id"
            query-field="projectName"
            placeholder="请选择目标父项目"
            class="!w-full"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitMove">保存</el-button>
      </template>
    </Dialog>

    <!-- ============ 直接子项目权重弹窗 ============ -->
    <Dialog v-model="weightsVisible" title="设置直接子项目权重" width="680px">
      <el-alert
        title="必须完整设置全部直接子项目，人工权重合计为 100% 后整组生效。"
        type="info"
        :closable="false"
        class="mb-12px"
      />
      <el-table :data="weightItems" size="small" border>
        <el-table-column prop="projectCode" label="项目编码" min-width="190" />
        <el-table-column
          prop="projectName"
          label="项目名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="权重（%）" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="100" :precision="2" :step="1" />
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-12px text-right">当前合计：{{ weightTotal.toFixed(2) }}%</div>
      <template #footer>
        <el-button @click="weightsVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitWeights">整组生效</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import { createSubmissionIdempotencyState } from '../projects/submissionIdempotency'
import type {
  ProjectMasterVO,
  ProjectInstancesVO,
  ProjectMemberAssignmentVO,
  ProjectProgressVO
} from '@/api/pms/project/projects'

defineOptions({ name: 'PmsProjectMasterDetail' })

const route = useRoute()
const router = useRouter()
const message = useMessage()

const loading = ref(false)
const detail = ref<ProjectMasterVO | null>(null)
const instances = ref<ProjectInstancesVO | null>(null)
const members = ref<ProjectMemberAssignmentVO[]>([])
const children = ref<ProjectMasterVO[]>([])
const progress = ref<ProjectProgressVO | null>(null)

const activeTab = ref('base')
const overviewSteps = [
  { key: 'base', label: '基本信息', icon: 'ep:document' },
  { key: 'instances', label: '生命周期实例', icon: 'ep:tickets' },
  { key: 'members', label: '成员区间', icon: 'ep:user-filled' }
]

const dimLabel = (value?: string | null, dict?: DICT_TYPE) =>
  value ? getDictLabel(dict!, value) : '不限'
const formatDateTime = (v?: any) => (v ? formatDate(v) : '-')

const switchTab = (key: string) => {
  activeTab.value = key
}

// ============ 下挂子项目 ============
const createChildVisible = ref(false)
const createChildFormRef = ref()
const saving = ref(false)
const createChildForm = reactive({ projectName: '', creationReason: '' })
const createChildSubmission = createSubmissionIdempotencyState()
const createChildRules = {
  projectName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
  creationReason: [{ required: true, message: '创建原因不能为空（BR-2）', trigger: 'blur' }]
}
const openCreateChild = () => {
  createChildSubmission.reset()
  createChildForm.projectName = ''
  createChildForm.creationReason = ''
  createChildVisible.value = true
}
const submitCreateChild = async () => {
  await createChildFormRef.value?.validate()
  if (!detail.value?.id || !detail.value.companyId || !detail.value.departmentId) {
    message.error('父项目缺少下单公司或办事处，不能创建子项目')
    return
  }
  saving.value = true
  try {
    const parentSites = await ProjectsApi.getProjectSites(detail.value.id)
    const payload = {
      projectName: createChildForm.projectName,
      parentId: detail.value.id,
      orderOfficeCompanyId: detail.value.companyId,
      orderOfficeDepartmentId: detail.value.departmentId,
      sites: parentSites.map((site) => ({
        siteId: site.siteId,
        siteVersion: site.siteVersionSnapshot,
        primarySite: site.primarySite
      })),
      implementationLocation: parentSites.length ? undefined : detail.value.implementationLocation,
      creationReason: createChildForm.creationReason
    }
    await ProjectsApi.createProject(payload, createChildSubmission.keyFor(payload))
    message.success('下挂子项目成功（继承父模板与三维）')
    createChildSubmission.reset()
    createChildVisible.value = false
    await loadTreeData()
  } finally {
    saving.value = false
  }
}

// ============ 子树移动 ============
const moveVisible = ref(false)
const moveFormRef = ref()
const moveForm = reactive({ newParentId: undefined as number | undefined })
const moveRules = {
  newParentId: [{ required: true, message: '请选择目标父项目', trigger: 'change' }]
}
const openMove = () => {
  moveForm.newParentId = undefined
  moveVisible.value = true
}
const submitMove = async () => {
  await moveFormRef.value?.validate()
  saving.value = true
  try {
    await ProjectsApi.moveSubtree(detail.value!.id!, moveForm.newParentId!)
    message.success('子树移动成功')
    moveVisible.value = false
    await loadTreeData()
    await loadDetail()
  } catch {
    // 请求拦截器已展示业务错误；保留弹窗供用户修正目标父项目。
  } finally {
    saving.value = false
  }
}

// ============ 直接子项目权重 ============
const weightsVisible = ref(false)
const weightItems = ref<
  { projectId: number; projectCode?: string; projectName?: string; weight: number }[]
>([])
const weightTotal = computed(() =>
  weightItems.value.reduce((sum, item) => sum + Number(item.weight || 0), 0)
)
const openWeights = () => {
  const equalWeight = children.value.length ? 100 / children.value.length : 0
  weightItems.value = children.value.map((child) => ({
    projectId: child.id!,
    projectCode: child.projectCode,
    projectName: child.projectName,
    weight: Number(child.aggregationWeight ?? equalWeight)
  }))
  weightsVisible.value = true
}
const submitWeights = async () => {
  if (Math.abs(weightTotal.value - 100) > 0.001) {
    message.error(`直接子项目权重合计必须为 100%，当前为 ${weightTotal.value.toFixed(2)}%`)
    return
  }
  saving.value = true
  try {
    await ProjectsApi.updateChildWeights(
      detail.value!.id!,
      weightItems.value.map((item) => ({ projectId: item.projectId, weight: item.weight }))
    )
    message.success('直接子项目权重已整组生效')
    weightsVisible.value = false
    await loadTreeData()
  } catch {
    // 请求拦截器已展示业务错误；保留弹窗供用户修正权重。
  } finally {
    saving.value = false
  }
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
const loadTreeData = async () => {
  const id = Number(route.query.projectId)
  if (!id) return
  children.value = (await ProjectsApi.getChildren(id)) || []
  try {
    progress.value = await ProjectsApi.getProgress(id)
  } catch {
    progress.value = null
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadDetail(), loadInstances(), loadMembers(), loadTreeData()])
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
$primary: #1e3a5f;

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
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 4px;
}
.project-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.project-meta-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #6b7280;
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
  font-size: 11px;
  letter-spacing: 1px;
  color: #9ca3af;
  padding: 6px 10px 4px;
  font-weight: 600;
  text-transform: uppercase;
}
.rail-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: #374151;
  font-size: 13px;
  text-align: left;
  transition: all 0.15s ease;
  &:hover {
    background: #f3f4f6;
  }
  &--active {
    background: rgba(30, 58, 95, 0.08);
    color: #1e3a5f;
    font-weight: 600;
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
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
  color: #1f2937;
}
.stage-title {
  margin-right: 8px;
  font-weight: 600;
}
.preview-block {
  margin-bottom: 10px;
}
.preview-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}
.preview-line {
  padding: 2px 0;
  font-size: 13px;
}

@media (max-width: 1024px) {
  .detail-body {
    flex-direction: column;
  }
  .rail-wrap {
    flex: 1 1 auto;
  }
}
</style>
