<template>
  <main
    class="inspection-detail inspection-page"
    data-testid="inspection-task-detail"
    v-loading="loading"
  >
    <div class="page-heading">
      <div>
        <el-button link @click="router.push('/pms/inspection/tasks')">返回任务列表</el-button>
        <h1>{{ task?.taskName || issue?.taskName || '巡检任务详情' }}</h1>
        <p class="help-text">{{ task?.taskCode || issue?.taskCode || taskId }}</p>
      </div>
      <div class="actions">
        <el-tag v-if="task">{{ statusLabel(task.statusCode) }}</el-tag>
        <el-button :disabled="busy || !!pending || onlineBusy || attachmentBusy" @click="reload"
          >刷新</el-button
        >
      </div>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
    <el-alert
      v-if="writeError"
      :title="writeError"
      :type="pending ? 'warning' : 'error'"
      :closable="false"
      show-icon
    >
      <template v-if="pending" #default>
        <el-button :loading="busy" type="primary" @click="retryPending"
          >重试原请求并确认结果</el-button
        >
      </template>
    </el-alert>
    <InspectionSteps
      v-if="task"
      :active="activeStepIndex"
      :reached="reachedStepIndex"
      @select="selectStep"
    />
    <ContentWrap v-if="task" :body-style="{}">
      <div class="inspection-card__header">
        <h2><Icon icon="ep:location" /> 项目信息</h2>
        <span class="help-text"
          >{{ task.projectCode || '项目资料' }} · {{ task.devices.length }} 台设备</span
        >
      </div>
      <dl class="project-context">
        <div
          ><dt>项目名称</dt><dd>{{ task.projectName || '—' }}</dd></div
        >
        <div
          ><dt>设备序列号</dt
          ><dd class="serial-value">{{
            task.devices
              .map((device) => device.serialNumber)
              .filter(Boolean)
              .join('、') || '—'
          }}</dd></div
        >
        <div
          ><dt>产品型号</dt
          ><dd>{{
            [...new Set(task.devices.map((device) => device.productModel).filter(Boolean))].join(
              '、'
            ) || '—'
          }}</dd></div
        >
        <div
          ><dt>客户名称</dt><dd>{{ task.customerName || '—' }}</dd></div
        >
        <div
          ><dt>巡检负责人</dt><dd>{{ task.ownerName || '未提供姓名' }}</dd></div
        >
        <div
          ><dt>巡检方式</dt><dd>{{ task.mode === 'OFFLINE' ? '离线巡检' : '在线巡检' }}</dd></div
        >
        <div
          ><dt>计划时间</dt
          ><dd>{{ task.plannedAt ? formatDate(task.plannedAt) : '未设置' }}</dd></div
        >
        <div
          ><dt>创建时间</dt><dd>{{ task.createTime ? formatDate(task.createTime) : '—' }}</dd></div
        >
      </dl>
    </ContentWrap>
    <p v-else-if="issue" class="help-text">当前显示你有权办理的问题及处理记录。</p>

    <template v-if="task">
      <ContentWrap v-show="activeStep === 'prepare'" :body-style="{}">
        <div class="inspection-card__header"
          ><h2><Icon icon="ep:connection" /> 巡检准备信息</h2></div
        >
        <el-alert
          v-if="task.currentAttemptId"
          title="当前尝试的规则版本已冻结；修改准备资料只影响下一次明确新建的尝试。"
          type="info"
          :closable="false"
          class="mb-16px"
        />
        <el-form
          ref="preparationForm"
          :model="preparation"
          :rules="preparationRules"
          label-position="top"
          :disabled="!canPrepare"
          @submit.prevent="savePreparation"
        >
          <div class="form-grid">
            <el-form-item label="任务名称" prop="taskName"
              ><el-input v-model="preparation.taskName" maxlength="200"
            /></el-form-item>
            <el-form-item label="计划时间"
              ><el-date-picker
                v-model="preparation.plannedAt"
                type="datetime"
                value-format="x"
                placeholder="选择计划时间"
                clearable
            /></el-form-item>
          </div>
          <h3 class="inspection-section-title">巡检方式选择</h3>
          <el-form-item>
            <el-radio-group v-model="preparation.mode" class="mode-options">
              <el-radio value="ONLINE"
                ><Icon icon="ep:connection" /> 在线巡检 — 登录设备执行命令</el-radio
              >
              <el-radio value="OFFLINE"
                ><Icon icon="ep:folder-opened" /> 离线巡检 — 上传日志并解析</el-radio
              >
            </el-radio-group>
          </el-form-item>
          <el-form-item label="巡检背景" prop="background"
            ><el-input
              v-model="preparation.background"
              type="textarea"
              :rows="3"
              maxlength="8000"
              show-word-limit
          /></el-form-item>
        </el-form>
        <h3 class="inspection-section-title">巡检设备</h3>
        <el-table :data="task.devices" :row-key="(row) => String(row.deviceId)" class="mb-16px">
          <el-table-column prop="serialNumber" label="序列号" min-width="180" />
          <el-table-column prop="deviceName" label="设备" min-width="160" />
          <el-table-column prop="productModel" label="型号" min-width="130" />
          <el-table-column prop="productTypeName" label="产品类型" min-width="140" />
          <el-table-column label="已选检测项" width="120"
            ><template #default="{ row }">{{
              selectedRules[String(row.deviceId)]?.length || 0
            }}</template></el-table-column
          >
        </el-table>
        <div class="section-heading"
          ><h3 class="inspection-section-title">巡检规则选择</h3>
          <el-select v-model="preparationDeviceId" class="device-select" @change="loadSelectable">
            <el-option
              v-for="device in task.devices"
              :key="String(device.deviceId)"
              :value="String(device.deviceId)"
              :label="[device.serialNumber, device.deviceName].filter(Boolean).join(' · ')"
            />
          </el-select>
        </div>
        <el-alert v-if="rulesError" :title="rulesError" type="error" :closable="false">
          <template #default
            ><el-button link @click="loadSelectable">重新加载规则</el-button></template
          >
        </el-alert>
        <div v-if="unavailableSelections.length" class="unavailable-rules">
          <p
            >先前选择中有
            {{ unavailableSelections.length }} 项已不在当前可选规则中，请核对其发布状态。</p
          >
          <el-tag
            v-for="id in unavailableSelections"
            :key="id"
            :closable="canPrepare"
            @close="removeSelection(id)"
            >修订 {{ id }}</el-tag
          >
        </div>
        <el-checkbox-group
          v-model="currentSelections"
          :disabled="!canPrepare || rulesLoading || !!rulesError"
          class="rule-options"
          v-loading="rulesLoading"
        >
          <el-checkbox
            v-for="rule in selectable"
            :key="String(rule.revisionId)"
            :value="String(rule.revisionId)"
            border
          >
            <span>{{ rule.inspectionItem }} · R{{ rule.revisionNo }}</span>
            <span class="help-text"
              >{{ rule.detectionId }} · {{ rule.categoryName }} · {{ rule.severityName }}</span
            >
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="!rulesLoading && !rulesError && !selectable.length"
          description="该设备暂无适用的已发布规则，可在下方提交规则缺失反馈"
          :image-size="70"
        />
        <div class="inspection-footer">
          <el-button @click="router.push('/pms/inspection/tasks')">返回任务列表</el-button>
          <el-button
            v-hasPermi="['pms:inspection-task:prepare']"
            :disabled="!canPrepare"
            :loading="busy"
            @click="savePreparation"
            >保存准备资料</el-button
          >
          <el-button
            v-hasPermi="['pms:inspection-task:execute']"
            type="primary"
            :disabled="!canStart"
            @click="startAttempt"
          >
            {{
              task.currentAttemptId
                ? '重新开始巡检'
                : `下一步 · ${preparation.mode === 'ONLINE' ? '在线' : '离线'}巡检执行`
            }}
          </el-button>
        </div>
        <p class="help-text"
          >保存后可离开并继续办理。每台设备至少选择一个当前已发布且适配的规则后，才能开始执行。</p
        >
      </ContentWrap>
      <ContentWrap v-show="activeStep === 'prepare'" :body-style="{}">
        <el-collapse>
          <el-collapse-item title="规则缺失反馈" name="feedback">
            <el-form label-position="top" :disabled="writeDisabled">
              <el-form-item label="缺失的检测内容与设备情况">
                <el-input
                  v-model="missingDescription"
                  type="textarea"
                  :rows="3"
                  maxlength="8000"
                  show-word-limit
                  placeholder="反馈关联上方当前设备；不适用于执行任务的规则不会强行加入命令清单。"
                />
              </el-form-item>
              <el-button
                v-hasPermi="['pms:inspection-task:prepare']"
                :disabled="writeDisabled || !missingDescription.trim()"
                @click="reportMissing"
              >
                提交规则缺失反馈
              </el-button>
            </el-form>
            <el-table :data="task.ruleFeedback || []" empty-text="暂无规则缺失反馈" class="mt-16px">
              <el-table-column prop="deviceId" label="设备ID" width="170" />
              <el-table-column prop="description" label="反馈内容" min-width="280" />
              <el-table-column label="反馈人" min-width="140">
                <template #default="{ row }">{{
                  personLabel({ name: row.reportedName, id: row.reportedBy })
                }}</template>
              </el-table-column>
              <el-table-column label="反馈时间" width="180"
                ><template #default="{ row }">{{
                  formatDate(row.reportedAt)
                }}</template></el-table-column
              >
            </el-table>
          </el-collapse-item>
          <el-collapse-item title="巡检附件（可选）" name="attachments">
            <InspectionAttachmentPanel
              :key="taskId"
              :task="task"
              :disabled="attachmentDisabled"
              @busy-changed="attachmentBusy = $event"
              @draft-changed="drafts.attachment = $event"
            />
          </el-collapse-item>
        </el-collapse>
      </ContentWrap>

      <ContentWrap v-show="activeStep === 'execute'" :body-style="{}">
        <el-alert v-if="commandsError" :title="commandsError" type="error" :closable="false">
          <template #default
            ><el-button link @click="loadCommands">重新加载命令</el-button></template
          >
        </el-alert>
        <InspectionOnlinePanel
          :key="taskId"
          :task="task"
          :commands="commands"
          :selected-device-id="executionDeviceId"
          :disabled="onlineDisabled || commandsLoading || !!commandsError"
          @device-change="selectExecutionDevice"
          @changed="load(false)"
          @busy-changed="onlineBusy = $event"
          @draft-changed="drafts.online = $event"
        />
        <InspectionExecutionPanel
          :key="executionPanelKey"
          :task="task"
          :commands="commands"
          :selected-device-id="executionDeviceId"
          :disabled="!canExecute || commandsLoading || !!commandsError"
          @device-change="selectExecutionDevice"
          @upload="uploadLog"
          @refresh="refreshLog"
          @finish="finishExecution"
          @download-log="downloadLog"
          @draft-changed="drafts.execution = $event"
        />
        <div v-if="pendingLogs.length && canExecute" class="pending-logs">
          <h3>停止采用未完成解析</h3>
          <p class="help-text">保留原日志和解析记录，可随后补传或新建尝试。</p>
          <el-button v-for="row in pendingLogs" :key="String(row.id)" @click="abandonLog(row)"
            >停止采用 {{ row.fileName || row.id }}</el-button
          >
        </div>
      </ContentWrap>

      <ContentWrap v-show="activeStep === 'report'" :body-style="{}">
        <InspectionReportPanel
          :task="task"
          :disabled="writeDisabled"
          @generate="generate"
          @submit-manual="submitManual"
          @download="downloadReport"
          @draft-changed="drafts.report = $event"
        />
        <el-divider />
        <InspectionAnnotationPanel
          :task="task"
          :disabled="writeDisabled"
          @annotate="annotate"
          @complete="completeAnnotations"
          @draft-changed="drafts.annotation = $event"
        />
      </ContentWrap>

      <ContentWrap v-show="activeStep === 'issues'" :body-style="{}">
        <div class="inspection-card__header"
          ><h2><Icon icon="ep:tickets" /> 待办清单跟踪信息</h2
          ><span class="help-text">仅展示确认跟踪的检出问题</span></div
        >
        <el-table
          :data="task.issues"
          :row-key="(row) => String(row.id)"
          empty-text="当前没有跟踪事项"
        >
          <el-table-column prop="id" label="问题编号" min-width="150" />
          <el-table-column prop="description" label="问题" min-width="230" show-overflow-tooltip />
          <el-table-column label="责任人" min-width="140">
            <template #default="{ row }">{{
              personLabel({ name: row.responsibleName, id: row.responsibleUserId })
            }}</template>
          </el-table-column>
          <el-table-column label="期限" min-width="160"
            ><template #default="{ row }">{{
              row.dueAt ? formatDate(row.dueAt) : '—'
            }}</template></el-table-column
          >
          <el-table-column label="状态" width="130"
            ><template #default="{ row }">{{
              issueStatuses[row.statusCode] || row.statusCode
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="100"
            ><template #default="{ row }">
              <el-button
                link
                type="primary"
                v-hasPermi="['pms:service-issue:query']"
                @click="openIssue(row.id)"
                >处理记录</el-button
              >
            </template></el-table-column
          >
        </el-table>
      </ContentWrap>

      <ContentWrap v-show="activeStep === 'archive'" :body-style="{}">
        <div class="inspection-card__header"
          ><h2><Icon icon="ep:circle-check" /> 闭环汇总</h2
          ><el-tag :type="task.statusCode === 'ARCHIVED' ? 'success' : 'info'">{{
            statusLabel(task.statusCode)
          }}</el-tag></div
        >
        <h3 class="inspection-section-title">已完成待办项</h3>
        <el-table :data="closedIssues" empty-text="暂无已完成待办项">
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column
            prop="description"
            label="待办项"
            min-width="280"
            show-overflow-tooltip
          />
          <el-table-column label="严重程度" width="120"
            ><template #default="{ row }">{{
              severityLabels[row.severityCode] || row.severityCode || '—'
            }}</template></el-table-column
          >
          <el-table-column label="状态" width="120"
            ><template #default><el-tag type="success">已闭环</el-tag></template></el-table-column
          >
        </el-table>
        <h3 class="inspection-section-title">未完成待办项</h3>
        <el-table :data="openIssues" empty-text="无未完成待办项">
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column
            prop="description"
            label="待办项"
            min-width="280"
            show-overflow-tooltip
          />
          <el-table-column label="严重程度" width="120"
            ><template #default="{ row }">{{
              severityLabels[row.severityCode] || row.severityCode || '—'
            }}</template></el-table-column
          >
          <el-table-column label="状态" width="120"
            ><template #default="{ row }"
              ><el-tag type="warning">{{
                issueStatuses[row.statusCode] || row.statusCode
              }}</el-tag></template
            ></el-table-column
          >
        </el-table>
        <h3 class="inspection-section-title">归档报告与处理结果</h3>
        <div class="closure-report">
          <span>{{
            task.currentReportId
              ? `当前报告 V${currentReport?.revisionNo || '—'}`
              : '尚无有效巡检报告'
          }}</span>
          <el-button link type="primary" @click="activeStep = 'report'">查看报告及标注</el-button>
        </div>
        <el-descriptions :column="smallScreen ? 1 : 2" border>
          <el-descriptions-item label="任务状态">{{
            statusLabel(task.statusCode)
          }}</el-descriptions-item>
          <el-descriptions-item label="尚未闭环的跟踪事项">{{
            task.issues.filter((row) => row.statusCode !== 'CLOSED').length
          }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{
            task.archiveRequestedAt ? formatDate(task.archiveRequestedAt) : '尚未申请'
          }}</el-descriptions-item>
          <el-descriptions-item label="归档时间">{{
            task.archivedAt ? formatDate(task.archivedAt) : '尚未归档'
          }}</el-descriptions-item>
          <el-descriptions-item v-if="task.customerArchiveId" label="客户归档引用">{{
            task.customerArchiveId
          }}</el-descriptions-item>
        </el-descriptions>
        <p class="help-text"
          >报告有效、问题标注完成且所有跟踪事项确认闭环后，由任务负责工程师申请，再由本区域服务经理归档。</p
        >
        <el-form v-if="task.statusCode === 'CLOSED'" label-position="top" :disabled="writeDisabled">
          <el-form-item label="申请或归档说明" required
            ><el-input
              v-model="archiveDescription"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
          /></el-form-item>
          <el-button
            v-if="task.owner"
            v-hasPermi="['pms:inspection-task:request-archive']"
            type="primary"
            @click="archive('request-archive')"
            >申请归档</el-button
          >
          <el-button
            v-if="task.manager && task.archiveRequestedAt"
            v-hasPermi="['pms:inspection-task:archive']"
            type="primary"
            @click="archive('archive')"
            >确认归档到客户历史</el-button
          >
        </el-form>
        <el-alert
          v-if="task.statusCode === 'ARCHIVED'"
          title="此任务已归档，可查看执行、报告及处理历史，历史版本只读。"
          type="success"
          :closable="false"
        />
        <el-alert
          v-if="task.statusCode === 'CANCELLED'"
          :title="'取消原因：' + (task.cancellationReason || '未提供')"
          type="info"
          :closable="false"
        />
      </ContentWrap>

      <ContentWrap :body-style="{}">
        <el-collapse>
          <el-collapse-item title="办理历史" name="events"
            ><el-timeline>
              <el-timeline-item
                v-for="event in [...task.events].reverse()"
                :key="String(event.id)"
                :timestamp="event.occurredAt ? formatDate(event.occurredAt) : ''"
              >
                <strong>{{ event.actionCode }}</strong> ·
                {{ event.description || statusLabel(event.toStatus || '') }}
                <p class="help-text"
                  >办理人 {{ personLabel({ name: event.actorName, id: event.actorId }) }} ·
                  {{ statusLabel(event.fromStatus || '') }} →
                  {{ statusLabel(event.toStatus || '') }}</p
                >
              </el-timeline-item>
            </el-timeline></el-collapse-item
          >
          <el-collapse-item
            v-if="task.manager && !terminal"
            title="负责人分配与取消"
            name="management"
          >
            <el-form label-position="top" :disabled="writeDisabled" class="management-form">
              <el-form-item label="新负责人"
                ><el-select
                  v-model="assignment.userId"
                  filterable
                  @visible-change="(visible) => visible && loadPeople()"
                >
                  <el-option
                    v-for="person in people"
                    :key="String(person.id)"
                    :value="String(person.id)"
                    :label="person.nickname"
                  /> </el-select
              ></el-form-item>
              <el-form-item label="分配原因"
                ><el-input v-model="assignment.reason" maxlength="2000"
              /></el-form-item>
              <el-button v-hasPermi="['pms:inspection-task:assign']" @click="assign"
                >保存负责人分配</el-button
              >
              <el-button
                v-hasPermi="['pms:inspection-task:cancel']"
                type="danger"
                plain
                @click="cancelTask"
                >取消任务</el-button
              >
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </ContentWrap>
    </template>

    <ContentWrap
      v-if="routeIssueId && (!task || activeStep === 'issues')"
      :body-style="{}"
      v-loading="issueLoading"
    >
      <el-alert v-if="issueError" :title="issueError" type="error" :closable="false">
        <template #default><el-button link @click="loadIssue">重新加载问题</el-button></template>
      </el-alert>
      <InspectionIssuePanel
        v-if="issue"
        :issue="issue"
        :disabled="
          busy ||
          !!pending ||
          onlineBusy ||
          drafts.online ||
          attachmentBusy ||
          drafts.attachment ||
          !issueFresh
        "
        @action="processIssue"
        @download="downloadEvidence"
        @draft-changed="drafts.issue = $event"
      />
    </ContentWrap>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import * as Api from '@/api/pms/inspection/tasks'
import type {
  AnnotationInput,
  CommandView,
  EvidenceView,
  ExecutionView,
  FileReference,
  IssueDetail,
  PreparationInput,
  ReportFileView,
  ReportView,
  TaskDetail,
  WireLong
} from '@/api/pms/inspection/tasks'
import * as RuleApi from '@/api/pms/inspection/rules'
import * as FileApi from '@/api/pms/platform/file/version-reference'
import { getSimpleUserList } from '@/api/system/user'
import { inspectionFailure } from '../shared/interaction'
import InspectionExecutionPanel from '../components/InspectionExecutionPanel.vue'
import InspectionOnlinePanel from '../components/InspectionOnlinePanel.vue'
import InspectionReportPanel from '../components/InspectionReportPanel.vue'
import InspectionAnnotationPanel from '../components/InspectionAnnotationPanel.vue'
import InspectionIssuePanel from '../components/InspectionIssuePanel.vue'
import InspectionAttachmentPanel from '../components/InspectionAttachmentPanel.vue'
import InspectionSteps from '../components/InspectionSteps.vue'
import '../shared/inspection.css'

defineOptions({ name: 'PmsInspectionTaskDetail' })
const route = useRoute(),
  router = useRouter(),
  message = useMessage()
const smallScreen = useMediaQuery('(max-width: 767px)')
const taskId = computed(() => String(route.params.taskId || ''))
const routeIssueId = computed(() =>
  typeof route.query.issueId === 'string' ? route.query.issueId : ''
)
const task = ref<TaskDetail>(),
  issue = ref<IssueDetail>()
const loading = ref(false),
  issueLoading = ref(false),
  busy = ref(false),
  fresh = ref(false),
  issueFresh = ref(false)
const loadError = ref(''),
  issueError = ref(''),
  writeError = ref('')
let loadSequence = 0,
  issueSequence = 0,
  ruleSequence = 0,
  commandSequence = 0,
  peopleSequence = 0
const statuses: Record<string, string> = {
  PREPARING: '待准备',
  PRECHECK_PENDING: '待预检',
  EXECUTING: '巡检中',
  REPORT_PENDING: '待报告',
  ANNOTATION_PENDING: '待标注',
  TRACKING: '待办跟踪中',
  CLOSED: '已闭环',
  ARCHIVED: '已归档',
  CANCELLED: '已取消'
}
const issueStatuses: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  CLOSURE_PENDING: '待确认闭环',
  CLOSED: '已闭环'
}
const statusLabel = (code: string) => statuses[code] || code || '—'
type Step = 'prepare' | 'execute' | 'report' | 'issues' | 'archive'
const steps: { key: Step; label: string }[] = [
  { key: 'prepare', label: '准备' },
  { key: 'execute', label: '执行' },
  { key: 'report', label: '报告与标注' },
  { key: 'issues', label: '待办跟踪' },
  { key: 'archive', label: '闭环' }
]
const activeStep = ref<Step>('prepare')
const activeStepIndex = computed(() => steps.findIndex((step) => step.key === activeStep.value))
const selectStep = (index: number) => {
  if (steps[index]) activeStep.value = steps[index].key
}
const stateStep = (state: string): Step =>
  ['PRECHECK_PENDING', 'EXECUTING'].includes(state)
    ? 'execute'
    : ['REPORT_PENDING', 'ANNOTATION_PENDING'].includes(state)
      ? 'report'
      : state === 'TRACKING'
        ? 'issues'
        : ['CLOSED', 'ARCHIVED', 'CANCELLED'].includes(state)
          ? 'archive'
          : 'prepare'
const reachedStepIndex = computed(() => {
  if (!task.value) return 0
  if (task.value.statusCode === 'ARCHIVED') return 5
  const state =
    task.value.statusCode === 'CANCELLED'
      ? [...task.value.events].reverse().find((event) => event.toStatus === 'CANCELLED')
          ?.fromStatus || 'PREPARING'
      : task.value.statusCode
  return steps.findIndex((step) => step.key === stateStep(state))
})
const closedIssues = computed(
  () => task.value?.issues.filter((row) => row.statusCode === 'CLOSED') || []
)
const currentReport = computed(() => {
  const current = task.value
  if (!current) return undefined
  return current.reports.find((report) => String(report.id) === String(current.currentReportId))
})
const openIssues = computed(
  () => task.value?.issues.filter((row) => row.statusCode !== 'CLOSED') || []
)
const severityLabels: Record<string, string> = { GENERAL: '一般', SEVERE: '严重', FATAL: '致命' }
const personLabel = (person: { name?: string; id?: WireLong }) =>
  person.name || (person.id == null ? '未提供' : `用户（编号 ${person.id}）`)
const drafts = reactive({
  execution: false,
  online: false,
  report: false,
  annotation: false,
  issue: false,
  attachment: false
})
const onlineBusy = ref(false)
const attachmentBusy = ref(false)
const preparation = reactive({
  taskName: '',
  background: '',
  mode: 'OFFLINE' as PreparationInput['mode'],
  plannedAt: ''
})
const selectedRules = ref<Record<string, string[]>>({})
const preparationBaseline = ref('')
const preparationPayload = (): PreparationInput => ({
  taskName: preparation.taskName,
  background: preparation.background,
  mode: preparation.mode,
  plannedAt: preparation.plannedAt ? Number(preparation.plannedAt) : undefined,
  rules: Object.entries(selectedRules.value).flatMap(([deviceId, revisions]) =>
    revisions.map((ruleRevisionId) => ({ deviceId, ruleRevisionId }))
  )
})
const preparationDirty = computed(
  () => !!task.value && preparationBaseline.value !== JSON.stringify(preparationPayload())
)
const archiveDescription = ref('')
const missingDescription = ref('')
const assignment = reactive({ userId: '', reason: '' })
const people = ref<Awaited<ReturnType<typeof getSimpleUserList>>>([])
const terminal = computed(
  () => !!task.value && ['ARCHIVED', 'CANCELLED'].includes(task.value.statusCode)
)
type Intent = { taskId: string; label: string; section: string; run: () => Promise<unknown> }
const pending = shallowRef<Intent>()
const writeDisabled = computed(
  () =>
    busy.value ||
    !!pending.value ||
    onlineBusy.value ||
    drafts.online ||
    attachmentBusy.value ||
    drafts.attachment ||
    !fresh.value ||
    terminal.value
)
const onlineDisabled = computed(
  () =>
    busy.value ||
    !!pending.value ||
    attachmentBusy.value ||
    !fresh.value ||
    terminal.value ||
    preparationDirty.value ||
    Object.entries(drafts).some(([key, dirty]) => key !== 'online' && dirty) ||
    !!missingDescription.value ||
    !!archiveDescription.value ||
    !!assignment.reason ||
    !!assignment.userId
)
const attachmentDisabled = computed(
  () =>
    busy.value ||
    !!pending.value ||
    onlineBusy.value ||
    !fresh.value ||
    terminal.value ||
    preparationDirty.value ||
    Object.entries(drafts).some(([key, dirty]) => key !== 'attachment' && dirty) ||
    !!missingDescription.value ||
    !!archiveDescription.value ||
    !!assignment.reason ||
    !!assignment.userId
)
const editablePhase = computed(
  () =>
    !!task.value &&
    ['PREPARING', 'PRECHECK_PENDING', 'EXECUTING', 'REPORT_PENDING'].includes(task.value.statusCode)
)
const canPrepare = computed(
  () => !writeDisabled.value && editablePhase.value && checkPermi(['pms:inspection-task:prepare'])
)
const canStart = computed(
  () => !writeDisabled.value && editablePhase.value && checkPermi(['pms:inspection-task:execute'])
)
const canExecute = computed(
  () =>
    !writeDisabled.value &&
    task.value?.statusCode === 'EXECUTING' &&
    checkPermi(['pms:inspection-task:execute'])
)
const anyDirty = computed(
  () =>
    preparationDirty.value ||
    Object.values(drafts).some(Boolean) ||
    !!missingDescription.value ||
    !!archiveDescription.value ||
    !!assignment.reason ||
    !!assignment.userId
)
const preparationForm = ref<FormInstance>()
const preparationRules: FormRules = {
  taskName: [{ required: true, whitespace: true, message: '请填写任务名称', trigger: 'blur' }],
  background: [{ required: true, whitespace: true, message: '请填写巡检背景', trigger: 'blur' }]
}

const preparationDeviceId = ref(''),
  executionDeviceId = ref('')
const selectable = ref<RuleApi.SelectableRule[]>([]),
  rulesLoading = ref(false),
  rulesError = ref('')
const currentSelections = computed({
  get: () => selectedRules.value[preparationDeviceId.value] || [],
  set: (value: string[]) => {
    selectedRules.value[preparationDeviceId.value] = value
  }
})
const unavailableSelections = computed(() =>
  rulesLoading.value || rulesError.value
    ? []
    : currentSelections.value.filter(
        (id) => !selectable.value.some((r) => String(r.revisionId) === id)
      )
)
const removeSelection = (id: string) => {
  currentSelections.value = currentSelections.value.filter((value) => value !== id)
}
const commands = ref<CommandView[]>([]),
  commandsLoading = ref(false),
  commandsError = ref('')
const executionPanelKey = ref(0)
const pendingLogs = computed(
  () =>
    task.value?.executions.filter(
      (r) =>
        String(r.attemptId) === String(task.value?.currentAttemptId) &&
        ['OFFLINE', 'COLLECT'].includes(r.purposeCode || '') &&
        !!r.artifactId &&
        ['PENDING', 'RUNNING'].includes(r.statusCode)
    ) || []
)
const loadSelectable = async () => {
  const seq = ++ruleSequence,
    id = preparationDeviceId.value
  selectable.value = []
  rulesError.value = ''
  rulesLoading.value = false
  if (!id || !task.value) return
  rulesLoading.value = true
  try {
    const rows = await RuleApi.getSelectableRules(id)
    if (seq === ruleSequence) selectable.value = rows
  } catch {
    if (seq === ruleSequence) rulesError.value = '适用规则加载失败，请重新加载后再修改选择。'
  } finally {
    if (seq === ruleSequence) rulesLoading.value = false
  }
}
const loadCommands = async () => {
  const seq = ++commandSequence,
    id = taskId.value,
    deviceId = executionDeviceId.value
  commands.value = []
  commandsError.value = ''
  commandsLoading.value = false
  if (!task.value?.currentAttemptId || !deviceId) return
  commandsLoading.value = true
  try {
    const rows = await Api.getCommands(id, deviceId)
    if (seq === commandSequence) commands.value = rows
  } catch {
    if (seq === commandSequence)
      commandsError.value = '冻结命令加载失败，暂不能开始执行或上传日志，请重新加载。'
  } finally {
    if (seq === commandSequence) commandsLoading.value = false
  }
}
const selectExecutionDevice = (id: WireLong) => {
  executionDeviceId.value = String(id)
  void loadCommands()
}
const clearDrafts = () => {
  Object.assign(drafts, { execution: false, report: false, annotation: false, issue: false })
  archiveDescription.value = ''
  missingDescription.value = ''
  assignment.userId = ''
  assignment.reason = ''
}
const resetPreparation = (value: TaskDetail) => {
  Object.assign(preparation, {
    taskName: value.taskName,
    background: value.background,
    mode: value.mode,
    plannedAt: value.plannedAt == null ? '' : String(value.plannedAt)
  })
  selectedRules.value = Object.fromEntries(
    value.devices.map((device) => [
      String(device.deviceId),
      value.selections
        .filter(
          (row) => String(row.deviceId) === String(device.deviceId) && row.ruleRevisionId != null
        )
        .map((row) => String(row.ruleRevisionId))
    ])
  )
  preparationBaseline.value = JSON.stringify(preparationPayload())
  if (!value.devices.some((d) => String(d.deviceId) === preparationDeviceId.value))
    preparationDeviceId.value = String(value.devices[0]?.deviceId || '')
  if (!value.devices.some((d) => String(d.deviceId) === executionDeviceId.value))
    executionDeviceId.value = String(value.devices[0]?.deviceId || '')
}
const loadIssue = async () => {
  const seq = ++issueSequence,
    id = routeIssueId.value,
    parent = taskId.value
  issueError.value = ''
  issueFresh.value = false
  issueLoading.value = false
  if (!id) {
    issue.value = undefined
    return
  }
  if (!checkPermi(['pms:service-issue:query'])) {
    issue.value = undefined
    issueError.value = '没有问题查询权限。'
    return
  }
  issueLoading.value = true
  try {
    const value = await Api.getIssue(id)
    if (seq !== issueSequence || parent !== taskId.value) return
    if (String(value.taskId) !== parent) throw new Error('问题归属与当前任务不一致')
    issue.value = value
    issueFresh.value = true
  } catch {
    if (seq === issueSequence) issueError.value = '问题记录加载失败或无权访问，请重新加载。'
  } finally {
    if (seq === issueSequence) issueLoading.value = false
  }
}
const load = async (selectStep = false) => {
  const seq = ++loadSequence,
    id = taskId.value
  loading.value = true
  fresh.value = false
  loadError.value = ''
  const issueRead = loadIssue()
  try {
    if (!/^\d+$/.test(id)) throw new Error('任务编号无效')
    if (checkPermi(['pms:inspection-task:query'])) {
      const value = await Api.getTask(id)
      if (seq !== loadSequence) return
      task.value = value
      fresh.value = true
      resetPreparation(value)
      clearDrafts()
      if (selectStep) activeStep.value = routeIssueId.value ? 'issues' : stateStep(value.statusCode)
      void loadSelectable()
      void loadCommands()
    } else if (!routeIssueId.value) loadError.value = '没有巡检任务查询权限。'
  } catch {
    if (seq === loadSequence)
      loadError.value = '任务详情加载失败或无权访问。当前内容禁止提交，请刷新后继续。'
  } finally {
    await issueRead
    if (seq === loadSequence) {
      if (!fresh.value && issueFresh.value && !task.value) loadError.value = ''
      loading.value = false
    }
  }
}
const allowDraft = (section: string) => {
  const other =
    (preparationDirty.value && section !== 'prepare') ||
    Object.entries(drafts).some(([key, dirty]) => dirty && key !== section) ||
    (!!missingDescription.value && section !== 'missing') ||
    (!!archiveDescription.value && section !== 'archive') ||
    ((!!assignment.userId || !!assignment.reason) && section !== 'assign')
  if (other) {
    message.warning('其他步骤还有未提交内容，请先保存或清理后再执行此操作。')
    return false
  }
  return true
}
const perform = async (intent: Intent) => {
  if (busy.value || pending.value || onlineBusy.value || !allowDraft(intent.section)) return
  pending.value = intent
  await retryPending()
}
const retryPending = async () => {
  const intent = pending.value
  if (!intent || busy.value || intent.taskId !== taskId.value) return
  busy.value = true
  writeError.value = ''
  try {
    await intent.run()
    if (intent.taskId !== taskId.value) return
    pending.value = undefined
    await load(true)
    if (fresh.value || issueFresh.value) message.success('已更新处理结果，请查看当前业务状态。')
    if (fresh.value && intent.section === 'execution') executionPanelKey.value++
  } catch (error) {
    if (intent.taskId !== taskId.value) return
    const failure = inspectionFailure(error)
    writeError.value = failure.message
    if (!failure.unknown) pending.value = undefined
    if (failure.refresh) {
      fresh.value = false
      issueFresh.value = false
      writeError.value += ' 请刷新最新资料后继续。'
    }
  } finally {
    if (intent.taskId === taskId.value) busy.value = false
  }
}
const runTask = (input: {
  label: string
  section: string
  run: (context: { id: string; version: WireLong; key: string }) => Promise<unknown>
}) => {
  if (!task.value || writeDisabled.value) return
  const context = { id: taskId.value, version: task.value.version, key: generateUUID() }
  return perform({
    taskId: context.id,
    label: input.label,
    section: input.section,
    run: () => input.run(context)
  })
}
const savePreparation = async () => {
  if (!canPrepare.value || !(await preparationForm.value?.validate().catch(() => false))) return
  const data = structuredClone(preparationPayload())
  await runTask({
    label: '保存准备资料',
    section: 'prepare',
    run: (c) => Api.savePreparation(c.id, data, c.version, c.key)
  })
}
const startAttempt = async () => {
  if (!canStart.value || !allowDraft('none')) return
  let reason = ''
  if (task.value?.currentAttemptId) {
    try {
      const answer = await message.prompt(
        '请填写重新执行原因。当前尝试和证据会保留，新尝试使用已保存的准备资料。',
        '新建执行尝试'
      )
      reason = answer.value.trim()
    } catch {
      return
    }
    if (!reason) {
      message.warning('请填写重新执行原因')
      return
    }
  }
  await runTask({
    label: '新建执行尝试',
    section: 'none',
    run: (c) => Api.taskAction(c.id, 'start-attempt', { reason }, c.version, c.key)
  })
}
const reportMissing = () => {
  if (!missingDescription.value.trim()) return
  const data = {
    deviceId: preparationDeviceId.value || undefined,
    description: missingDescription.value.trim()
  }
  return runTask({
    label: '提交规则缺失反馈',
    section: 'missing',
    run: (c) => Api.reportMissingRule(c.id, data, c.version, c.key)
  })
}
const uploadLog = (data: { deviceId: WireLong; file: File; commandHint?: string }) => {
  if (!task.value?.currentAttemptId) return
  const payload = { ...data, attemptId: task.value.currentAttemptId }
  return runTask({
    label: '登记日志',
    section: 'execution',
    run: (c) => Api.uploadOfflineLog(c.id, payload, c.version, c.key)
  })
}
const refreshLog = (executionId: WireLong) =>
  runTask({
    label: '刷新解析结果',
    section: 'none',
    run: (c) => Api.taskAction(c.id, 'refresh-log-parse', { executionId }, c.version, c.key)
  })
const finishExecution = () =>
  runTask({
    label: '完成执行',
    section: 'none',
    run: (c) => Api.taskAction(c.id, 'complete-execution', {}, c.version, c.key)
  })
const abandonLog = async (row: ExecutionView) => {
  let reason: string
  try {
    reason = (
      await message.prompt('请填写停止采用的原因。原日志与远端解析证据会保留。', '停止采用解析结果')
    ).value.trim()
  } catch {
    return
  }
  if (!reason) {
    message.warning('请填写停止采用原因')
    return
  }
  await runTask({
    label: '停止采用解析结果',
    section: 'none',
    run: (c) =>
      Api.taskAction(c.id, 'abandon-log-parse', { executionId: row.id, reason }, c.version, c.key)
  })
}
const generate = (data: { explanation: string }) =>
  runTask({
    label: '生成报告',
    section: 'report',
    run: (c) => Api.generateReport(c.id, data.explanation, c.version, c.key)
  })
const submitManual = (data: {
  explanation: string
  files: { formatCode: 'PDF' | 'DOCX' | 'DOC' | 'XML'; file: FileReference }[]
}) => {
  const payload = structuredClone(data)
  return runTask({
    label: '提交人工报告',
    section: 'report',
    run: (c) => Api.submitManualReport(c.id, payload, c.version, c.key)
  })
}
const annotate = (data: AnnotationInput) => {
  const payload = structuredClone(data)
  return runTask({
    label: '保存问题标注',
    section: 'annotation',
    run: (c) => Api.annotateResult(c.id, payload, c.version, c.key)
  })
}
const completeAnnotations = () =>
  runTask({
    label: '确认全部标注',
    section: 'none',
    run: (c) => Api.taskAction(c.id, 'complete-annotations', {}, c.version, c.key)
  })
const archive = (action: 'request-archive' | 'archive') => {
  const description = archiveDescription.value.trim()
  if (!description) {
    message.warning('请填写申请或归档说明')
    return
  }
  return runTask({
    label: action === 'archive' ? '客户归档' : '申请归档',
    section: 'archive',
    run: (c) => Api.taskAction(c.id, action, { description }, c.version, c.key)
  })
}
const loadPeople = async () => {
  const seq = ++peopleSequence
  try {
    const values = await getSimpleUserList()
    if (seq === peopleSequence) people.value = values
  } catch {
    if (seq === peopleSequence) {
      people.value = []
      message.error('负责人候选加载失败')
    }
  }
}
const assign = () => {
  const ownerUserId = assignment.userId,
    reason = assignment.reason.trim()
  if (!ownerUserId || !reason) {
    message.warning('请选择负责人并填写分配原因')
    return
  }
  return runTask({
    label: '负责人分配',
    section: 'assign',
    run: (c) => Api.taskAction(c.id, 'assign', { ownerUserId, reason }, c.version, c.key)
  })
}
const cancelTask = async () => {
  if (!allowDraft('none')) return
  let reason: string
  try {
    reason = (
      await message.prompt(
        '取消后任务只读；已有未闭环跟踪事项仍须处理。请填写取消原因。',
        '取消巡检任务'
      )
    ).value.trim()
  } catch {
    return
  }
  if (!reason) {
    message.warning('请填写取消原因')
    return
  }
  await runTask({
    label: '取消任务',
    section: 'none',
    run: (c) => Api.taskAction(c.id, 'cancel', { reason }, c.version, c.key)
  })
}
type IssueAction = {
  action: 'record-process' | 'request-closure' | 'confirm-closure' | 'return-closure'
  data: { content?: string; description?: string; evidence?: FileReference[] }
}
const processIssue = (input: IssueAction) => {
  if (!issue.value || !issueFresh.value || busy.value || pending.value) return
  const id = issue.value.id,
    version = issue.value.version,
    key = generateUUID(),
    data = structuredClone(input.data),
    action = input.action
  return perform({
    taskId: taskId.value,
    label: '问题办理',
    section: 'issue',
    run: () => Api.issueAction(id, action, data, version, key)
  })
}
const openIssue = (id: WireLong) =>
  router.replace({ query: { ...route.query, issueId: String(id) } })
const download = async (input: {
  taskId: WireLong
  artifactId?: WireLong
  versionNo?: number
  referenceKey?: string
  purpose: string
}) => {
  if (!input.artifactId || !input.versionNo || !input.referenceKey) {
    message.error('文件引用不完整')
    return
  }
  const target = window.open('about:blank', '_blank')
  if (!target) {
    message.warning('浏览器阻止了下载窗口，请允许弹窗后重试')
    return
  }
  target.opener = null
  try {
    const ticket = await FileApi.createAccessTicket(input.artifactId, input.versionNo, 'DOWNLOAD', {
      ownerContext: 'SRV',
      objectType: 'INSPECTION_TASK',
      objectId: String(input.taskId),
      purposeCode: input.purpose,
      referenceKey: input.referenceKey
    })
    target.location.replace(ticket.shortLivedUrl)
  } catch {
    target.close()
    message.error('此文件版本暂不可用或无权下载，请重试')
  }
}
const downloadLog = (row: ExecutionView) =>
  download({
    taskId: taskId.value,
    artifactId: row.artifactId,
    versionNo: row.fileVersionNo,
    referenceKey: row.fileReferenceKey,
    purpose: 'LOG'
  })
const downloadReport = ({ file }: { report: ReportView; file: ReportFileView }) =>
  download({
    taskId: taskId.value,
    artifactId: file.artifactId,
    versionNo: file.fileVersionNo,
    referenceKey: file.referenceKey,
    purpose: 'REPORT'
  })
const downloadEvidence = (row: EvidenceView) =>
  issue.value &&
  download({
    taskId: issue.value.taskId,
    artifactId: row.artifactId,
    versionNo: row.fileVersionNo,
    referenceKey: row.referenceKey,
    purpose: 'EVIDENCE'
  })

const mayLeave = async () => {
  if (busy.value || pending.value || onlineBusy.value || attachmentBusy.value) {
    message.warning('请先确认当前操作结果，再离开此页。')
    return false
  }
  if (!anyDirty.value) return true
  try {
    await message.confirm(
      '有尚未提交的内容，离开后将丢失这些输入。已上传到平台的文件会保留原归属。',
      '确认离开'
    )
    return true
  } catch {
    return false
  }
}
const reload = async () => {
  if (await mayLeave()) {
    writeError.value = ''
    await load(false)
  }
}
onBeforeRouteLeave(mayLeave)
onBeforeRouteUpdate(mayLeave)
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (anyDirty.value || busy.value || pending.value || onlineBusy.value || attachmentBusy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', beforeUnload)
watch(
  () => [taskId.value, routeIssueId.value],
  () => {
    loadSequence++
    issueSequence++
    commandSequence++
    ruleSequence++
    peopleSequence++
    task.value = undefined
    issue.value = undefined
    pending.value = undefined
    clearDrafts()
    fresh.value = false
    issueFresh.value = false
    writeError.value = ''
    preparationBaseline.value = ''
    void load(true)
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  loadSequence++
  issueSequence++
  commandSequence++
  ruleSequence++
  peopleSequence++
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.inspection-detail {
  min-width: 0;
}
.page-heading,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
h1 {
  margin: 8px 0;
  font-size: 18px;
}
h2 {
  margin: 0 0 16px;
  font-size: 15px;
}
h3 {
  font-size: 14px;
}
.help-text {
  color: var(--inspection-muted);
  font-size: 12px;
  line-height: 1.6;
  margin: 8px 0;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.project-context {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}
.project-context dt {
  margin-bottom: 4px;
  color: var(--inspection-muted);
  font-size: 12px;
}
.project-context dd {
  min-height: 30px;
  margin: 0;
  padding: 6px 0;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.serial-value {
  font-family: Consolas, monospace;
}
.closure-report {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin: 0 0 20px;
}
.mode-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
}
.mode-options :deep(.el-radio) {
  height: auto;
  min-height: 32px;
  margin-right: 0;
}
.mode-options :deep(.el-radio__label) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: normal;
  line-height: 1.6;
}
.inspection-card__header h2 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}
.inspection-card__header .help-text {
  margin: 0;
}
.inspection-section-title {
  margin-top: 16px;
}
.inspection-page .form-grid {
  grid-template-columns: 2fr 1fr;
}
.form-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}
.device-select {
  width: 340px;
}
.rule-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}
.rule-options :deep(.el-checkbox) {
  height: auto;
  min-height: 68px;
  margin: 0;
  padding: 12px;
}
.rule-options :deep(.el-checkbox__label) {
  display: flex;
  flex-direction: column;
  white-space: normal;
}
.unavailable-rules {
  margin: 12px 0;
}
.unavailable-rules :deep(.el-tag) {
  margin-right: 8px;
}
.pending-logs {
  margin-top: 24px;
}
.management-form {
  max-width: 700px;
}
@media (max-width: 767px) {
  .page-heading,
  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }
  .inspection-page .form-grid,
  .rule-options {
    grid-template-columns: 1fr;
  }
  .project-context {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .device-select {
    width: 100%;
  }
}
@media (max-width: 479px) {
  .project-context {
    grid-template-columns: 1fr;
  }
}
</style>
