<template>
  <section class="contract-sync" aria-label="同步合同订单范围">
    <el-button link :disabled="busy" @click="emit('cancel')">〈 返回订单范围</el-button>
    <header><h3>同步合同订单</h3><p>汇总已关联合同下的全部订单行，更新本项目尚未分出的数量。已有子项目范围和验收历史保留。</p></header>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="preview">
      <div class="sync-summary"><div><strong>{{ preview.changedLineCount }}</strong><span>个订单行有变化</span></div>
        <el-checkbox v-model="showAll" :disabled="busy">显示全部 {{ preview.lines.length }} 个订单行</el-checkbox></div>
      <el-alert v-for="code in preview.errors" :key="code" :title="contractScopeError(code)" type="warning" :closable="false" />
      <el-alert v-if="preview.valid && !preview.changedLineCount" title="订单范围已是最新，无需同步。" type="success" :closable="false" show-icon />
      <div class="sync-lines">
        <article v-for="line in shownLines" :key="line.orderLineId" class="sync-line" :class="{ 'has-error': line.errors.length }">
          <header><div><strong>{{ line.orderNo }}</strong><span>行 {{ line.lineNo }}</span></div>
            <el-tag :type="line.errors.length ? 'danger' : line.changeType === 'NO_CHANGE' ? 'info' : 'primary'" effect="plain" size="small">{{ contractScopeChangeLabel(line.changeType) }}</el-tag></header>
          <div class="line-product"><strong>{{ line.productCode || '未提供产品编码' }}</strong><p v-if="line.productDesc">{{ line.productDesc }}</p></div>
          <dl><div><dt>订单有效数量</dt><dd>{{ line.orderQuantity ?? '待确认' }}</dd></div><div><dt>其他项目已承接</dt><dd>{{ line.allocatedElsewhere }}</dd></div>
            <div><dt>本项目当前数量</dt><dd>{{ line.currentQuantity }}</dd></div><div class="proposed"><dt>同步后数量</dt><dd>{{ line.proposedQuantity ?? '待确认' }}</dd></div></dl>
          <p v-for="code in line.errors" :key="code" class="line-error">{{ contractScopeError(code) }}</p>
        </article>
      </div>
      <p v-if="!preview.valid" class="blocked-note">请先处理上方冲突，再重新预览。此次预览未修改任何项目范围。</p>
    </template>
    <footer><el-button :loading="loading" :disabled="submitting || completed" @click="refresh">重新预览</el-button>
      <el-button type="primary" :loading="submitting" :disabled="busy || completed || !preview?.valid || !preview.changedLineCount" @click="submit">确认同步 {{ preview?.changedLineCount || 0 }} 个订单行</el-button></footer>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import { contractScopeChangeLabel, contractScopeError } from '@/api/pms/project/project-splits/contract-scope'
import { useProjectContractScopeSync } from './useProjectContractScopeSync'
const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ cancel: []; success: []; busy: [value: boolean] }>()
const { preview, loading, submitting, busy, completed, error, refresh, submit } = useProjectContractScopeSync(toRef(props, 'projectId'), () => emit('success'))
const showAll = ref(false)
const shownLines = computed(() => preview.value?.lines.filter(line => showAll.value || line.changeType !== 'NO_CHANGE' || line.errors.length) || [])
watch(busy, value => emit('busy', value), { immediate: true, flush: 'sync' })
onBeforeUnmount(() => emit('busy', false))
</script>
<style scoped>
.contract-sync { container-type: inline-size; }
.contract-sync > header { margin: 20px 0; }
h3 { margin: 0 0 10px; font-size: 20px; font-weight: 600; }
.contract-sync > header p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.8; }
.contract-sync > .el-alert { margin: 16px 0; }
.sync-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 24px 0; }
.sync-summary > div { display: flex; align-items: baseline; gap: 8px; }
.sync-summary strong { font-size: 28px; font-weight: 600; font-variant-numeric: tabular-nums; }
.sync-summary span { color: var(--el-text-color-secondary); font-size: 13px; }
.sync-lines { display: grid; gap: 14px; }
.sync-line { border: 1px solid var(--el-border-color); border-radius: 5px; overflow: hidden; }
.sync-line.has-error { border-color: var(--el-color-danger-light-5); }
.sync-line > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; background: var(--el-fill-color-lighter); }
.sync-line > header > div { display: flex; flex-wrap: wrap; align-items: baseline; gap: 10px; min-width: 0; }
.sync-line > header strong { font-size: 14px; overflow-wrap: anywhere; }
.sync-line > header span { font-size: 12px; color: var(--el-text-color-secondary); }
.sync-line > header > .el-tag { flex: none; }
.line-product { padding: 16px 16px 0; overflow-wrap: anywhere; }
.line-product strong { font-size: 15px; font-weight: 600; }
.line-product p { margin: 6px 0 0; font-size: 12px; line-height: 1.7; color: var(--el-text-color-secondary); }
dl { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 18px 16px; }
dt { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
dd { margin: 0; font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.proposed dd { color: var(--el-color-primary); }
.line-error { font-size: 12px; line-height: 1.7; color: var(--el-color-danger); margin: 8px 16px 14px; }
.blocked-note { margin: 18px 0; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
footer { position: sticky; bottom: 0; display: flex; gap: 12px; justify-content: flex-end; padding: 18px 0 4px; margin-top: 20px; background: var(--el-bg-color); border-top: 1px solid var(--el-border-color-lighter); }
footer :deep(.el-button + .el-button) { margin-left: 0; }
@container (max-width: 520px) { dl { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; } .sync-summary { align-items: flex-start; flex-direction: column; gap: 8px; } footer { flex-wrap: wrap; } }
</style>
