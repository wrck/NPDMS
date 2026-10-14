<template>
  <section class="parent-scope-panel" aria-label="项目订单范围">
    <header class="project-context">
      <span>{{ projectCode }}</span><strong>{{ projectName }}</strong>
      <small v-if="departmentName">{{ departmentName }}</small>
    </header>
    <ProjectContractScopeSyncPanel v-if="view === 'sync'" :project-id="projectContext.projectId"
      @cancel="view = 'list'" @success="saved" @busy="value => emit('busy', value)" />
    <template v-else>
      <div v-if="view === 'list'" class="list-heading">
        <div><h3>本项目订单范围</h3><p>{{ isRoot ? '可按已关联合同同步全部订单，拆分后显示本项目剩余数量。' : '范围由父项目拆分确定，可在当前范围内继续划分子项目。' }}</p></div>
        <el-button v-if="isRoot" type="primary" v-hasPermi="['pms:project:update']" :disabled="loading || !!error" @click="view = 'sync'">同步合同订单</el-button>
      </div>
      <div v-else class="history-heading">
        <el-button link @click="backToList">〈 返回订单范围</el-button>
        <h3>分配历史</h3><p>{{ selectedScope?.orderNo }} / 行 {{ selectedScope?.lineNo }} · {{ selectedScope?.productCode }}</p>
      </div>
      <div v-if="view === 'list'" class="scope-toolbar">
        <el-input v-model="keyword" clearable aria-label="搜索父项目订单范围" placeholder="搜索订单号、行号或产品" :disabled="loading" />
        <el-checkbox v-model="includeHistory" :disabled="loading" @change="load">包含历史</el-checkbox>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon class="scope-error">
        <el-button link type="primary" @click="load">重新加载</el-button>
      </el-alert>
      <el-skeleton v-if="loading" :rows="5" animated class="scope-loading" />
      <template v-else-if="!error">
        <div class="list-caption"><span>{{ view === 'history' ? '历史记录' : '范围明细' }}</span><span>{{ filteredRows.length }} 条记录</span></div>
        <div v-if="pagedRows.length" class="scope-list">
          <article v-for="row in pagedRows" :key="row.id" class="scope-record" :class="{ 'scope-record--past': !isCurrent(row) }">
            <header class="record-header"><div><strong>{{ row.orderNo }}</strong><span>行 {{ row.lineNo }}</span></div>
              <el-tag :type="row.scopeStatus === 'ACTIVE' ? 'success' : row.scopeStatus === 'CONFLICT_FROZEN' ? 'warning' : 'info'" size="small" effect="plain">{{ scopeStatusLabel(row.scopeStatus) }}</el-tag></header>
            <div class="record-main"><div class="record-product"><h4>{{ row.productCode || '未提供产品编码' }}</h4><p v-if="row.productDesc">{{ row.productDesc }}</p></div>
              <div class="record-quantity"><span>分配数量</span><strong>{{ row.allocatedQuantity }}</strong></div></div>
            <dl class="record-facts"><div><dt>归属办事处</dt><dd>{{ row.departmentName || row.departmentCode || '未提供' }}</dd></div>
              <div><dt>生效时间</dt><dd>{{ formatDate(row.effectiveFrom) || '未提供' }}</dd></div>
              <div v-if="row.effectiveTo"><dt>结束时间</dt><dd>{{ formatDate(row.effectiveTo) }}</dd></div>
              <div v-if="row.contractNo"><dt>合同号</dt><dd>{{ row.contractNo }}</dd></div></dl>
            <p v-if="row.changeReason" class="change-reason">{{ row.changeReason }}</p>
            <details v-if="row.details.length" class="scope-details">
              <summary>查看范围明细 <span>{{ row.details.length }} 项</span></summary>
              <ul><li v-for="detail in row.details" :key="detail.id"><span>{{ detail.serialNo ? `序列号 ${detail.serialNo}` : detail.productCode || detail.deviceTypeName || '产品范围' }}</span><span>数量 {{ detail.allocatedQuantity }}</span></li></ul>
            </details>
            <footer class="record-footer"><span>第 {{ row.allocationVersion }} 版分配</span>
              <div v-if="view === 'list'">
                <el-button link @click="history(row)">分配历史</el-button>
              </div></footer>
          </article>
        </div>
        <div v-else class="scope-empty"><strong>{{ keyword ? '没有找到匹配的订单范围' : '当前没有本项目可用的订单范围' }}</strong>
          <p>{{ keyword ? '换一个订单号、行号或产品关键词，或清空搜索条件。' : isRoot ? '可同步已关联合同的最新订单，或查看历史分配记录。' : '请核对父项目拆分结果，或查看历史分配记录。' }}</p>
          <el-button v-if="keyword" @click="keyword = ''">清空搜索</el-button></div>
        <el-pagination v-if="filteredRows.length > 10" v-model:current-page="page" :page-size="10" :total="filteredRows.length" layout="prev, pager, next" :pager-count="5" class="scope-pagination" />
      </template>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import * as CommerceApi from '@/api/pms/commerce'
import { getParentScopes, scopeStatusLabel, type ParentScope } from '@/api/pms/project/project-splits/parent-scope'
import type { ProjectRouteContext } from '@/views/pms/commerce/commerceInteraction'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import ProjectContractScopeSyncPanel from './ProjectContractScopeSyncPanel.vue'
import { splitErrorMessage } from './useProjectSplitWorkspace'
const props = defineProps<{ projectContext: ProjectRouteContext; isRoot: boolean; projectCode?: string; projectName?: string; departmentName?: string }>()
const emit = defineEmits<{ changed: []; busy: [value: boolean] }>()
const view = ref<'list' | 'sync' | 'history'>('list'), selectedScope = ref<ParentScope>()
const rows = ref<ParentScope[]>([]), loading = ref(false), error = ref('')
const keyword = ref(''), includeHistory = ref(false), page = ref(1)
const message = useMessage()
let loadGeneration = 0
const isCurrent = (row: ParentScope) => !row.effectiveTo && row.scopeStatus !== 'RELEASED'
const filteredRows = computed(() => {
  const search = view.value === 'history' ? '' : keyword.value.trim().toLocaleLowerCase()
  return rows.value.filter(row => !search || [row.orderNo, row.lineNo, row.productCode, row.productDesc].some(value => value?.toLocaleLowerCase().includes(search)))
})
const pagedRows = computed(() => filteredRows.value.slice((page.value - 1) * 10, page.value * 10))
watch(keyword, () => { page.value = 1 })
const load = async () => {
  const generation = ++loadGeneration
  loading.value = true; error.value = ''; rows.value = []; page.value = 1
  const projectId = props.projectContext.projectId
  try {
    const versionBefore = await CommerceApi.getDeliveryScopeVersion(projectId)
    const result = await getParentScopes(projectId, view.value === 'history' ? selectedScope.value?.orderLineId : undefined, view.value === 'history' || includeHistory.value)
    const versionAfter = await CommerceApi.getDeliveryScopeVersion(projectId)
    if (generation !== loadGeneration) return
    if (versionBefore !== versionAfter) throw new Error('订单范围正在更新，请重新加载后操作')
    rows.value = result
  } catch (failure) { if (generation === loadGeneration) error.value = splitErrorMessage(failure) }
  finally { if (generation === loadGeneration) loading.value = false }
}
const history = (scope: ParentScope) => { selectedScope.value = scope; view.value = 'history'; load() }
const backToList = () => { view.value = 'list'; load() }
const saved = () => { message.success('合同订单范围已同步，原分配记录保留'); emit('changed'); backToList() }
watch(() => props.projectContext, () => { view.value = 'list'; selectedScope.value = undefined; keyword.value = ''; load() }, { immediate: true })
onBeforeUnmount(() => { loadGeneration++; emit('busy', false) })
</script>

<style scoped>
.parent-scope-panel { --scope-ink: var(--el-text-color-primary, #243348); --scope-muted: var(--el-text-color-secondary, #64748b); color: var(--scope-ink); font-family: "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", sans-serif; container-type: inline-size; min-width: 0; }
.project-context { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px 12px; padding: 0 0 18px; margin-bottom: 24px; border-bottom: 1px solid var(--el-border-color-lighter); }
.project-context span, .project-context small { color: var(--scope-muted); font-size: 12px; }
.project-context strong { font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.list-heading { display: flex; gap: 20px; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
.list-heading h3, .history-heading h3 { font-size: 19px; font-weight: 600; margin: 0 0 8px; }
.list-heading p, .history-heading p { font-size: 13px; line-height: 1.7; color: var(--scope-muted); margin: 0; }
.list-heading > .el-button { flex: none; }
.history-heading { margin-bottom: 20px; }
.history-heading h3 { margin-top: 18px; }
.scope-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.scope-toolbar > .el-input { flex: 1; min-width: 0; }
.scope-toolbar > .el-checkbox { margin: 0; flex: none; }
.scope-error { margin-bottom: 20px; }
.scope-loading { padding: 24px 0; }
.list-caption { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: var(--scope-muted); margin-bottom: 12px; }
.scope-list { display: grid; gap: 16px; }
.scope-record { border: 1px solid var(--el-border-color); border-radius: 5px; overflow: hidden; }
.record-header { padding: 12px 18px; background: var(--el-fill-color-lighter); display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.record-header > div { display: flex; align-items: baseline; gap: 12px; min-width: 0; flex-wrap: wrap; }
.record-header strong { font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.record-header > div > span { font-size: 12px; color: var(--scope-muted); }
.record-header > .el-tag { flex: none; }
.record-main { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; padding: 18px; }
.record-product { min-width: 0; }
.record-product h4 { font-size: 16px; margin: 0 0 6px; font-weight: 600; overflow-wrap: anywhere; }
.record-product p { margin: 0; color: var(--scope-muted); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.record-quantity { display: flex; flex-direction: column; gap: 6px; text-align: right; flex: none; }
.record-quantity span { color: var(--scope-muted); font-size: 12px; }
.record-quantity strong { font-size: 26px; line-height: 1.2; font-weight: 600; font-variant-numeric: tabular-nums; }
.record-facts { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); padding: 0 18px; gap: 14px 24px; margin: 0 0 16px; }
.record-facts dt { font-size: 12px; color: var(--scope-muted); margin-bottom: 5px; }
.record-facts dd { font-size: 13px; margin: 0; line-height: 1.5; overflow-wrap: anywhere; }
.change-reason { margin: 0 18px 14px; font-size: 12px; line-height: 1.7; color: var(--scope-muted); overflow-wrap: anywhere; }
.scope-details { margin: 0 18px 14px; font-size: 12px; }
.scope-details summary { cursor: pointer; width: fit-content; line-height: 24px; color: var(--el-color-primary); }
.scope-details summary span { color: var(--scope-muted); margin-left: 8px; }
.scope-details summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 3px; }
.scope-details ul { padding: 0; list-style: none; margin: 8px 0 0; }
.scope-details li { display: flex; justify-content: space-between; gap: 12px; padding: 6px 0; border-top: 1px solid var(--el-border-color-lighter); overflow-wrap: anywhere; }
.scope-details li > span:last-child { flex: none; }
.record-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 18px; border-top: 1px solid var(--el-border-color-lighter); }
.record-footer > span { color: var(--scope-muted); font-size: 12px; }
.record-footer > div { display: flex; gap: 14px; flex-wrap: wrap; }
.record-footer :deep(.el-button + .el-button) { margin-left: 0; }
.scope-record--past .record-header { background: var(--el-fill-color-blank); }
.scope-empty { padding: 40px 20px; border: 1px dashed var(--el-border-color); text-align: center; }
.scope-empty strong { font-size: 14px; font-weight: 500; }
.scope-empty p { color: var(--scope-muted); font-size: 13px; line-height: 1.7; }
.scope-pagination { justify-content: flex-end; margin-top: 20px; }
@container (max-width: 520px) { .list-heading { flex-direction: column; gap: 14px; } .list-heading > .el-button { width: 100%; } .scope-toolbar { flex-wrap: wrap; gap: 8px 12px; } .scope-toolbar > .el-input { flex-basis: 100%; } .scope-toolbar > .el-button { margin-left: auto; } .record-footer { align-items: flex-start; flex-direction: column; gap: 10px; } .record-footer > div { width: 100%; justify-content: flex-end; } .record-header, .record-main, .record-footer { padding-left: 14px; padding-right: 14px; } .record-facts { padding: 0 14px; gap: 12px; } .record-main { gap: 14px; } .record-quantity strong { font-size: 24px; } }
@container (max-width: 330px) { .record-facts { grid-template-columns: 1fr; } .record-footer > div { justify-content: space-between; gap: 8px; } }
</style>
