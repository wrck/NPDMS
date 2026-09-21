<script setup lang="ts">
import { formatLocalTime, statusLabel } from '@/management/presentation'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { scheduleApi, parseSchedule, type Schedule } from '@/api/schedule-management'
import { managementApi } from '@/api/management'
import { createRequest, safeError } from '@/management/use-request'
import { useAccess } from '@/management/access'
import RequestState from './RequestState.vue'
const { can } = useAccess()
const namespace = ref(''), project = ref(''), key = ref(''), json = ref(''), busy = ref(false), message = ref('')
const request = createRequest<Schedule[]>(), settings = createRequest<Awaited<ReturnType<typeof managementApi.settings>>>()
let generation = 0
watch([namespace, project], () => { generation++; request.clear(); json.value = ''; key.value = ''; message.value = '' })
onBeforeUnmount(() => generation++)
const load = () => request.run(signal => scheduleApi.list(project.value, namespace.value, signal))
function edit(row: Schedule) {
  key.value = row.scheduleKey
  json.value = JSON.stringify({ namespace: row.namespace, projectKey: row.projectKey, projectHint: row.projectHint, deviceKeyHints: row.deviceKeyHints, scriptKey: row.scriptKey, scriptVersion: row.scriptVersion, cron: row.cron, timezone: row.timezone, callbackUri: row.callbackUri, enabled: row.enabled }, null, 2)
}
function create() { key.value = ''; json.value = JSON.stringify({ namespace: namespace.value, projectKey: project.value, projectHint: '', deviceKeyHints: [], scriptKey: '', scriptVersion: '', cron: '0 0 * * * *', timezone: 'UTC', callbackUri: '', enabled: false }, null, 2) }
async function save(disable?: Schedule) {
  if (busy.value) return
  const current = generation, p = project.value, n = namespace.value, scheduleKey = disable?.scheduleKey || key.value
  let payload
  try { if (!disable) payload = parseSchedule(json.value, p, n) } catch (cause) { message.value = cause instanceof Error ? cause.message : '无效调度 JSON'; return }
  try { await ElMessageBox.confirm(`${disable ? '停用' : '保存'}到期通知 ${scheduleKey}？这不是无人值守 SSH 任务。首次保存强制停用。`, '确认调度变更', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }) } catch { return }
  if (generation !== current) return
  busy.value = true; message.value = ''
  try { if (disable) await scheduleApi.disable(p, n, scheduleKey); else await scheduleApi.save(p, scheduleKey, payload!, Boolean(request.data.value?.some(row => row.scheduleKey === scheduleKey))); if (generation === current) { await load(); message.value = '已保存，请核对启用状态。' } }
  catch (cause) { if (generation === current) message.value = safeError(cause) }
  finally { busy.value = false }
}
onMounted(() => settings.run(managementApi.settings))
</script>
<template>
  <el-alert
    title="SCHEDULE_DUE 仅向回调发送到期通知，不执行无人值守 SSH；首次创建强制停用，之后可显式启用。"
    type="info"
    :closable="false"
    show-icon
  />
  <RequestState
    :loading="settings.loading.value"
    :error="settings.error.value"
    @retry="settings.run(managementApi.settings)"
  >
    <el-alert
      v-if="settings.data.value && !settings.data.value.capabilities.scheduleEnabled"
      title="当前部署未启用调度派发：保存的计划不会发送到期通知。"
      type="warning"
      :closable="false"
    />
  </RequestState>
  <el-form
    inline
    @submit.prevent="load"
  >
    <el-form-item label="命名空间">
      <el-input
        v-model="namespace"
        aria-label="调度 命名空间"
      />
    </el-form-item><el-form-item label="项目">
      <el-input
        v-model="project"
        aria-label="调度项目"
      />
    </el-form-item><el-button
      native-type="submit"
      :disabled="!namespace.trim() || !project.trim() || busy"
    >
      查询调度
    </el-button><el-button
      :disabled="!request.data.value || busy || !can('device-ops:collections:execute')"
      @click="create"
    >
      新建通知
    </el-button>
  </el-form>
  <RequestState
    :loading="request.loading.value"
    :error="request.error.value"
    :empty="request.data.value?.length === 0"
    empty-text="当前项目暂无调度通知"
    @retry="load"
  >
    <el-table
      v-if="request.data.value"
      :data="request.data.value"
    >
      <el-table-column
        prop="scheduleKey"
        label="计划标识"
      /><el-table-column
        prop="cron"
        label="Cron"
      /><el-table-column
        prop="timezone"
        label="时区"
      /><el-table-column label="启用状态">
        <template #default="{ row }">
          {{ row.enabled ? '启用' : '停用' }}
        </template>
      </el-table-column><el-table-column
        prop="nextRunAt"
        label="下次到期（本地）"
        :formatter="(row: Schedule) => formatLocalTime(row.nextRunAt)"
      /><el-table-column
        prop="lastStatus"
        label="最近状态"
        :formatter="(row: Schedule) => statusLabel(row.lastStatus)"
      /><el-table-column label="操作">
        <template #default="{ row }">
          <el-button
            text
            :disabled="busy || !can('device-ops:collections:execute')"
            @click="edit(row)"
          >
            编辑
          </el-button><el-button
            text
            :disabled="busy || !row.enabled || !can('device-ops:collections:execute')"
            @click="save(row)"
          >
            停用
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </RequestState>
  <el-alert
    v-if="message"
    :title="message"
    :closable="false"
  />
  <div v-if="json">
    <el-input
      v-model="key"
      aria-label="调度标识"
      placeholder="scheduleKey"
    /><el-input
      v-model="json"
      type="textarea"
      :rows="15"
      aria-label="调度请求 JSON"
    /><el-button
      type="primary"
      :disabled="!key.trim() || busy || !can('device-ops:collections:execute')"
      @click="save()"
    >
      确认保存通知
    </el-button>
  </div>
</template>
