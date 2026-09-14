<template>
  <div>
    <template v-if="!selected">
      <ContentWrap
        ><div class="integration-toolbar"
          ><el-button :loading="loading" @click="load"
            ><Icon icon="ep:refresh" class="mr-5px" />刷新运行记录</el-button
          ></div
        ></ContentWrap
      >
      <ContentWrap>
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
        <el-table
          v-loading="loading"
          :data="rows"
          class="integration-table"
          empty-text="暂无运行记录，完成任务配置后可进行全量预览"
          @row-click="select"
        >
          <el-table-column prop="id" label="批次" min-width="200" />
          <el-table-column label="分页" width="110"
            ><template #default="{ row }">{{
              row.pageNumber ? `第 ${row.pageNumber} 页` : row.pagingJson ? '分页主运行' : '—'
            }}</template></el-table-column
          >
          <el-table-column label="类型" width="130"
            ><template #default="{ row }">{{
              row.preview ? '完整预览' : row.fullSnapshot ? '全量同步' : '增量同步'
            }}</template></el-table-column
          >
          <el-table-column label="状态" min-width="180"
            ><template #default="{ row }">{{ runStatusLabel(row) }}</template></el-table-column
          >
          <el-table-column label="开始时间" min-width="170"
            ><template #default="{ row }">{{
              formatDate(row.startedAt)
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="100"
            ><template #default="{ row }"
              ><el-button link type="primary" @click.stop="select(row)">详情</el-button></template
            ></el-table-column
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
    </template>
    <section v-if="selected" aria-label="运行详情" aria-live="polite">
      <ContentWrap>
        <div class="integration-toolbar">
          <el-button @click="emit('back')"
            ><Icon icon="ep:back" class="mr-5px" />返回运行列表</el-button
          >
          <el-button :loading="loading" @click="refreshSelected"
            ><Icon icon="ep:refresh" class="mr-5px" />刷新状态</el-button
          >
          <el-tag
            :type="
              selected.status === 'FAILED'
                ? 'danger'
                : selected.status === 'SUCCESS'
                  ? 'success'
                  : 'info'
            "
            >{{ runStatusLabel(selected) }}</el-tag
          >
        </div>
      </ContentWrap>
      <ContentWrap title="运行详情">
        <el-descriptions :column="narrow ? 1 : 2" border class="integration-run-meta">
          <el-descriptions-item label="批次编号"
            ><span class="integration-identity">{{ selected.id }}</span></el-descriptions-item
          >
          <el-descriptions-item label="执行类型">{{
            selected.preview
              ? '完整预览（未写入）'
              : selected.fullSnapshot
                ? '全量同步'
                : '增量同步'
          }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{
            formatDate(selected.startedAt) || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{
            formatDate(selected.finishedAt) || '尚未结束'
          }}</el-descriptions-item>
          <el-descriptions-item
            :label="selected.pagingJson && !selected.pageNumber ? '已完成页行数' : '读取行数'"
            >{{ selected.readCount ?? '尚未返回' }}</el-descriptions-item
          >
          <el-descriptions-item label="处理结果">{{ summary }}</el-descriptions-item>
          <el-descriptions-item
            v-if="selected.parentRunId && !selected.pageNumber"
            label="重试来源"
          >
            <el-button link type="primary" @click="select({ id: selected.parentRunId } as api.Run)"
              >查看上次运行及已成功分页</el-button
            >
          </el-descriptions-item>
          <el-descriptions-item v-if="selected.pageNumber" label="分页归属">
            第 {{ selected.pageNumber }} 页 ·
            <el-button link type="primary" @click="select({ id: selected.parentRunId! } as api.Run)"
              >返回主运行</el-button
            >
          </el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt-3" />
        <el-alert
          v-if="selected.preview"
          title="这是完整预览结果，不代表业务数据已经写入。"
          type="info"
          :closable="false"
          class="my-3"
        />
        <el-alert
          v-if="selected.errorMessage"
          :title="selected.errorMessage"
          type="error"
          :closable="false"
          class="my-3"
        />
        <el-alert
          v-if="selected.cachePending"
          title="业务已提交，组织缓存刷新待补偿。"
          type="warning"
          :closable="false"
          class="my-3"
        />
        <el-button
          v-if="selected.status === 'FAILED' && !selected.pageNumber"
          v-hasPermi="['pms:integration:execute']"
          :loading="retrying"
          @click="retry"
          >创建关联重试</el-button
        >
      </ContentWrap>
      <ContentWrap v-if="selected.pagingJson && !selected.pageNumber" title="分页批次">
        <el-alert
          title="每页结果和来源证据独立保存；主运行失败时，已成功的页仍保留，关联重试从已提交游标继续。"
          type="info"
          :closable="false"
          class="mb-3"
        />
        <el-table :data="childPages" @row-click="select" empty-text="尚未生成分页批次">
          <el-table-column prop="pageNumber" label="页序号" width="100" />
          <el-table-column prop="id" label="批次编号" min-width="200" />
          <el-table-column prop="readCount" label="读取行数" width="110" />
          <el-table-column label="状态"
            ><template #default="{ row }">{{
              api.runLabels[row.status] || row.status
            }}</template></el-table-column
          >
          <el-table-column label="操作"
            ><template #default="{ row }"
              ><el-button link type="primary" @click.stop="select(row)"
                >查看本页明细</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <Pagination
          v-model:page="childPage.pageNo"
          v-model:limit="childPage.pageSize"
          :total="childTotal"
          @pagination="loadChanges"
        />
      </ContentWrap>
      <ContentWrap v-else :title="selected.preview ? '预计变更' : '处理明细'">
        <el-table
          :data="changes"
          class="integration-table"
          empty-text="暂无差异结果；失败批次未提交业务数据"
        >
          <el-table-column prop="object" label="对象" width="130" /><el-table-column
            prop="sourceKey"
            label="源主键"
            width="120"
          />
          <el-table-column label="结果" width="110"
            ><template #default="{ row }">{{
              api.changeLabels[row.action] || row.action
            }}</template></el-table-column
          >
          <el-table-column prop="targetId" label="目标 ID" min-width="170" />
          <el-table-column label="名称" min-width="180"
            ><template #default="{ row }">{{
              row.after?.name ?? row.after?.orderNo ?? row.before?.name ?? '—'
            }}</template></el-table-column
          >
          <el-table-column prop="message" label="说明" min-width="180" />
          <el-table-column label="操作" width="110"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="detailChange = row"
                >字段对照</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <div class="integration-pagination"
          ><Pagination
            v-model:page="changePage.pageNo"
            v-model:limit="changePage.pageSize"
            :total="changeTotal"
            @pagination="loadChanges"
        /></div>
      </ContentWrap>
    </section>
    <el-dialog
      :model-value="!!detailChange"
      title="字段对照"
      width="min(800px, calc(100vw - 32px))"
      class="integration-editor"
      @update:model-value="!$event && (detailChange = undefined)"
    >
      <template v-if="detailChange">
        <p class="integration-note mb-4"
          >{{ detailChange.object }} / {{ detailChange.sourceKey }} ·
          {{ api.changeLabels[detailChange.action] || detailChange.action }}</p
        >
        <ChangeDetails :change="detailChange" />
      </template>
      <template #footer><el-button @click="detailChange = undefined">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
import { formatDate } from '@/utils/formatTime'
import { useMediaQuery } from '@vueuse/core'
import ChangeDetails from './ChangeDetails.vue'
const narrow = useMediaQuery('(max-width: 640px)')
const detailChange = ref<api.Change>()
const emit = defineEmits<{ selected: [id: api.Id]; back: [] }>()
const props = defineProps<{ runId?: api.Id }>()
const loading = ref(false),
  error = ref(''),
  retrying = ref(false),
  rows = ref<api.Run[]>([]),
  total = ref(0)
const selected = ref<api.Run>(),
  changes = ref<api.Change[]>([]),
  changeTotal = ref(0)
const runStatusLabel = (run: api.Run) =>
  run.status === 'FAILED' && run.pagingJson && !run.pageNumber
    ? '分页停止（已成功页保留）'
    : api.runLabels[run.status] || run.status
const childPages = ref<api.Run[]>([]),
  childTotal = ref(0)
const childPage = reactive({ pageNo: 1, pageSize: 20 })
const summary = computed(() => {
  try {
    const counts = JSON.parse(selected.value?.summaryJson || '{}') as Record<string, number>
    return (
      Object.entries(counts)
        .map(
          ([key, count]) => (key === 'FAILED' ? '失败' : api.changeLabels[key] || key) + ' ' + count
        )
        .join('，') || '尚无处理结果'
    )
  } catch {
    return '尚无处理结果'
  }
})
const page = reactive({ pageNo: 1, pageSize: 20 }),
  changePage = reactive({ pageNo: 1, pageSize: 20 })
let timer: ReturnType<typeof setTimeout> | undefined
const loadChanges = async () => {
  if (!selected.value) return
  if (selected.value.pagingJson && !selected.value.pageNumber) {
    const p = await api.getRuns({ ...childPage, parentRunId: selected.value.id })
    childPages.value = p.list.filter((r) => !!r.pageNumber)
    childTotal.value = p.total
    return
  }
  const p = await api.getChanges(selected.value.id, changePage)
  changes.value = p.list
  changeTotal.value = p.total
}
const select = async (row: api.Run) => {
  selected.value = await api.getRun(row.id)
  rows.value = rows.value.map((item) => (item.id === row.id ? selected.value! : item))
  emit('selected', row.id)
  changePage.pageNo = 1
  await loadChanges()
  watchRun()
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const p = await api.getRuns(page)
    rows.value = p.list
    total.value = p.total
  } catch {
    error.value = '运行记录加载失败，请重试。'
  } finally {
    loading.value = false
  }
}
const refreshSelected = async () => {
  if (!selected.value) return
  loading.value = true
  error.value = ''
  try {
    await select(selected.value)
  } catch {
    error.value = '运行详情加载失败，请重试；不会重复提交任务。'
  } finally {
    loading.value = false
  }
}
const watchRun = () => {
  clearTimeout(timer)
  if (!selected.value || ['SUCCESS', 'FAILED', 'PREVIEW_READY'].includes(selected.value.status))
    return
  timer = setTimeout(async () => {
    try {
      selected.value = await api.getRun(selected.value!.id)
      rows.value = rows.value.map((item) =>
        item.id === selected.value?.id ? selected.value! : item
      )
      await loadChanges()
      watchRun()
    } catch {
      error.value = '状态读取失败，请刷新确认；不会自动再次提交任务。'
    }
  }, 2000)
}
const retry = async () => {
  if (!selected.value) return
  retrying.value = true
  try {
    const task = await api.getTask(selected.value.taskId)
    const confirmPreparation =
      !!(task.definition.clearBeforeLoad || task.definition.resetMappingsBeforeLoad) &&
      !selected.value.preview
    if (confirmPreparation) {
      try {
        await useMessage().confirm(
          task.definition.clearBeforeLoad
            ? '重试仍会清空当前租户全部目标公司和部门，再重新加载。确认继续？'
            : '重试会重置当前任务映射并按源主键追加更新，不清空目标表。失败保留旧映射。确认继续？',
          task.definition.clearBeforeLoad ? '确认清空重试' : '确认重置映射重试'
        )
      } catch (error) {
        if (error === 'cancel' || error === 'close') return
        throw error
      }
    }
    const id = await api.startRun(
      task,
      selected.value.preview,
      selected.value.adoptExisting,
      selected.value.id,
      selected.value.fullSnapshot,
      confirmPreparation
    )
    await select(await api.getRun(id))
    await load()
  } finally {
    retrying.value = false
  }
}
onMounted(async () => {
  await load()
  if (props.runId) await select(await api.getRun(props.runId))
})
onBeforeUnmount(() => clearTimeout(timer))
</script>
