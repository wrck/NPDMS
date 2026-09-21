<script setup lang="ts">
import { statusLabel, formatLocalTime } from '@/management/presentation'
import ManagementStatus from '@/components/management/ManagementStatus.vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useAccess } from '@/management/access'
import { parserApi } from '@/api/parser-management'
import { createRequest, safeError } from '@/management/use-request'
import type { ParseTask } from '@/types/management'
import type { CollectionSemanticResult } from '@/types/parser'
import RequestState from './RequestState.vue'
import SemanticResultPanel from '@/components/SemanticResultPanel.vue'
const { data, loading, error, run } = createRequest<ParseTask[]>()
const { can } = useAccess()
const selectedTask = ref<ParseTask>()
const results = createRequest<CollectionSemanticResult[]>()
const filter = ref(''), cursor = ref<string>(), history = ref<(string | undefined)[]>([]), busy = ref(false), actionError = ref('')
const visible = computed(() => data.value?.filter(row => !filter.value || row.state === filter.value))
let generation = 0
onBeforeUnmount(() => generation++)
const load = () => { generation++; results.clear(); return run(signal => parserApi.tasks(cursor.value, signal)) }
function next() { if (!data.value?.length) return; history.value.push(cursor.value); cursor.value = data.value.at(-1)?.taskId; void load() }
function previous() { cursor.value = history.value.pop(); void load() }
async function change(row: ParseTask, terminate: boolean) {
  if (busy.value) return
  const current = generation
  try { await ElMessageBox.confirm(`${terminate ? '终止等待' : '取消解析'}任务 ${row.taskId}？此操作不可撤销。`, '确认解析任务操作', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '返回' }) } catch { return }
  if (current !== generation) return
  busy.value = true; actionError.value = ''
  try { if (terminate) await parserApi.terminate(row.taskId); else await parserApi.cancel(row.taskId); await load() }
  catch (cause) { if (current === generation) actionError.value = safeError(cause) }
  finally { busy.value = false }
}
function showResult(row: ParseTask) { selectedTask.value = row; void results.run(async signal => [{ targetId: 0, taskId: row.taskId, state: row.state, releaseId: row.releaseId, coordinate: row.coordinate, result: await parserApi.result(row.taskId, signal) }]) }
onMounted(load)
</script>
<template>
  <el-space wrap>
    <el-select
      v-model="filter"
      clearable
      placeholder="本页状态筛选"
      aria-label="解析任务本页状态筛选"
      style="width:200px"
    >
      <el-option
        v-for="state in ['QUEUED','RUNNING','WAITING','SUCCEEDED','FAILED','CANCELLED']"
        :key="state"
        :value="state"
        :label="statusLabel(state)"
      />
    </el-select><el-button
      :disabled="busy"
      @click="load"
    >
      刷新
    </el-button><el-text type="info">
      接口使用游标分页，状态筛选仅作用于当前页。
    </el-text>
  </el-space>
  <el-alert
    v-if="actionError"
    :title="actionError"
    type="error"
    :closable="false"
  />
  <RequestState
    :loading="loading"
    :error="error"
    :empty="visible?.length === 0"
    empty-text="暂无匹配解析任务"
    @retry="load"
  >
    <el-table
      :data="visible"
      row-key="taskId"
    >
      <el-table-column
        prop="taskId"
        label="任务 ID"
        min-width="180"
      /><el-table-column
        prop="logType"
        label="日志类型"
      /><el-table-column label="状态">
        <template #default="{ row }">
          <ManagementStatus :status="row.state" />
        </template>
      </el-table-column><el-table-column
        label="等待 / 错误信息"
        min-width="190"
      >
        <template #default="{ row }">
          {{ row.waitReason || (row.state === 'FAILED' ? '解析失败；接口未提供详细错误' : '—') }}
        </template>
      </el-table-column><el-table-column
        prop="createdAt"
        label="创建时间（本地）"
        :formatter="(row: ParseTask) => formatLocalTime(row.createdAt)"
        min-width="180"
      /><el-table-column
        label="操作"
        min-width="180"
      >
        <template #default="{ row }">
          <el-button
            v-if="['QUEUED','RUNNING','WAITING'].includes(row.state)"
            text
            :disabled="busy || !can('parser:task:cancel')"
            @click="change(row, false)"
          >
            取消解析
          </el-button><el-button
            v-if="row.state === 'WAITING'"
            type="danger"
            text
            :disabled="busy || !can('parser:task:terminate')"
            @click="change(row, true)"
          >
            终止等待
          </el-button><el-button
            v-if="row.state === 'SUCCEEDED'"
            text
            @click="showResult(row)"
          >
            语义结果
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </RequestState>
  <el-space class="pagination">
    <el-button
      :disabled="!history.length || loading || busy"
      @click="previous"
    >
      上一页
    </el-button><span>第 {{ history.length + 1 }} 页</span><el-button
      :disabled="!data || data.length < 20 || loading || busy"
      @click="next"
    >
      下一页
    </el-button>
  </el-space>
  <RequestState
    :loading="results.loading.value"
    :error="results.error.value"
    @retry="selectedTask && showResult(selectedTask)"
  >
    <SemanticResultPanel
      v-if="results.data.value"
      :results="results.data.value"
    />
  </RequestState>
</template>
