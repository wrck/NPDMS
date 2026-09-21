<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import SemanticEntityView from '@/components/SemanticEntityView.vue'
import SemanticEvidenceTable from '@/components/SemanticEvidenceTable.vue'
import SemanticGenericStructureView from '@/components/SemanticGenericStructureView.vue'
import type { CollectionDetails } from '@/types/collection'
import type { BlockObservation, CollectionSemanticResult } from '@/types/parser'
import { flattenSemanticEvidence } from '@/utils/semantic-result'

const props = withDefaults(defineProps<{
  results?: CollectionSemanticResult[]
  collectionDetails?: CollectionDetails
  legacyFacts?: Record<string, string>
}>(), {
  results: () => [],
  collectionDetails: undefined,
  legacyFacts: undefined
})

const activeTarget = ref('')
let initialized = false
watch(() => props.collectionDetails?.collectionId, () => { initialized = false; activeTarget.value = '' })
watch(() => props.results, (results) => {
  if (!initialized && results.length) {
    activeTarget.value = results[0]!.taskId
    initialized = true
  }
}, { immediate: true })
const legacyTargets = computed(() => {
  if (props.collectionDetails?.targets.length) return props.collectionDetails.targets
    .filter(target => Object.keys(target.parsedFacts ?? {}).length)
    .map(target => ({ id: target.targetId, label: `目标 ${target.targetId} · ${target.contextSnapshot.device?.deviceName || target.endpointSnapshot.host}`, facts: target.parsedFacts }))
  return props.legacyFacts && Object.keys(props.legacyFacts).length
    ? [{ id: 'legacy', label: '兼容解析事实', facts: props.legacyFacts }] : []
})

function commandText(targetId: number, observation: BlockObservation): string {
  const target = props.collectionDetails?.targets.find((item) => item.targetId === targetId)
  return target?.commandBlocks.find((item) => item.commandIndex === observation.commandIndex)?.commandText
    ?? `命令 #${observation.commandIndex + 1}`
}

function statusType(status: BlockObservation['status']): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'OBSERVED') return 'success'
  if (status === 'UNPARSED' || status === 'NO_DATA' || status === 'PARTIAL') return 'warning'
  if (status === 'EXECUTION_FAILED' || status === 'SOURCE_CORRUPTED') return 'danger'
  return 'info'
}

function confidence(value: number): string {
  return Number.isFinite(value) ? `${Math.round(value * 100)}%` : '—'
}

function profileSourceLabel(source?: string): string {
  return ({ LOG_OUTPUT: '日志识别', CONTEXT_SNAPSHOT: '设备快照', GENERIC: '通用回退' } as Record<string, string>)[source ?? ''] ?? '旧版本未记录'
}

function evidenceRows(item: CollectionSemanticResult) {
  return flattenSemanticEvidence(item.result?.semanticResult.snapshot)
}

function lineRange(start: number, end: number): string {
  return `父命令行 ${start}${end !== start ? `–${end}` : ''}`
}
</script>

<template>
  <section class="semantic-result" aria-labelledby="semantic-result-title">
    <div class="semantic-result__title-row">
      <h3 id="semantic-result-title">本次解析结果</h3>
      <el-tag v-if="results.length" size="small" type="info">{{ results.length }} 个目标</el-tag>
    </div>

    <el-empty v-if="!results.length" :image-size="48" description="暂无解析结果" />

    <el-collapse v-else v-model="activeTarget" accordion>
      <el-collapse-item v-for="item in results" :key="item.taskId" :name="item.taskId">
        <template #title>
          <span class="semantic-result__target">目标 {{ item.targetId }}</span>
          <el-tag size="small" :type="item.state === 'SUCCEEDED' ? 'success' : item.state === 'FAILED' ? 'danger' : 'info'">
            {{ item.state }}
          </el-tag>
        </template>

        <template v-if="item.result">
          <div class="semantic-result__meta">
            <span>版本 {{ item.coordinate.releaseVersion }}</span>
            <span>{{ item.result.semanticResult.observations.length }} 个命令块</span>
          </div>
          <div class="semantic-result__profile">
            <span>配置档</span>
            <el-tag size="small" type="primary">{{ item.result.semanticResult.profileSelection?.profileId ?? '未记录' }}</el-tag>
            <span>{{ profileSourceLabel(item.result.semanticResult.profileSelection?.source) }}</span>
            <code v-if="item.result.semanticResult.profileSelection?.normalizedModel">
              {{ item.result.semanticResult.profileSelection.normalizedModel }}
            </code>
          </div>
          <el-alert v-for="warning in item.result.semanticResult.profileSelection?.warnings ?? []" :key="warning"
            :title="warning === 'MODEL_HINT_CONFLICT' ? '日志型号与设备快照不一致，已按日志型号选择配置档。' : warning"
            type="warning" :closable="false" show-icon />
          <el-tabs class="semantic-result__tabs">
            <el-tab-pane v-if="item.result.semanticResult.genericContent" label="通用结构">
              <SemanticGenericStructureView
                :content="item.result.semanticResult.genericContent"
                :observations="item.result.semanticResult.observations"
                :nested-observations="item.result.semanticResult.nestedObservations"
              />
            </el-tab-pane>
            <el-tab-pane label="实体视图">
              <SemanticEntityView :projections="item.result.semanticResult.projections" />
            </el-tab-pane>
            <el-tab-pane label="字段证据">
              <SemanticEvidenceTable :rows="evidenceRows(item)" />
            </el-tab-pane>
            <el-tab-pane label="原始 JSON">
              <pre class="semantic-result__raw">{{ JSON.stringify(item.result.semanticResult, null, 2) }}</pre>
            </el-tab-pane>
          </el-tabs>
          <el-collapse class="semantic-result__summary">
            <el-collapse-item :name="`${item.taskId}-summary`" title="命令与内嵌摘要">
              <ul class="semantic-result__commands">
                <li v-for="observation in item.result.semanticResult.observations" :key="observation.commandIndex">
                  <div>
                    <code>{{ commandText(item.targetId, observation) }}</code>
                    <small v-if="observation.blockRole">{{ observation.blockRole }}</small>
                  </div>
                  <span class="semantic-result__command-status">
                    <el-tag size="small" :type="statusType(observation.status)">{{ observation.status }}</el-tag>
                    <small>{{ confidence(observation.confidence) }}</small>
                  </span>
                </li>
              </ul>
              <el-collapse
                v-if="item.result.semanticResult.nestedObservations?.length"
                class="semantic-result__nested"
              >
                <el-collapse-item
                  :name="`${item.taskId}-nested`"
                  :title="`内嵌命令块 ${item.result.semanticResult.nestedObservations.length} 项`"
                >
                  <ul class="semantic-result__commands">
                    <li
                      v-for="observation in item.result.semanticResult.nestedObservations"
                      :key="`${observation.parentCommandIndex}-${observation.sectionIndex}`"
                    >
                      <div>
                        <code>{{ observation.commandText }}</code>
                        <small>{{ observation.blockRole || '未映射' }} · {{ lineRange(observation.sourceLineStart, observation.sourceLineEnd) }}</small>
                      </div>
                      <span class="semantic-result__command-status">
                        <el-tag size="small" :type="statusType(observation.status)">{{ observation.status }}</el-tag>
                        <small>{{ confidence(observation.confidence) }}</small>
                      </span>
                    </li>
                  </ul>
                </el-collapse-item>
              </el-collapse>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-alert v-else :title="item.waitReason || '结构化结果正在生成。'"
          :type="item.state === 'FAILED' ? 'error' : 'info'" :closable="false" />
      </el-collapse-item>
    </el-collapse>

    <el-collapse v-if="legacyTargets.length" class="semantic-result__legacy">
      <el-collapse-item v-for="target in legacyTargets" :key="target.id" :title="`兼容解析事实 · ${target.label}`" :name="target.id">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item v-for="(value, key) in target.facts" :key="key" :label="String(key)">
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<style scoped>
.semantic-result { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--line); }
.semantic-result__title-row, .semantic-result__meta, .semantic-result__profile, .semantic-result__commands li, .semantic-result__command-status { display: flex; align-items: center; }
.semantic-result__title-row { justify-content: space-between; margin-bottom: 0.75rem; }
.semantic-result__title-row h3, .semantic-result__data h4 { margin: 0; font-size: 0.875rem; }
.semantic-result__target { margin-right: 0.5rem; }
.semantic-result__meta { justify-content: space-between; color: var(--slate); font-size: 0.75rem; }
.semantic-result__profile { flex-wrap: wrap; gap: 0.375rem; margin-top: 0.5rem; color: var(--slate); font-size: 0.75rem; }
.semantic-result__profile code { color: var(--ink); }
.semantic-result__commands { display: grid; gap: 0.5rem; margin: 0.75rem 0; padding: 0; list-style: none; }
.semantic-result__commands li { justify-content: space-between; gap: 0.5rem; padding: 0.5rem; border: 1px solid var(--line); border-radius: 4px; }
.semantic-result__commands li > div { display: grid; min-width: 0; }
.semantic-result__commands code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.semantic-result__commands small, .semantic-result__command-status small { color: var(--slate); font-size: 0.75rem; }
.semantic-result__command-status { flex: none; gap: 0.375rem; }
.semantic-result__tabs { margin-top: 0.75rem; }
.semantic-result__nested { margin-bottom: 0.75rem; }
.semantic-result__raw { max-height: 24rem; overflow: auto; margin: 0; padding: 0.75rem; border-radius: 4px; background: #101820; color: #dce6ec; font: 0.75rem/1.55 "Cascadia Code", "JetBrains Mono", monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
.semantic-result__legacy { margin-top: 0.75rem; }
</style>
