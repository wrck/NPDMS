<template>
  <details v-if="evaluations.length" class="my-12px">
    <summary>查看适用条件结果（{{ evaluations.length }} 个模板版本）</summary>
    <ul>
      <li v-for="item in evaluations" :key="String(item.templateRevisionId)">
        <details>
          <summary>
            {{ item.ruleName }} · {{ outcomes[item.result.outcome] }}
            （模板 {{ item.templateId }}，版本 ID {{ item.templateRevisionId }}）
          </summary>
          <p v-if="item.result.reasonCode">原因：{{ item.result.reasonCode }}</p>
          <ul v-if="item.result.conditions.length">
            <li v-for="condition in item.result.conditions" :key="condition.key">
              {{ condition.path }} · {{ condition.component }} · {{ outcomes[condition.outcome] }}
              <span v-if="condition.reasonCode">（{{ condition.reasonCode }}）</span>
            </li>
          </ul>
          <ul v-if="item.result.diagnostics.length">
            <li v-for="(diagnostic, index) in item.result.diagnostics" :key="index">
              {{ diagnostic.path }} · {{ diagnostic.component }} · {{ diagnostic.code }}
            </li>
          </ul>
        </details>
      </li>
    </ul>
  </details>
</template>
<script setup lang="ts">
import type { TemplateMatchEvaluation } from '@/api/pms/project/project-templates'

defineProps<{ evaluations: TemplateMatchEvaluation[] }>()
const outcomes = { MATCHED: '满足', NOT_MATCHED: '不满足', UNKNOWN: '未知' } as const
</script>
