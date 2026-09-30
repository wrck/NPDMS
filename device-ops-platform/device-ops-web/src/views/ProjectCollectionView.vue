<script setup lang="ts">
import { onBeforeRouteLeave } from 'vue-router'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import { upsertSchedule } from '@/api/device-ops'
import { consumeScript } from '@/management/script-handoff'
import AppShell from '@/components/AppShell.vue'
import CollectionTaskPanel from '@/components/CollectionTaskPanel.vue'
import ParserSidebar from '@/components/ParserSidebar.vue'
import ScriptArtifactEditor from '@/components/ScriptArtifactEditor.vue'
import TargetSelector from '@/components/TargetSelector.vue'
import type {
  CollectionSubmission,
  CollectionTargetRequest,
  ConnectionRequest,
  DeviceProjection,
  ProjectProjection,
  ScriptDraft,
  SubmitCollectionRequest,
  SubmitGenericCollectionRequest
} from '@/types/collection'
import type {
  SemanticParserSelection,
  SemanticParsingRequest
} from '@/types/parser'

const props = defineProps<{
  projectKey: string
  embedded?: boolean
}>()

const targetRef = ref<InstanceType<typeof TargetSelector>>()
const taskPanelRef = ref<InstanceType<typeof CollectionTaskPanel>>()
const parserRef = ref<InstanceType<typeof ParserSidebar>>()
const formError = ref('')
const commandCollapsed = ref(false)
const parserCollapsed = ref(false)
const mediumViewportQuery = '(min-width: 768px) and (max-width: 1199px)'
let mediumViewport: MediaQueryList | undefined
const sessionLabel = ref('未选择目标')
const script = ref<ScriptDraft>({
  source: 'LOCAL_MANAGED',
  key: '',
  version: '1.0.0',
  content: '',
  sha256: '',
  policy: 'REGISTER_VERSION',
  parserType: 'NONE',
  parserConfig: ''
})
const activityType = ref('IMPLEMENTATION')
const semanticParser = ref<SemanticParserSelection>({ mode: 'AUTO' })
const scheduleSaving = ref(false)
const scheduleMessage = ref('')
const schedule = reactive({
  scheduleKey: '',
  namespace: 'standalone',
  projectHint: props.projectKey,
  deviceKeyHints: '',
  cron: '0 0 2 * * *',
  timezone: 'Asia/Shanghai',
  callbackUri: ''
})

function semanticParsingRequest(): SemanticParsingRequest | undefined {
  if (semanticParser.value.mode === 'AUTO') return undefined
  if (semanticParser.value.mode === 'DISABLED') return { enabled: false }
  const releaseId = requireText(semanticParser.value.releaseId ?? '', '请选择解析版本。')
  const logType = requireText(semanticParser.value.logType ?? '', '解析版本缺少内部类型。')
  return {
    enabled: true,
    releaseId,
    logType,
    inputFormat: 'command-output-block/v1'
  }
}

function requireText(value: string, message: string): string {
  if (!value.trim()) throw new Error(message)
  return value.trim()
}

function isSavedConnectionReference(connection: ConnectionRequest): connection is {
  savedConnectionId: string
  credentialNamespace: string
} {
  return 'savedConnectionId' in connection
}

function createCollectionTarget(
  connection: ConnectionRequest,
  project: ProjectProjection,
  device: DeviceProjection
): CollectionTargetRequest {
  const context = {
    project: structuredClone(project),
    device: structuredClone(device),
    extensions: {}
  }
  if (isSavedConnectionReference(connection)) {
    return {
      ...context,
      savedConnectionId: connection.savedConnectionId,
      credentialNamespace: connection.credentialNamespace
    }
  }
  if (connection.protocol === 'TELNET') {
    return {
      ...context,
      ...structuredClone(connection),
      executionMode: 'SHELL'
    }
  }
  return {
    ...context,
    ...structuredClone(connection)
  }
}

async function submit() {
  formError.value = ''
  try {
    if (semanticParser.value.mode === 'AUTO') {
      const options = await parserRef.value?.ensureOptions?.()
      if (options && !options.defaultAvailable) {
        throw new Error(
          '结构化解析无法生效：当前没有已激活的结构化解析版本，本次下发将只有原始输出。' +
          '请先在解析控制台发布并激活版本，或改用“指定版本 / 不解析”后再下发。'
        )
      }
    }
    const selection = targetRef.value?.buildSelection()
    if (!selection) throw new Error('连接组件尚未就绪。')
    const scriptSnapshot = { ...script.value }
    if (scriptSnapshot.source === 'ADHOC_INLINE' && !scriptSnapshot.key.trim()) {
      scriptSnapshot.key = `adhoc-${scriptSnapshot.sha256.slice(0, 16)}`
    }
    requireText(scriptSnapshot.key, '请输入脚本 Key。')
    requireText(scriptSnapshot.version, '请输入脚本版本。')
    requireText(scriptSnapshot.content, '请输入采集命令。')
    if (!scriptSnapshot.sha256) throw new Error('脚本摘要仍在计算，请稍后下发。')

    const common = {
      script: scriptSnapshot,
      activityType: activityType.value,
      commandTimeoutSeconds: 120,
      parseTimeoutSeconds: 15,
      leaseGraceSeconds: 10,
      ...(semanticParser.value.mode === 'AUTO'
        ? {}
        : { semanticParsing: semanticParsingRequest() })
    }
    let submission: CollectionSubmission
    if (selection.project && selection.device) {
      const request: SubmitCollectionRequest = {
        namespace: selection.namespace,
        project: structuredClone(selection.project),
        targets: [createCollectionTarget(selection.connection, selection.project, selection.device)],
        ...common
      }
      submission = {
        mode: 'project',
        projectKey: selection.project.projectKey,
        request,
        deviceLabel: selection.deviceLabel
      }
    } else {
      const request: SubmitGenericCollectionRequest = {
        namespace: selection.namespace,
        ...(selection.project
          ? {
              context: {
                project: structuredClone(selection.project),
                extensions: {}
              }
            }
          : {}),
        connection: structuredClone(selection.connection),
        ...common
      }
      submission = { mode: 'generic', request, deviceLabel: selection.deviceLabel }
    }
    sessionLabel.value = selection.deviceLabel
    const accepted = await taskPanelRef.value?.start(submission)
    if (accepted) {
      targetRef.value?.rememberCurrent()
    }
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '请检查目标和脚本后重试。'
  }
}

function syncParserCollapse(event: MediaQueryList | MediaQueryListEvent) {
  parserCollapsed.value = event.matches
}

async function saveSchedule() {
  scheduleSaving.value = true
  scheduleMessage.value = ''
  try {
    const deviceKeyHints = schedule.deviceKeyHints
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean)
    if (!deviceKeyHints.length) throw new Error('请至少填写一个设备 Key 提示。')
    await upsertSchedule(props.projectKey, requireText(schedule.scheduleKey, '请输入配置标识。'), {
      namespace: requireText(schedule.namespace, '请输入命名空间。'),
      projectKey: props.projectKey,
      projectHint: requireText(schedule.projectHint, '请输入项目提示。'),
      deviceKeyHints,
      scriptKey: requireText(script.value.key, '请先填写脚本 Key。'),
      scriptVersion: requireText(script.value.version, '请先填写脚本版本。'),
      cron: requireText(schedule.cron, '请输入 Cron。'),
      timezone: requireText(schedule.timezone, '请输入时区。'),
      callbackUri: requireText(schedule.callbackUri, '请输入到期回调地址。'),
      enabled: false
    })
    scheduleMessage.value = '已创建停用状态的 SCHEDULE_DUE 配置；它不会直接执行 SSH。'
  } catch (error) {
    scheduleMessage.value =
      error instanceof Error && !('response' in error)
        ? error.message
        : '配置未保存。请检查项目范围、Cron、时区和回调地址。'
  } finally {
    scheduleSaving.value = false
  }
}

onMounted(() => {
  const offeredScript = consumeScript()
  if (offeredScript) {
    // 已登记版本仅执行，避免重复注册；保持 source 不变以免编辑器重置 policy。
    script.value = {
      ...script.value,
      key: offeredScript.scriptKey,
      version: offeredScript.scriptVersion,
      content: offeredScript.content,
      sha256: '',
      policy: 'EXECUTION_ONLY'
    }
  }
  mediumViewport = window.matchMedia(mediumViewportQuery)
  syncParserCollapse(mediumViewport)
  mediumViewport.addEventListener('change', syncParserCollapse)
})

onBeforeUnmount(() => {
  mediumViewport?.removeEventListener('change', syncParserCollapse)
})

onBeforeRouteLeave(() => {
  // 凭据保留在当前窗口内（仅切换连接上下文或关闭窗口时清除），离开路由只停止轮询。
  taskPanelRef.value?.stopPolling()
})
</script>

<template>
  <AppShell
    class="project-collection"
    :embedded="embedded"
    element-layout
    title="设备连接与采集工作台"
    subtitle="连接设备、执行采集并查看输出。"
  >
    <template #context>
      <el-space class="workbench-status" :size="8" wrap aria-label="当前工作台状态">
        <el-text size="small" type="info">项目</el-text>
        <el-text size="small">{{ projectKey }}</el-text>
        <el-divider direction="vertical" />
        <el-text size="small" type="info">入口</el-text>
        <el-text size="small">{{ embedded ? '宿主嵌入' : '独立工作台' }}</el-text>
        <el-divider direction="vertical" />
        <el-tag size="small" type="info" effect="plain">当前目标：{{ sessionLabel }}</el-tag>
      </el-space>
    </template>

    <el-row
      class="workflow-grid"
      :gutter="12"
      :class="{
        'workbench--command-collapsed': commandCollapsed,
        'workbench--parser-collapsed': parserCollapsed
      }"
    >
      <el-col :xs="24" :lg="6" class="workbench-connection">
        <TargetSelector ref="targetRef" :project-key="projectKey" />
      </el-col>
      <el-col :xs="24" :lg="13" class="workbench-center">
        <ScriptArtifactEditor
          class="workbench-command"
          v-model="script"
          :collapsed="commandCollapsed"
          @toggle="commandCollapsed = !commandCollapsed"
        />
        <div class="workbench-output">
        <label class="dispatch-options">
          <span>实施活动</span>
          <el-select v-model="activityType" size="small" aria-label="实施活动">
            <el-option label="实施采集" value="IMPLEMENTATION" />
            <el-option label="联调验证" value="INTEGRATION" />
            <el-option label="巡检采集" value="INSPECTION" />
            <el-option label="割接采集" value="CUTOVER" />
          </el-select>
        </label>
        <CollectionTaskPanel
          ref="taskPanelRef"
          layout="workbench"
          @submit="submit"
        />
          <el-alert
            v-if="formError"
            class="inline-error"
            :title="formError"
            type="error"
            :closable="false"
            show-icon
          />
        </div>
      </el-col>
      <el-col :xs="24" :lg="5" class="workbench-parser">
        <ParserSidebar
          ref="parserRef"
          v-model="script"
          v-model:selection="semanticParser"
          :collapsed="parserCollapsed"
          @toggle="parserCollapsed = !parserCollapsed"
        />
      </el-col>
    </el-row>

    <el-collapse class="schedule-panel project-collection__settings">
      <el-collapse-item title="可选：到期通知" name="schedule">
        <p>
          此处只保存停用状态的到期通知配置。到期后由发起方提供新快照和新凭据，不直接触发 SSH。
        </p>
        <el-form label-position="top" class="form-grid">
          <el-form-item label="配置标识">
            <el-input v-model="schedule.scheduleKey" autocomplete="off" />
          </el-form-item>
          <el-form-item label="命名空间">
            <el-input v-model="schedule.namespace" autocomplete="off" />
          </el-form-item>
          <el-form-item label="项目提示">
            <el-input v-model="schedule.projectHint" autocomplete="off" />
          </el-form-item>
          <el-form-item label="设备 Key 提示（逗号分隔）">
            <el-input v-model="schedule.deviceKeyHints" autocomplete="off" />
          </el-form-item>
          <el-form-item label="Cron">
            <el-input v-model="schedule.cron" class="command-input" autocomplete="off" />
          </el-form-item>
          <el-form-item label="时区">
            <el-input v-model="schedule.timezone" autocomplete="off" />
          </el-form-item>
          <el-form-item label="SCHEDULE_DUE 回调地址" class="form-grid__wide">
            <el-input v-model="schedule.callbackUri" autocomplete="off" />
          </el-form-item>
        </el-form>
        <el-button size="small" type="primary" :loading="scheduleSaving" @click="saveSchedule">创建停用配置</el-button>
        <p v-if="scheduleMessage" role="status">{{ scheduleMessage }}</p>
      </el-collapse-item>
    </el-collapse>
  </AppShell>
</template>

<style scoped>
.workbench-output {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  gap: 0.375rem;
  overflow: hidden;
  background: transparent;
}

.dispatch-options {
  display: flex;
  min-height: 2rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--line);
  border-radius: 0.3125rem;
  background: var(--surface);
  color: var(--slate);
  font-size: 0.75rem;
}

.dispatch-options :deep(.el-select) {
  width: 8.5rem;
}

.project-collection__settings {
  margin: 0;
}

.workbench-status {
  max-width: min(100%, 38rem);
  justify-content: flex-end;
}

.workbench-status :deep(.el-divider--vertical) {
  height: 1em;
  margin: 0;
}

@media (min-width: 1200px) and (min-height: 851px) {
  .project-collection :deep(.app-shell__content) {
    display: flex;
    flex-direction: column;
    gap: calc(var(--el-component-size-small) / 2);
    overflow: hidden;
  }

  .workbench-command {
    flex: 0 0 auto;
  }

  .workflow-grid {
    flex: 1 1 0;
    min-height: 0;
    flex-wrap: nowrap;
  }

  .workflow-grid > .el-col {
    min-height: 0;
  }

  .workflow-grid > .workbench-center {
    display: flex;
    flex-direction: column;
  }

  .project-collection__settings {
    flex: 0 0 auto;
    max-height: 35vh;
    overflow-y: auto;
  }
}

@media (max-height: 850px), (max-width: 1199px) {
  .workbench-output {
    flex: 0 0 auto;
    overflow: visible;
  }

  .workflow-grid {
    align-items: flex-start;
  }

  .workbench-command {
    flex-shrink: 0;
  }

  .workbench-connection > :deep(.connection-workbench),
  .workbench-parser > :deep(.parser-sidebar) {
    height: auto;
    max-height: none;
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .dispatch-options {
    justify-content: flex-start;
  }
}

@media (max-width: 767px) {
  .workbench-status {
    justify-content: flex-start;
  }
}
</style>
