<template>
  <el-drawer v-model="visible" :size="narrow ? '100%' : '860px'" title="需求分析修订对比">
    <el-skeleton v-if="loading" :rows="7" animated />
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" />
    <template v-else>
      <p>V{{ left?.revision.revisionNo }} → V{{ right?.revision.revisionNo }}</p>
      <el-empty v-if="!rows.length" description="两个修订没有差异" />
      <article v-for="field in rows" :key="field.fieldCode" class="diff-card">
        <strong>{{ labels[field.fieldCode] || field.fieldCode }}</strong>
        <div class="diff-values"><section><h4>修改前</h4><div>{{ display(field.before) }}</div></section>
          <section><h4>修改后</h4><div>{{ display(field.after) }}</div></section></div>
      </article>
    </template>
  </el-drawer>
</template>
<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import * as api from '@/api/pms/engineering/requirement-analysis/entity'
import type { EntityId, Difference, FieldValue, View } from '@/api/pms/engineering/requirement-analysis/entity'
const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false), loading = ref(false), error = ref('')
const rows = ref<Difference[]>([]), left = ref<View>(), right = ref<View>()
const labels = computed(() => {
  const result: Record<string, string> = {}
  for (const view of [left.value, right.value]) {
    if (!view?.form) continue
    const visit = (rules: Record<string, any>[]) => rules.forEach(rule => {
      if (rule.field && typeof rule.title === 'string') result[view.form!.binding.fieldBindings[rule.field] || rule.field] = rule.title
      if (Array.isArray(rule.children)) visit(rule.children)
    })
    visit(JSON.parse(view.form.formRulesJson))
  }
  return result
})
const display = (fact: FieldValue) => {
  if (!fact.readable) return '不可读取'
  if (fact.value == null || fact.value === '') return '（空）'
  if (typeof fact.value === 'string') return fact.value.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim() || '（空）'
  return typeof fact.value === 'object' ? JSON.stringify(fact.value) : String(fact.value)
}
let sequence = 0
const open = async (entityId: EntityId, leftId: EntityId, rightId: EntityId) => {
  const request = ++sequence
  visible.value = true; loading.value = true; error.value = ''; rows.value = []
  try {
    const [differences, before, after] = await Promise.all([api.compare(entityId, leftId, rightId), api.read(leftId), api.read(rightId)])
    if (request !== sequence) return
    rows.value = differences; left.value = before; right.value = after
  } catch { if (request === sequence) error.value = '修订已变化或无权查看，请刷新后重试' }
  finally { if (request === sequence) loading.value = false }
}
defineExpose({ open })
</script>
<style scoped>
.diff-card { padding: 16px 0; border-bottom: 1px solid var(--el-border-color-light); }
.diff-values { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (width <= 767px) { .diff-values { grid-template-columns: 1fr; } }
</style>
