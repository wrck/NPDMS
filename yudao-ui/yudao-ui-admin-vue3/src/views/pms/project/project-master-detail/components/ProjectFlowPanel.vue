<template>
  <!-- bodyStyle 去掉默认 overflow:hidden：吸附操作栏依赖祖先滚动容器（ElScrollbar）的 sticky 定位 -->
  <ContentWrap class="flow-panel" :body-style="{ padding: '10px' }">
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button link @click="reload">重新加载内容</el-button>
    </el-alert>
    <el-empty v-if="!selection" description="请在左侧交付流程中选择阶段或任务" />

    <template v-else-if="selection.kind === 'stage'">
      <!-- 设计稿 wb-head：阶段码徽标 + 名称 + 状态，元信息行（计划/进度/任务完成）为辅助层级 -->
      <div class="panel-header stage-head">
        <div class="stage-head-row">
          <span class="stage-code-chip">{{ selection.stageCode }}</span>
          <span class="stage-head-name">{{
            currentStage?.stageName || selection.stageCode
          }}</span>
          <dict-tag
            v-if="currentStage"
            :type="DICT_TYPE.PMS_PROJECT_STAGE_STATUS"
            :value="currentStage.stageStatus"
          />
        </div>
        <div v-if="currentStage" class="stage-head-meta">
          <span>计划 <b class="num">{{ stageTime(stageDetails?.planStartTime) }} ~ {{ stageTime(stageDetails?.planEndTime) }}</b></span>
          <span v-if="stageDetails?.actualStartTime || stageDetails?.actualEndTime">实际 <b class="num">{{ stageTime(stageDetails?.actualStartTime) }} ~ {{ stageTime(stageDetails?.actualEndTime) }}</b></span>
          <span v-if="stageDetails?.suggestedStartTime || stageDetails?.suggestedEndTime">建议 <b class="num">{{ stageTime(stageDetails?.suggestedStartTime) }} ~ {{ stageTime(stageDetails?.suggestedEndTime) }}</b></span>
          <span v-if="stageProgress">进度 <b class="num">{{ stageProgress.percent }}%</b></span>
          <span v-if="stageProgress">任务 <b class="num">{{ stageProgress.done }}/{{ stageProgress.total }}</b> 已完成</span>
          <span v-if="stageDetails?.deviationReason">偏差原因 {{ stageDetails.deviationReason }}</span>
        </div>
      </div>

      <!-- 无任务阶段整节隐藏（标题+表格+加载更多）；仅在加载中或加载失败时保留，失败时仍有重试入口 -->
      <template v-if="stageTaskSectionVisible">
        <div class="section-title task-business-heading">
          <span>任务</span>
          <span v-if="stageProgress" class="section-sub">已完成 {{ stageProgress.done }}/{{ stageProgress.total }}</span>
        </div>
        <el-alert v-if="tasks.error.value" :title="tasks.error.value" type="error" :closable="false">
          <el-button link @click="tasks.retry">重试任务加载</el-button>
        </el-alert>
        <!-- 列结构与 /pms/project-detail 的阶段任务表同口径：名称/编码/状态/进度/负责人；子任务列保留本页懒加载能力 -->
        <el-table
          v-loading="tasks.loading.value"
          :data="stageTree"
          row-key="taskId"
          :tree-props="{ children: 'children' }"
          default-expand-all
          :empty-text="tasks.error.value ? '任务尚未加载成功' : '本阶段暂无任务'"
        >
          <el-table-column prop="name" label="任务名称" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.name || `#${row.taskId}` }}</template>
          </el-table-column>
          <el-table-column prop="taskCode" label="编码" width="140" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <dict-tag :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS" :value="row.status ?? ''" />
            </template>
          </el-table-column>
          <el-table-column label="进度" width="140">
            <template #default="{ row }">
              <el-progress
                :percentage="row.progress ?? 0"
                :stroke-width="6"
                :show-text="false"
              />
              <span class="progress-text">{{ row.progress ?? 0 }}%</span>
            </template>
          </el-table-column>
          <el-table-column v-if="showResponsibilities" label="负责人" width="110">
            <template #default="{ row }">
              <UserTag v-if="row.assigneeUserId" :user-id="row.assigneeUserId" />
              <span v-else>未指派</span>
            </template>
          </el-table-column>
          <el-table-column label="子任务" width="150">
            <template #default="{ row }">
              <el-button
v-if="!tasks.childState(row.taskId)?.loaded || tasks.childState(row.taskId)?.cursor" link
                :loading="tasks.childState(row.taskId)?.loading" @click="tasks.loadChildren(row.taskId)">
                {{ tasks.childState(row.taskId)?.error ? '加载失败，重试' : tasks.childState(row.taskId)?.loaded ? '加载更多子任务' : '加载子任务' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-if="tasks.hasMore.value && !tasks.error.value" :loading="tasks.loading.value" @click="tasks.more">加载更多任务</el-button>
      </template>

      <!-- STAGE_NATIVE 阶段没有外部业务办理区：整节隐藏，阶段推进仍由底部操作栏承载 -->
      <template v-if="!stageBusinessNative">
        <div class="section-title task-business-heading">
          <span>阶段业务办理</span>
        </div>
        <StageBusinessPanel ref="stageBusinessRef" :project="project" :stage-code="selection.stageCode" @changed="handleStageBusinessChanged" @binding="stageBindingType = $event" />
      </template>

      <StageGateResultsPanel
v-if="showStageGates && hasStageGates" ref="stageGatesRef" :project-id="projectId"
        :stage-code="selection.stageCode" :project-version="project.version" :instances="instances" @changed="handleGateChanged" />

      <!-- 阶段元数据并入头部 meta 行（建议时间/偏差原因），准入/准出由门禁面板与侧栏「完成条件」承载，不再单设信息表 -->
      <template v-if="stageDeliverables.length">
        <div class="section-title section-title--sub">阶段交付件</div>
        <div class="task-deliverables">
          <div v-for="item in stageDeliverables" :key="item.id" class="deliverable-line">
            <el-tag size="small" :type="item.required ? 'danger' : 'info'">{{ item.required ? '必选' : '可选' }}</el-tag>
            <span class="deliverable-name">{{ item.name }}</span>
            <span class="deliverable-code">{{ item.deliverableCode }}</span>
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS" :value="item.status ?? ''" />
            <el-button v-if="item.id" link type="primary" @click="deliverableRef?.open(item.id)">提交与查看</el-button>
          </div>
        </div>
      </template>
    </template>

    <template v-else>
      <!-- 与阶段工作台同一头部格式：编码章 + 名称 + 状态；负责人/计划/进度为元信息行。
           数据源为 headTask（工作台任务优先，切换期间用实例视图任务补位），加载全程格式不变 -->
      <div class="panel-header stage-head">
        <div class="stage-head-row">
          <span v-if="headTask?.taskCode" class="stage-code-chip">{{ headTask.taskCode }}</span>
          <span class="stage-head-name">{{ headTask?.name || (selection?.taskId != null ? `#${selection.taskId}` : '') }}</span>
          <dict-tag
            v-if="headTask?.status"
            :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS"
            :value="headTask.status"
          />
        </div>
        <div v-if="headTask" class="stage-head-meta">
          <span>负责人 <UserTag v-if="headTask.assigneeUserId" :user-id="headTask.assigneeUserId" /><span v-else>未指派</span></span>
          <span>计划 <b class="num">{{ stageTime(headTask.planStartTime) }} ~ {{ stageTime(headTask.planEndTime) }}</b></span>
          <span v-if="headTask.actualStartTime || headTask.actualEndTime">实际 <b class="num">{{ stageTime(headTask.actualStartTime) }} ~ {{ stageTime(headTask.actualEndTime) }}</b></span>
          <span>进度 <b class="num">{{ headTask.progress ?? 0 }}%</b></span>
          <span v-if="headTask.businessLevelCode">层级 {{ headTask.businessLevelCode }}</span>
        </div>
      </div>
      <el-alert
        v-if="workbench?.recoverableError"
        type="error"
        :closable="false"
        show-icon
        :title="`执行区暂不可用：${workbench.recoverableError}`"
      />

      <!-- 任务办理区紧跟头部：业务办理是工作区主体，交付件清单依次其后；
           TASK_NATIVE 通用任务没有办理区，任务操作直接使用底部操作栏 -->
      <template v-if="workbench?.bindingType !== 'TASK_NATIVE'">
      <div class="section-title task-business-heading">
        <span>任务业务办理</span>
      </div>
      <TaskBusinessPanel
        v-if="businessBound && workbench?.task.version != null"
        ref="businessRef"
        :key="`flow-task-${workbench.task.taskId}`"
        :task-id="workbench.task.taskId"
        :task-version="workbench.task.version"
        :initial-project="project"
        :readonly="['DONE', 'CLOSED', 'CANCELLED'].includes(workbench.task.status || '')"
        @changed="handleBusinessChanged"
        @fact-version="handleBusinessFactChanged"
      />
      <TaskApprovalPanel
v-else-if="workbench?.bindingType === 'APPROVAL'" ref="approvalRef"
        :key="`approval-${workbench.task.taskId}`" :workbench="workbench" @changed="handleBusinessChanged" />
      <el-alert
        v-else-if="workbench?.bindingType === 'RESULT_SUBSCRIPTION'"
        :type="subscriptionSatisfied ? 'success' : 'info'"
        :closable="false" show-icon
        title="本任务订阅业务结果：满足订阅与节点完成条件后由系统推进；业务办理请使用对应业务入口。"
      >
        <div class="subscription-observations">
          <p v-if="!workbench?.resultSubscriptions?.length" class="subscription-empty">
            暂无订阅评估记录：订阅尚未评估或本轮尚未安装，请稍后刷新。
          </p>
          <div v-for="round in workbench?.resultSubscriptions || []" :key="round.executionId" class="subscription-round">
            <p v-if="round.roundEnded" class="subscription-note">
              本轮已结束；以下为证据复核状态，返工请使用正式返工入口。
            </p>
            <table class="subscription-table">
              <thead>
                <tr><th>订阅</th><th>评估状态</th><th>合格结果</th><th>缺失对象</th><th>已观察原因</th></tr>
              </thead>
              <tbody>
                <tr v-for="item in round.subscriptions" :key="item.subscriptionKey">
                  <td>
                    <span class="subscription-key">{{ item.subscriptionKey }}</span>
                    <span class="subscription-type">{{ item.resultType }}</span>
                  </td>
                  <td>{{ waitReasonLabel(item.waitReason) }}</td>
                  <td class="num">{{ item.eligible }}/{{ item.examined }}</td>
                  <td>
                    <span v-if="item.missingObjects.length" class="subscription-missing">
                      {{ item.missingObjects.join('、') }}
                    </span>
                    <span v-else>—</span>
                  </td>
                  <td>
                    <span v-if="item.reasons.length">{{ item.reasons.join('、') }}</span>
                    <span v-else>—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="subscription-note">
            订阅满足不等于节点已完成；完成仍按冻结条件判定。刷新只读取当前状态，不产生业务写入。
          </p>
        </div>
      </el-alert>
      <template v-else-if="workbench?.bindingType === 'PAGE'">
        <template v-if="pageEmbed">
          <p class="page-embed-hint">本任务业务在下方工作区直接办理，不跳离当前任务；办理完成后使用任务状态操作推进状态。</p>
          <component :is="pageEmbed" :key="`page-embed-${workbench.task.taskId}`" :project-id="projectId" />
        </template>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="本任务通过专用页面办理业务；路由为模板冻结的入口，仅用于跳转，完成仍按任务状态机与冻结规则判定。"
        >
          <span v-if="workbench?.trustedTargetRef" class="page-route">{{ workbench.trustedTargetRef }}</span>
        </el-alert>
      </template>
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="尚未取得可用的任务业务绑定；不能据此判断交付件或完成依据不适用，请重试或核对冻结契约。"
      />
      </template>

      <template v-if="workbench?.deliverables?.length">
        <div class="section-title"><span>交付件清单</span></div>
        <div class="task-deliverables">
          <div v-for="item in workbench.deliverables" :key="item.id" class="deliverable-line">
            <el-tag size="small" :type="item.required ? 'danger' : 'info'">{{ item.required ? '必选' : '可选' }}</el-tag>
            <span class="deliverable-name">{{ item.name }}</span>
            <span class="deliverable-code">{{ item.deliverableCode }}</span>
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS" :value="item.status ?? ''" />
            <el-button link type="primary" @click="deliverableRef?.open(item.id)">提交与查看</el-button>
          </div>
          <p class="deliverable-hint">文件或业务成果有效且模板条件满足时，自动满足交付要求。</p>
        </div>
      </template>
    </template>

    <!-- 任务/阶段操作底部吸附栏（设计稿 exec-actions）：操作统一收口于工作区底部并吸附滚动容器底缘。
         业务操作区（左）：业务视图的视图级操作按钮——本节刷新入口在此渲染，业务组件经
         BUSINESS_ACTION_BAR_TARGET teleport 收口到这里；状态操作区（右）：任务=状态推进与资料编辑，
         阶段=按冻结门禁推进（仅所选阶段即项目当前阶段时出现） -->
    <div v-if="selection && showActionBar" class="flow-action-bar">
      <div v-if="businessBarVisible" ref="businessBarTarget" class="flow-bar-business">
        <template v-if="selection.kind === 'stage'">
          <el-button :disabled="stageBusinessRef?.isBusy()" @click="stageBusinessRef?.refresh()">刷新业务结果</el-button>
        </template>
        <template v-else>
          <el-button
            v-if="businessBound && workbench?.task.version != null"
            :loading="businessRef?.loading" :disabled="!businessRef" @click="businessRef?.refresh()">刷新业务结果</el-button>
          <el-button v-else-if="workbench?.bindingType === 'APPROVAL'" :disabled="approvalRef?.isBusy()" @click="reload">刷新审批结果</el-button>
          <el-button v-else-if="workbench?.bindingType === 'RESULT_SUBSCRIPTION'" @click="reload">刷新任务状态</el-button>
          <el-button v-if="workbench?.bindingType === 'PAGE' && pageEmbed" @click="reload">刷新工作区</el-button>
          <el-button v-else-if="workbench?.bindingType === 'PAGE'" :disabled="!workbench?.trustedTargetRef" @click="openTaskPage">打开页面</el-button>
        </template>
      </div>
      <div class="flow-bar-spacer" />
      <template v-if="selection.kind === 'stage'">
        <el-alert v-if="advanceError" class="flow-bar-alert" :title="advanceError" type="error" :closable="false" show-icon />
        <p v-if="readiness" class="flow-bar-hint">{{ stageAdvanceHint }}</p>
        <el-button
          v-if="readiness?.nextStage"
          v-hasPermi="['pms:project:update']"
          type="primary"
          :loading="advancing"
          :disabled="!readiness.advanceAllowed"
          @click="advanceStage"
        >推进至 {{ readiness.nextStage }}{{ nextStageName ? ` ${nextStageName}` : '' }}</el-button>
      </template>
      <template v-else-if="workbench">
        <TaskStateActions
          ref="stateActionsRef" :workbench="workbench" :business-bound="businessBound"
          :business-fact-version="businessFactVersion" :before-action="requestLeave" @changed="handleBusinessChanged">
          <ProjectTaskDetailsEditor ref="detailsRef" :workbench="workbench" :before-action="requestLeave" @changed="handleBusinessChanged" />
        </TaskStateActions>
      </template>
    </div>
  </ContentWrap>
  <ProjectDeliverableDialog ref="deliverableRef" :project-id="projectId" @changed="handleDeliverableChanged" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
import type { Component } from 'vue'
import { onBeforeRouteUpdate, useRouter } from 'vue-router'
import { generateUUID } from '@/utils'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as TaskWorkbenchApi from '@/api/pms/project/task-workbench'
import * as BusinessApi from '@/api/pms/project/task-business'
import { prefetchTaskView, taskBusinessContextKey } from '../taskViewPrefetch'
import {
  advanceProjectStage,
  getProjectStageAdvanceReadiness,
  type ProjectMasterVO,
  type ProjectInstancesVO,
  type ProjectStageAdvanceReadinessVO
} from '@/api/pms/project/projects'
import type {
  ProjectWorkspace,
  StageTaskNavigation,
  TaskDetail,
  TaskWorkbench
} from '@/api/pms/project/task-workbench'
import TaskBusinessPanel from './TaskBusinessPanel.vue'
import type { ProjectFlowSelection } from './project-flow'
import { taskForest, useFlowTaskPaging } from '@/views/pms/project/inheritance/detail/flowTaskPaging'
import { BUSINESS_ACTION_BAR_TARGET } from '@/components/BusinessView/businessActionBar'
import StageBusinessPanel from '@/views/pms/project/inheritance/detail/StageBusinessPanel.vue'
import StageGateResultsPanel from './StageGateResultsPanel.vue'
import TaskStateActions from '@/views/pms/project/inheritance/detail/TaskStateActions.vue'
import TaskApprovalPanel from '@/views/pms/project/inheritance/detail/TaskApprovalPanel.vue'
import ProjectTaskDetailsEditor from './ProjectTaskDetailsEditor.vue'
import ProjectDeliverableDialog from './ProjectDeliverableDialog.vue'
import UserTag from '@/components/UserTag/index.vue'
import ProjectArrivalReceiptPanel from './ProjectArrivalReceiptPanel.vue'
import InstallationWorkbench from '@/views/pms/engineering/installation/index.vue'
import ConfigurationWorkbench from '@/views/pms/engineering/configuration/index.vue'
import JointTestWorkbench from '@/views/pms/engineering/joint-test/index.vue'
import TrainingWorkbench from '@/views/pms/engineering/training/index.vue'
import CustomerContactsWorkbench from '@/views/pms/customer/contacts/index.vue'

defineOptions({ name: 'ProjectFlowPanel' })

const props = withDefaults(defineProps<{
  projectId: number
  project: ProjectMasterVO
  instances?: ProjectInstancesVO | null
  selection?: ProjectFlowSelection
  showResponsibilities?: boolean
  /** 页面工作区把门禁面板放到右栏时关闭内嵌渲染，避免同一门禁出现两份审批入口 */
  showStageGates?: boolean
}>(), { showResponsibilities: false, showStageGates: true })

const workspace = ref<ProjectWorkspace>()
const tasks = useFlowTaskPaging(() => props.projectId, () => props.selection?.stageCode)
const stageTree = computed(() => taskForest(tasks.rows.value, props.selection?.stageCode || ''))
// 无任务阶段不渲染任务表格（含标题与加载更多）；加载中或失败时保留，失败仍有重试入口
const stageTaskSectionVisible = computed(
  () => stageTree.value.length > 0 || tasks.loading.value || !!tasks.error.value
)
const stageDetails = computed(() => props.instances?.stages.find(stage => stage.stageCode === props.selection?.stageCode))
// 阶段进度：DONE 任务比例（与推进轨/头部统计同口径；实例视图任务无 progress 字段），无任务时不展示
const stageProgress = computed(() => {
  const stageCode = props.selection?.stageCode
  if (props.selection?.kind !== 'stage' || !stageCode) return undefined
  const list = (props.instances?.tasks || []).filter((task) => task.stageCode === stageCode)
  if (!list.length) return undefined
  const done = list.filter((task) => task.status === 'DONE').length
  return { done, total: list.length, percent: Math.round((done / list.length) * 100) }
})
const stageTime = (value?: string) => value ? formatDate(value) : '—'
// 设计稿「阶段交付件」：本阶段全部交付件（含任务归属），只读清单 + 提交入口
const stageDeliverables = computed(() =>
  props.selection?.kind === 'stage'
    ? (props.instances?.deliverables || []).filter(
        (item) => item.stageCode === props.selection?.stageCode
      )
    : []
)
const hasStageGates = computed(() => props.instances?.gates.some(gate => gate.stageCode === props.selection?.stageCode))
const workbench = ref<TaskWorkbench>()
// 明细切换期间头部用导航选中的任务快照即时填充：工作台未返回时不退回占位标题，避免两套格式跳变；
// 导航快照（TaskNode）没有实际时间字段，实际起止此时隐藏，工作台返回后补齐
const headTask = computed<TaskDetail | undefined>(
  () => workbench.value?.task ?? (props.selection?.task as TaskDetail | undefined)
)
const loadError = ref('')
const businessRef = ref<InstanceType<typeof TaskBusinessPanel>>()
const stageBusinessRef = ref<InstanceType<typeof StageBusinessPanel>>()
// 阶段业务绑定类型由 StageBusinessPanel 装载后上报；STAGE_NATIVE 阶段不渲染业务办理区
const stageBindingType = ref<string>()
const stageBusinessNative = computed(() => stageBindingType.value === 'STAGE_NATIVE')
const stageGatesRef = ref<InstanceType<typeof StageGateResultsPanel>>()
const stateActionsRef = ref<InstanceType<typeof TaskStateActions>>()
const approvalRef = ref<InstanceType<typeof TaskApprovalPanel>>()
const detailsRef = ref<InstanceType<typeof ProjectTaskDetailsEditor>>()
const deliverableRef = ref<InstanceType<typeof ProjectDeliverableDialog>>()
const message = useMessage()
// 阶段推进准备度：底部吸附栏推进操作的数据源；只读评估 + 显式推进，语义与后端冻结门禁一致
const readiness = ref<ProjectStageAdvanceReadinessVO>()
const advancing = ref(false)
const advanceError = ref('')
const loadReadiness = async () => {
  const projectId = props.projectId
  // 只读评估在共享库锁冲突时会瞬时失败，短暂重试；仍失败才隐藏推进操作，不阻塞工作区
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const result = await getProjectStageAdvanceReadiness(projectId)
      if (projectId === props.projectId) readiness.value = result
      return
    } catch {
      if (attempt < 2) await new Promise((resolve) => setTimeout(resolve, 400))
    }
  }
  if (projectId === props.projectId) readiness.value = undefined
}
// 原因码提示沿用阶段进展面板已登记的展示文案，不新增业务语义
const guidanceLabel = (code?: string | null) => ({
  S0_PRIMARY_MANAGERS_REQUIRED: '请先完成主责服务经理和主责项目经理指派，S0不能直接准出。',
  S0_PRIMARY_SERVICE_MANAGER_REQUIRED: '尚未指派有效主责服务经理，请先完成服务经理指派。',
  S0_PRIMARY_PROJECT_MANAGER_REQUIRED: '尚未指派有效主责项目经理，请先完成项目经理指派。',
  S0_ASSIGNMENT_STATUS_NOT_ASSIGNED: '双主责指派事实与项目指派状态不一致，请重新核对成员指派。',
  TERMINAL: '已到达模板的最后实际阶段。阶段结束不等于项目闭环，请按闭环条件申请审批。'
}[code || ''] || '')
const nextStageName = computed(() => {
  const code = readiness.value?.nextStage
  return code ? props.instances?.stages.find(stage => stage.stageCode === code)?.name : undefined
})
const stageAdvanceHint = computed(() => {
  const current = readiness.value
  if (!current) return ''
  if (!current.nextStage) return guidanceLabel(current.guidance) || '已到达模板的最后实际阶段'
  if (current.advanceAllowed) return '出口门禁已全部满足，可推进至下一阶段。'
  const guidance = guidanceLabel(current.guidance)
  if (guidance) return guidance
  const satisfied = current.gates.filter(gate => gate.satisfied).length
  return `出口门禁 ${satisfied}/${current.gates.length} 满足；全部通过后可推进。`
})
const advanceStage = async () => {
  const snapshot = readiness.value
  if (!snapshot?.advanceAllowed || advancing.value) return
  advancing.value = true
  advanceError.value = ''
  try {
    await advanceProjectStage(props.projectId, snapshot, generateUUID())
    message.success('阶段已按冻结关系推进')
    emit('changed')
  } catch (failure: any) {
    advanceError.value = failure?.response?.data?.msg || failure?.msg || '阶段未推进，已保存的业务不受影响，可重新检查'
  } finally {
    advancing.value = false
    await loadReadiness()
  }
}
// 吸附操作栏显隐：任务视图随工作台就绪——业务绑定任务的业务操作与任务状态操作都在栏内承载；
// 阶段视图在业务办理区存在（非 STAGE_NATIVE）或所选阶段即项目当前阶段（推进操作）时承载
const showActionBar = computed(() => {
  const selection = props.selection
  if (!selection) return false
  if (selection.kind === 'stage') {
    return !stageBusinessNative.value
      || (!!readiness.value && readiness.value.currentStage === selection.stageCode)
  }
  return !!workbench.value
    && (workbench.value.bindingType !== 'TASK_NATIVE' || !!workbench.value.allowedActions?.length)
})
// 业务操作区显隐：与业务办理区的渲染条件一致（区在则操作在），避免渲染空操作区
const businessBarVisible = computed(() => {
  const selection = props.selection
  if (!selection) return false
  if (selection.kind === 'stage') return !stageBusinessNative.value
  return !!workbench.value && workbench.value.bindingType !== 'TASK_NATIVE'
})
// 业务组件的视图级操作按钮经此挂载点 teleport 收口到底部操作栏（businessActionBar.ts 协议）
const businessBarTarget = ref<HTMLElement>()
provide(BUSINESS_ACTION_BAR_TARGET, businessBarTarget)
const handleDeliverableChanged = async () => { await refreshSummary(); emit('changed') }
const emit = defineEmits<{ changed: [] }>()
// Await the existing Owner leave contract before the parent changes selection/unmounts content.
// https://vuejs.org/guide/essentials/template-refs.html#ref-on-component
const requestLeave = async () => !advancing.value && !stateActionsRef.value?.isBusy()
  && detailsRef.value?.requestLeave() !== false
  && (await businessRef.value?.requestLeave()) !== false
  && (await stageBusinessRef.value?.requestLeave()) !== false
  && (await approvalRef.value?.requestLeave()) !== false
  && (await stageGatesRef.value?.requestLeave()) !== false
// BusinessViewHost already guards route leave; only reused-route project changes need this guard.
onBeforeRouteUpdate((to, from) => to.query.projectId === from.query.projectId || requestLeave())
const businessFactVersion = ref<string>()

// 订阅观察只读展示：等待原因只引用后端已登记的评估记录，缺失对象与原因码原样呈现。
const WAIT_REASON_LABELS: Record<string, string> = {
  EVALUATION_PENDING: '待评估：尚无可用评估结论',
  EVALUATION_OUTDATED: '评估待更新：旧结论不作为当前依据',
  EVALUATION_SCANNING: '正在扫描业务结果',
  NO_QUALIFIED_RESULT: '尚无合格结果',
  EXPECTED_OBJECTS_MISSING: '仍有预期对象缺少合格结果',
  RESULT_AMBIGUOUS: '存在多个合格结果，需要唯一结论',
  RESULT_SOURCE_UNAVAILABLE: '结果来源暂不可用',
  EVIDENCE_SATISFIED: '订阅证据已满足'
}
const waitReasonLabel = (reason: string) => WAIT_REASON_LABELS[reason] ?? reason
const subscriptionSatisfied = computed(() => {
  const rounds = workbench.value?.resultSubscriptions
  return !!rounds?.length
    && rounds.every((round) => round.subscriptions.length > 0
      && round.subscriptions.every((item) => item.waitReason === 'EVIDENCE_SATISFIED'))
})

let sequence = 0
const current = (value: number) => value === sequence

const currentStage = computed<StageTaskNavigation | undefined>(() =>
  workspace.value?.stageTaskNavigation.find(
    (stage) => stage.stageCode === props.selection?.stageCode
  )
)
const businessBound = computed(() =>
  ['BUSINESS_OBJECT', 'BUSINESS_COMPONENT', 'DYNAMIC_FORM', 'COMPOSITE'].includes(
    workbench.value?.bindingType || ''
  )
)
const router = useRouter()
const openTaskPage = () => { const route = workbench.value?.trustedTargetRef; if (route) void router.push(route) }

// PAGE 绑定任务的内嵌业务组件：key 为模板冻结的 routePath（trustedTargetRef）。
// 业务操作直接在任务工作区完成，不再跳离当前界面；未映射的 routePath 保留「打开页面」跳转兜底。
const pageEmbedComponents: Record<string, Component> = {
  '/pms/engineering/execution/imp-arrival': ProjectArrivalReceiptPanel,
  '/pms/engineering/execution/imp-installation': InstallationWorkbench,
  '/pms/engineering/execution/imp-configuration': ConfigurationWorkbench,
  '/pms/engineering/execution/imp-joint-test': JointTestWorkbench,
  '/pms/imp-training': TrainingWorkbench,
  '/customer-asset/customer-contact': CustomerContactsWorkbench
}
const pageEmbed = computed(() =>
  workbench.value?.bindingType === 'PAGE'
    ? pageEmbedComponents[workbench.value.trustedTargetRef || '']
    : undefined
)

const loadWorkspace = async (token: number) => {
  const result = await TaskWorkbenchApi.getProjectWorkspace(props.projectId)
  if (current(token)) workspace.value = result
}

const loadTaskWorkbench = async (taskId: number | string, token: number) => {
  const result = await TaskWorkbenchApi.getTaskWorkbench(taskId)
  if (current(token)) workbench.value = result
}

const load = async () => {
  const token = ++sequence
  loadError.value = ''
  workbench.value = undefined
  businessFactVersion.value = undefined
  advanceError.value = ''
  stageBindingType.value = undefined
  tasks.reset()
  const selection = props.selection
  if (!selection) return
  try {
    if (selection.kind === 'stage') {
      // 准备度评估不阻塞主内容：就绪前吸附操作栏不渲染，门禁明细由完成条件面板承载
      void loadReadiness()
      await Promise.all([loadWorkspace(token), tasks.reload()])
    } else if (selection.taskId != null) {
      const taskId = selection.taskId
      // 点击时刻并行预取业务上下文：TaskBusinessPanel 挂载后直接命中，省去第二波串行等待；
      // 非业务绑定任务的结果无人消费，预取 TTL 到期自动失效。
      prefetchTaskView(taskBusinessContextKey(taskId), () => BusinessApi.getTaskBusinessContext(taskId))
      // Both endpoints authorize independently. Publish together so a failed
      // workspace read cannot expose a partially loaded task action panel.
      const [nextWorkspace, nextWorkbench] = await Promise.all([
        TaskWorkbenchApi.getProjectWorkspace(props.projectId),
        TaskWorkbenchApi.getTaskWorkbench(selection.taskId)
      ])
      if (!current(token)) return
      workspace.value = nextWorkspace
      workbench.value = nextWorkbench
    }
  } catch {
    if (current(token)) loadError.value = '内容加载失败，请重新加载。'
  }
}
const reload = async () => { if (await requestLeave()) await load() }
const handleBusinessChanged = async () => {
  if (props.selection?.taskId == null) return
  try { await loadTaskWorkbench(props.selection.taskId, sequence) }
  catch { loadError.value = '业务已保存，任务详情刷新失败，请重新加载。' }
  emit('changed')
}
const handleStageBusinessChanged = async () => { await refreshSummary(); await stageGatesRef.value?.refresh(); emit('changed') }
const handleGateChanged = async () => { await refreshSummary(); emit('changed') }
const handleBusinessFactChanged = (version?: string) => {
  if (!version) return
  const previous = businessFactVersion.value
  businessFactVersion.value = version
  if (previous && previous !== version) void handleBusinessChanged()
}
const refreshSummary = async () => {
  const token = sequence
  if (props.selection?.kind === 'stage') void loadReadiness()
  try {
    await loadWorkspace(token)
    if (current(token) && props.selection?.kind === 'stage') await tasks.reload()
  } catch {
    if (current(token)) loadError.value = '概要刷新失败，已加载内容保留，请重新加载。'
  }
}
defineExpose({ requestLeave, reload, refreshSummary })

watch(() => [props.projectId, props.selection?.kind, props.selection?.stageCode, props.selection?.taskId], load, { immediate: true })
onBeforeUnmount(() => {
  ++sequence
  tasks.reset()
})
</script>

<style scoped lang="scss">
.flow-panel {
  min-width: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.section-title {
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* 任务/阶段操作底部吸附栏（设计稿 exec-actions 收口）：滚动时吸附在滚动容器（ElScrollbar）底缘，
   负边距抵消卡体内边距使栏体通栏贴边；白底描边与三栏白底统一 */
/* 吸附栏的 sticky 必须相对真正的滚动容器（ElScrollbar）定位：
   卡体和卡片根自身任何非 visible 的 overflow 都会截断 sticky 链路，故显式放开 */
.flow-panel,
.flow-panel :deep(.el-card__body) {
  overflow: visible;
}

.flow-action-bar {
  position: sticky;
  bottom: 0;
  z-index: 3;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 16px -10px -10px;
  padding: 12px 10px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.flow-action-bar :deep(.task-state-actions) {
  margin: 0;
}

/* 业务操作区：本节刷新入口 + 各业务视图 teleport 收口的视图级操作按钮；与状态操作区左右分区 */
.flow-bar-business {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.flow-bar-business :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 计划卡脚注等说明文字收口进栏后不再拉伸占位；原位顶部留白的按钮进栏后回归对齐 */
.flow-bar-business :deep(.footer-note) {
  flex: none;
}

.flow-bar-business :deep(.mt-15px) {
  margin-top: 0;
}

.flow-bar-spacer {
  flex: 1 1 12px;
}

.flow-bar-hint {
  margin: 0;
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
}

.flow-bar-alert {
  width: 100%;
}

/* 设计稿 wb-head：阶段码徽标 + 名称 + 状态；头部为两行（标题行 + 元信息行） */
.stage-head {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.stage-head-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.stage-head-meta {
  display: flex;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--el-text-color-regular);

  span {
    padding: 0 12px;
    border-left: 1px solid var(--el-border-color-lighter);
    line-height: 1.6;

    &:first-child {
      padding-left: 0;
      border-left: none;
    }
  }

  b {
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

.section-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.section-title--sub {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.stage-code-chip {
  flex: none;
  padding: 2px 8px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 4px;
}

.stage-head-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-business-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

/* 订阅观察：紧凑表格 + 说明，全部只读 */
.subscription-observations {
  margin-top: 6px;
  font-size: 12.5px;
}
.subscription-empty,
.subscription-note {
  margin: 4px 0;
  color: var(--el-text-color-secondary);
}
.subscription-table {
  width: 100%;
  margin: 6px 0;
  border-collapse: collapse;
}
.subscription-table th,
.subscription-table td {
  padding: 4px 8px;
  text-align: left;
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-wrap: anywhere;
}
.subscription-table th {
  font-weight: 500;
  color: var(--el-text-color-secondary);
}
.subscription-key {
  margin-right: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}
.task-deliverables {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
.deliverable-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.deliverable-name {
  font-size: 13px;
}
.deliverable-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.deliverable-hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.page-route {
  margin-left: 12px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  /* 卡体不再裁剪溢出（吸附栏需要 sticky 祖先链），长路由就地折行 */
  overflow-wrap: anywhere;
}

/* PAGE 绑定内嵌工作区：提示行（刷新入口已收口到操作栏），业务组件占满面板宽度 */
.page-embed-hint {
  margin: 12px 0 8px;
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
}
.subscription-type {
  color: var(--el-text-color-secondary);
  font-size: 11.5px;
}
.subscription-missing {
  color: var(--el-color-warning);
}
.subscription-table .num {
  font-variant-numeric: tabular-nums;
}

.task-owner {
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
}

/* 阶段进度：进度条 + 等宽百分比文本（与 /pms/project-detail 阶段进度同款） */
.progress-text {
  flex: none;
  margin-left: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.stage-business {
  margin-top: 4px;
}

@media (width <= 767px) {
  .panel-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
