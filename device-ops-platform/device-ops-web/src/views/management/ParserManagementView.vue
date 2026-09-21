<script setup lang="ts">
import { statusLabel, formatLocalTime } from '@/management/presentation'
import ManagementStatus from '@/components/management/ManagementStatus.vue'
import '@/styles/management.css'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import RequestState from '@/components/management/RequestState.vue'
import SemanticResultPanel from '@/components/SemanticResultPanel.vue'
import { parserApi } from '@/api/parser-management'
import { createRequest, safeError } from '@/management/use-request'
import { inputError, MAX_INPUT_BYTES, MAX_DRAFT_BYTES, parseDraft } from '@/management/parser-input'
import { useAccess } from '@/management/access'
import { managementApi } from '@/api/management'
import type { LogType, ParserRelease, ReleaseValidation, RuntimeStatus } from '@/types/management'
import type { CollectionSemanticResult } from '@/types/parser'
const { can } = useAccess()
const inputLimit = ref(MAX_INPUT_BYTES)
const draftFile = ref<HTMLInputElement>()
const types = createRequest<LogType[]>()
const runtime = createRequest<RuntimeStatus>()
const releases = createRequest<ParserRelease[]>()
const detail = createRequest<ParserRelease>()
const binding = createRequest<{ logType: string; releaseId: string | null }>()
const selectedType = ref(''), selectedRelease = ref(''), tab = ref('releases')
const busy = ref(false), actionError = ref(''), notice = ref('')
const draft = ref(''), text = ref(''), inputFormat = ref('command-output-block/v1')
const validation = ref<ReleaseValidation>(), result = ref<CollectionSemanticResult[]>([])
const taskId = ref(''), taskState = ref('')
const newType = ref(''), displayName = ref('')
let context = 0, fileGeneration = 0
let submissionKey = crypto.randomUUID()
watch([text, inputFormat, selectedType, selectedRelease], () => { submissionKey = crypto.randomUUID() })
onBeforeUnmount(() => { context++; fileGeneration++ })
const loadTypes = () => types.run(parserApi.logTypes)
const loadRuntime = () => runtime.run(parserApi.runtime)
const loadReleases = () => releases.run(signal => parserApi.releases(selectedType.value, signal))
const loadBinding = () => binding.run(signal => parserApi.active(selectedType.value, signal))
const loadDetail = () => detail.run(signal => parserApi.release(selectedRelease.value, signal))
watch(selectedType, () => {
  context++; fileGeneration++; selectedRelease.value = ''; validation.value = undefined; result.value = []; taskId.value = ''; taskState.value = ''; actionError.value = ''; notice.value = ''; draft.value = ''
  releases.clear(); detail.clear(); binding.clear()
  if (selectedType.value) { void loadReleases(); void loadBinding() }
})
watch(selectedRelease, () => { context++; validation.value = undefined; detail.clear(); if (selectedRelease.value) void loadDetail() })
const published = computed(() => detail.data.value?.state === 'PUBLISHED')
async function action(work: () => Promise<unknown>, message: string) {
  if (busy.value) return
  const generation = context
  busy.value = true; actionError.value = ''; notice.value = ''
  try { await work(); if (context === generation) notice.value = message }
  catch (cause) { if (context === generation) actionError.value = cause instanceof Error && !('response' in cause) ? cause.message : safeError(cause) }
  finally { busy.value = false }
}
async function confirmed(message: string) {
  try { await ElMessageBox.confirm(message, '确认变更', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }); return true } catch { return false }
}
async function changeBinding(revoke = false) {
  if (!binding.data.value || busy.value) return
  const type = selectedType.value, release = selectedRelease.value, expected = binding.data.value.releaseId, generation = context
  if (revoke && !expected) return
  if (!await confirmed(`${revoke ? '撤销' : '激活 ' + release}；当前活动版本：${expected ?? '无'}。仅在当前绑定未变化时生效。`) || generation !== context) return
  await action(async () => {
    try { if (revoke) await parserApi.revoke(type, expected!); else await parserApi.activate(type, release, expected) }
    finally { if (generation === context) await loadBinding() }
  }, '活动绑定已更新。')
}
async function saveDraft() {
  await action(async () => { const payload = parseDraft(draft.value, selectedType.value); await parserApi.draft(selectedType.value, payload); await loadReleases() }, '草稿已保存，尚未发布或激活。')
}
async function validate() {
  const release = selectedRelease.value, generation = context
  await action(async () => { const value = await parserApi.validate(release); if (generation === context) validation.value = value }, '样例验证已完成。')
}
async function publish() {
  const release = selectedRelease.value, generation = context
  if (!await confirmed(`发布 ${release}？此操作不会自动激活。`) || generation !== context) return
  await action(async () => { await parserApi.publish(release); if (generation === context) { await loadReleases(); await loadDetail() } }, '版本已发布，未自动激活。')
}
async function importFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0], generation = ++fileGeneration
  if (!file) return
  if (file.size > MAX_DRAFT_BYTES) { actionError.value = '草稿文件超过 32 MiB 上限。'; return }
  try { const value = await file.text(); if (generation === fileGeneration) { parseDraft(value, selectedType.value); draft.value = value; actionError.value = '' } }
  catch { if (generation === fileGeneration) actionError.value = '草稿文件不是符合当前日志类型契约的有效 JSON。' }
}
async function submitText() {
  const error = inputError(text.value, inputLimit.value)
  if (error) { actionError.value = error; return }
  const generation = context
  await action(async () => {
    const task = await parserApi.submit({ logType: selectedType.value, releaseId: selectedRelease.value || undefined, inputFormat: inputFormat.value, inputContent: text.value, mediaType: 'application/json', contextSnapshot: {} }, submissionKey)
    if (generation === context) { taskId.value = task.taskId; taskState.value = statusLabel(task.state); result.value = [] }
  }, '解析任务已提交。可刷新查看状态与结果，不执行设备采集。')
  if (generation === context && actionError.value) actionError.value += ' 提交可能已被受理，请先在任务中心核对；未编辑输入的再次提交复用同一幂等标识。'
}
async function refreshTask() {
  const generation = context, id = taskId.value
  await action(async () => {
    const task = await parserApi.task(id)
    if (generation !== context) return
    taskState.value = `${statusLabel(task.state)}${task.waitReason ? ' · ' + task.waitReason : ''}`
    if (task.state === 'SUCCEEDED') {
      const envelope = await parserApi.result(id)
      if (generation === context) result.value = [{ targetId: 0, taskId: id, state: task.state, releaseId: task.releaseId, coordinate: task.coordinate, resultId: envelope.resultId, result: envelope }]
    }
  }, '任务状态已刷新。')
}
onMounted(() => {
  void loadTypes(); void loadRuntime()
  const generation = context
  void managementApi.settings().then(value => { if (generation === context && value.maxParserInputBytes > 0) inputLimit.value = value.maxParserInputBytes }).catch(() => { /* Parser-only identities retain server-default limit. */ })
})
</script>
<template>
  <AppShell
    class="management-page"
    element-layout
    title="解析控制台"
    subtitle="草稿 → 验证 → 发布 → 激活"
  >
    <div class="console">
      <el-alert
        v-if="actionError"
        :title="actionError"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="notice"
        :title="notice"
        type="success"
        :closable="false"
      />
      <el-card shadow="never">
        <template #header>
          <h2>日志类型与版本</h2>
        </template>
        <RequestState
          :loading="types.loading.value"
          :error="types.error.value"
          :empty="types.data.value?.length === 0"
          empty-text="暂无日志类型"
          @retry="loadTypes"
        >
          <el-select
            v-model="selectedType"
            placeholder="选择日志类型"
            aria-label="日志类型"
            :disabled="busy"
            style="width: 300px"
          >
            <el-option
              v-for="item in types.data.value"
              :key="item.logType"
              :value="item.logType"
              :label="`${item.displayName} · ${item.logType}`"
            />
          </el-select>
          <p v-if="selectedType">
            {{ types.data.value?.find(item => item.logType === selectedType)?.description || '此日志类型暂无描述' }}
          </p>
        </RequestState>
        <el-collapse>
          <el-collapse-item
            title="注册日志类型"
            name="create"
          >
            <el-space wrap>
              <el-input
                v-model="newType"
                placeholder="logType"
                aria-label="新日志类型标识"
              /><el-input
                v-model="displayName"
                placeholder="显示名称"
                aria-label="日志类型显示名称"
              /><el-button
                :disabled="!can('parser:release:write') || !newType.trim() || !displayName.trim() || busy"
                @click="action(async () => { await parserApi.createLogType({ logType: newType.trim(), displayName: displayName.trim() }); await loadTypes() }, '日志类型已注册。')"
              >
                注册
              </el-button>
            </el-space>
          </el-collapse-item>
        </el-collapse>
      </el-card>
      <el-card
        v-if="selectedType"
        shadow="never"
      >
        <RequestState
          :loading="binding.loading.value"
          :error="binding.error.value"
          @retry="loadBinding"
        >
          <p>
            当前活动版本：<el-tag>{{ binding.data.value?.releaseId ?? '无活动绑定' }}</el-tag><el-button
              text
              @click="loadBinding"
            >
              刷新绑定
            </el-button>
          </p>
        </RequestState>
        <el-tabs v-model="tab">
          <el-tab-pane
            label="发布版本 / 详情"
            name="releases"
          >
            <RequestState
              :loading="releases.loading.value"
              :error="releases.error.value"
              :empty="releases.data.value?.length === 0"
              empty-text="暂无版本，请创建草稿"
              @retry="loadReleases"
            >
              <el-table
                :data="releases.data.value"
                row-key="releaseId"
                highlight-current-row
                @current-change="(row: ParserRelease | null) => { if (row && !busy) selectedRelease = row.releaseId }"
              >
                <el-table-column
                  prop="releaseId"
                  label="发布版本 ID"
                /><el-table-column
                  prop="releaseVersion"
                  label="版本"
                /><el-table-column label="状态">
                  <template #default="{ row }">
                    <ManagementStatus :status="row.state" />
                  </template>
                </el-table-column><el-table-column
                  prop="createdAt"
                  label="创建时间（本地）"
        :formatter="(row: ParserRelease) => formatLocalTime(row.createdAt)"
                />
              </el-table>
            </RequestState>
            <RequestState
              v-if="selectedRelease"
              :loading="detail.loading.value"
              :error="detail.error.value"
              @retry="loadDetail"
            >
              <template v-if="detail.data.value">
                <h3>{{ detail.data.value.releaseId }}</h3><pre>{{ JSON.stringify(detail.data.value.coordinate, null, 2) }}</pre><el-space wrap>
                  <el-button
                    :disabled="!can('parser:release:write') || busy || detail.data.value.state !== 'DRAFT'"
                    @click="validate"
                  >
                    样例验证
                  </el-button><el-button
                    :disabled="!can('parser:release:write') || busy || detail.data.value.state !== 'DRAFT' || !(validation?.passed || detail.data.value.validation?.passed)"
                    @click="publish"
                  >
                    发布
                  </el-button><el-button
                    type="primary"
                    :disabled="!can('parser:release:write') || busy || !published || !binding.data.value || binding.loading.value"
                    @click="changeBinding()"
                  >
                    激活版本
                  </el-button><el-button
                    type="danger"
                    plain
                    :disabled="!can('parser:release:write') || busy || !binding.data.value?.releaseId || binding.loading.value"
                    @click="changeBinding(true)"
                  >
                    撤销活动绑定
                  </el-button>
                </el-space><pre v-if="validation || detail.data.value.validation">{{ JSON.stringify(validation || detail.data.value.validation, null, 2) }}</pre>
              </template>
            </RequestState>
          </el-tab-pane>
          <el-tab-pane
            label="草稿 JSON / 文件"
            name="draft"
          >
            <p>输入完整 ReleaseRequest JSON，上限 32 MiB</p><el-button
              class="management-file-button"
              :disabled="busy || !can('parser:release:write')"
              @click="draftFile?.click()"
            >
              选择草稿 JSON 文件
            </el-button><input
              ref="draftFile"
              class="management-file-input"
              type="file"
              accept=".json,application/json"
              aria-label="导入草稿 JSON 文件"
              :disabled="busy"
              @change="importFile"
            ><el-input
              v-model="draft"
              type="textarea"
              :rows="14"
              aria-label="草稿 JSON"
              placeholder="粘贴已审查的 ReleaseRequest JSON"
            /><el-button
              type="primary"
              :disabled="!can('parser:release:write') || busy || !draft.trim()"
              @click="saveDraft"
            >
              保存草稿
            </el-button>
          </el-tab-pane>
          <el-tab-pane
            label="离线文本解析"
            name="offline"
          >
            <el-alert
              title="仅提交解析任务，不连接设备。请粘贴符合所选 adapter 的 JSON，不是任意原始日志文本。默认上限 8 MiB UTF-8，实际以部署为准。"
              :closable="false"
            /><p>留空使用当前活动版本，上限 {{ inputLimit }} 字节。</p><el-collapse
              class="parser-input-example"
            >
              <el-collapse-item
                title="输入示例"
                name="example"
              >
                <p>command-output-block/v1，stdout 为实际日志：</p><pre>{ "schemaVersion": "1.0.0", "commandBlocks": [{ "commandIndex": 0, "commandText": "show version", "status": "SUCCEEDED", "stdout": "设备输出", "stderr": "" }] }</pre>
              </el-collapse-item>
            </el-collapse><el-select
              v-model="selectedRelease"
              clearable
              placeholder="使用活动版本"
              aria-label="离线解析版本"
            >
              <el-option
                v-for="item in releases.data.value?.filter(r => r.state === 'PUBLISHED')"
                :key="item.releaseId"
                :label="item.releaseVersion"
                :value="item.releaseId"
              />
            </el-select><el-input
              v-model="inputFormat"
              aria-label="输入适配器格式"
              placeholder="inputFormat 必须匹配 release manifest.inputAdapter"
            /><el-input
              v-model="text"
              type="textarea"
              :rows="10"
              aria-label="离线日志文本"
            /><el-button
              type="primary"
              :disabled="!can('parser:task:create') || busy || !inputFormat.trim() || !!inputError(text, inputLimit)"
              @click="submitText"
            >
              提交离线解析
            </el-button><p v-if="taskId">
              {{ taskId }} · {{ taskState }} <el-button
                :loading="busy"
                @click="refreshTask"
              >
                刷新解析状态 / 结果
              </el-button>
            </p><SemanticResultPanel
              v-if="result.length"
              :results="result"
            />
          </el-tab-pane>
        </el-tabs>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="row">
            <h2>解析运行环境</h2><el-button @click="loadRuntime">
              刷新运行状态
            </el-button>
          </div>
        </template><RequestState
          :loading="runtime.loading.value"
          :error="runtime.error.value"
          @retry="loadRuntime"
        >
          <template v-if="runtime.data.value">
            <el-descriptions
              :column="2"
              border
            >
              <el-descriptions-item label="工作节点">
                {{ runtime.data.value.workerId }}
              </el-descriptions-item><el-descriptions-item label="最近心跳（本地）">
                {{ formatLocalTime(runtime.data.value.latestHeartbeatAt) }}
              </el-descriptions-item><el-descriptions-item label="执行 / 排队">
                {{ runtime.data.value.executorActiveCount }} / {{ runtime.data.value.executorQueuedCount }}
              </el-descriptions-item><el-descriptions-item label="可用执行槽">
                {{ runtime.data.value.executorAvailableSlots }}
              </el-descriptions-item>
            </el-descriptions><pre>{{ JSON.stringify({ supportedCoordinates: runtime.data.value.supportedCoordinates, waitingCounts: runtime.data.value.waitingCounts }, null, 2) }}</pre>
          </template>
        </RequestState>
      </el-card>
    </div>
  </AppShell>
</template>
