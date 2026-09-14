<template>
  <div>
    <ContentWrap>
      <div class="integration-toolbar">
        <el-button
          v-hasPermi="['pms:integration:configure']"
          type="primary"
          plain
          @click="openEditor()"
          ><Icon icon="ep:plus" class="mr-5px" />新建任务</el-button
        >
        <el-button :loading="loading" @click="load"
          ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
        >
      </div>
    </ContentWrap>
    <ContentWrap>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
      <el-table
        v-loading="loading"
        :data="rows"
        class="integration-table"
        empty-text="暂无任务，可通过“新建任务”配置数据迁移与同步"
      >
        <el-table-column prop="name" label="任务" min-width="180" />
        <el-table-column label="来源系统" min-width="100"
          ><template #default="{ row }">{{
            row.definition.sourceSystem
          }}</template></el-table-column
        >
        <el-table-column label="运行方式" min-width="110"
          ><template #default="{ row }">{{ modes[row.definition.mode] }}</template></el-table-column
        >
        <el-table-column label="调度状态" width="110"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              row.enabled ? '已启用' : '已暂停'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="最近成功同步" min-width="170"
          ><template #default="{ row }">{{
            formatDate(row.checkpoint) || '尚未成功同步'
          }}</template></el-table-column
        >
        <el-table-column label="操作" min-width="280"
          ><template #default="{ row }">
            <div class="integration-row-actions">
              <el-button
                v-hasPermi="['pms:integration:configure']"
                link
                type="primary"
                :disabled="!!row.activeRunId"
                @click="openEditor(row.id)"
                >配置</el-button
              >
              <el-button
                v-hasPermi="['pms:integration:execute']"
                link
                type="primary"
                :disabled="!!row.activeRunId"
                @click="run(row, true)"
                >全量预览</el-button
              >
              <el-button
                v-hasPermi="['pms:integration:execute']"
                link
                type="primary"
                :disabled="!!row.activeRunId"
                @click="run(row, false)"
                >执行同步</el-button
              >
              <el-button
                v-if="row.definition.adapter === 'EHR_ORGANIZATION'"
                v-hasPermi="['pms:integration:adopt']"
                link
                type="warning"
                :disabled="!!row.activeRunId"
                @click="adopt(row)"
                >接管已有组织</el-button
              >
              <el-button
                v-hasPermi="['pms:integration:schedule']"
                link
                type="primary"
                @click="toggle(row)"
                >{{ row.enabled ? '暂停' : '启用' }}</el-button
              >
              <el-button v-if="row.activeRunId" link @click="emit('openRun', row.activeRunId)"
                >查看运行</el-button
              >
            </div>
          </template></el-table-column
        >
      </el-table>
      <div class="integration-pagination"
        ><Pagination
          v-model:page="page.pageNo"
          v-model:limit="page.pageSize"
          :total="total"
          @pagination="load"
      /></div>
    </ContentWrap>
    <TaskEditor ref="editor" @saved="load" @closed="closeEditor" @open-existing="openEditor" />
    <el-dialog v-model="adoptVisible" title="显式接管已有组织" width="min(600px, 95vw)">
      <p
        >先核对来源编码、名称、启停、排序和父级关系。只有全部一致的本地组织才能接管；目标 ID
        保持不变，不接管其他来源受管记录。</p
      >
      <el-alert
        title="接管后仍可人工修改；下次同步将按上游值覆盖同步字段。电话、邮箱和负责人等本地字段保留，受管组织不可删除。"
        type="warning"
        :closable="false"
        class="mt-4"
      />
      <template #footer
        ><el-button :loading="submitting" @click="adoptRun(true)">接管预检</el-button
        ><el-button type="primary" :loading="submitting" @click="adoptRun(false)"
          >确认接管</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
import TaskEditor from './TaskEditor.vue'
import { replaceIntegrationUrlState } from './urlState'
import { formatDate } from '@/utils/formatTime'
const emit = defineEmits<{ openRun: [id: api.Id] }>()
const message = useMessage()
const rows = ref<api.Task[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref(''),
  submitting = ref(false)
const page = reactive({ pageNo: 1, pageSize: 20 }),
  editor = ref<InstanceType<typeof TaskEditor>>()
const adoptVisible = ref(false),
  selected = ref<api.Task>()
const modes: Record<string, string> = {
  ONCE: '一次迁移',
  SNAPSHOT: '全量快照',
  INCREMENTAL: '时间增量'
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const p = await api.getTasks(page)
    rows.value = p.list
    total.value = p.total
  } catch {
    error.value = '任务加载失败，请刷新重试。'
  } finally {
    loading.value = false
  }
}
const run = async (task: api.Task, preview: boolean) => {
  const confirmPreparation =
    !!(task.definition.clearBeforeLoad || task.definition.resetMappingsBeforeLoad) && !preview
  if (confirmPreparation) {
    try {
      await message.confirm(
        task.definition.clearBeforeLoad
          ? '将清空当前租户的全部公司、部门及目标同步映射，再重新加载来源。旧 ID 和本地字段不保留；其他受影响任务将暂停。确认执行？'
          : '只重置当前任务的来源映射，保留全部目标记录，再按源主键追加更新。编码与归属冲突仍会整批失败；失败保留旧映射。确认执行？',
        task.definition.clearBeforeLoad ? '确认清空全部目标组织' : '确认仅重置映射'
      )
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      throw error
    }
  }
  const id = await api.startRun(
    task,
    preview,
    false,
    undefined,
    preview || task.definition.mode !== 'INCREMENTAL',
    confirmPreparation
  )
  emit('openRun', id)
}
const toggle = async (task: api.Task) => {
  await api.schedule(task, !task.enabled)
  message.success(task.enabled ? '调度已暂停' : '调度已启用')
  await load()
}
const adopt = (task: api.Task) => {
  selected.value = task
  adoptVisible.value = true
}
const adoptRun = async (preview: boolean) => {
  if (!selected.value) return
  submitting.value = true
  try {
    const id = await api.startRun(selected.value, preview, true)
    adoptVisible.value = false
    emit('openRun', id)
  } finally {
    submitting.value = false
  }
}
// URL state is maintained through history.replaceState; Vue Router's initial query can be stale after tab changes.
const editorTaskId = ref(new URL(window.location.href).searchParams.get('task') || undefined)
const openEditor = async (id?: api.Id) => {
  editorTaskId.value = String(id || 'new')
  replaceIntegrationUrlState({ task: editorTaskId.value })
  await nextTick()
  await editor.value?.open(id)
}
const closeEditor = () => {
  editorTaskId.value = undefined
  replaceIntegrationUrlState({ task: undefined })
}
onMounted(async () => {
  await load()
  if (editorTaskId.value)
    await editor.value?.open(editorTaskId.value === 'new' ? undefined : editorTaskId.value)
})
</script>
