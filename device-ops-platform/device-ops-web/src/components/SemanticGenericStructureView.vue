<script setup lang="ts">
import { computed, ref } from 'vue'

import SemanticGenericSection from '@/components/SemanticGenericSection.vue'
import type { BlockObservation, GenericContent, GenericSection, GenericUnit,
  NestedBlockObservation, ObservationStatus } from '@/types/parser'

const props = withDefaults(defineProps<{
  content: GenericContent
  observations?: BlockObservation[]
  nestedObservations?: NestedBlockObservation[]
}>(), {
  observations: () => [],
  nestedObservations: () => []
})

const activeUnits = ref<string[]>([])
const activeSections = ref<string[]>([])
const topUnits = computed(() => props.content.units.filter(unit => unit.parentCommandIndex == null))

function unitKey(unit: GenericUnit): string {
  return unit.parentCommandIndex == null
    ? `unit:${unit.commandIndex}`
    : `unit:${unit.parentCommandIndex}:${unit.sectionIndex}`
}

function sectionKey(unit: GenericUnit, section: GenericSection): string {
  return `${unitKey(unit)}:section:${section.sectionIndex}`
}

function childrenFor(unit: GenericUnit): GenericUnit[] {
  return props.content.units
    .filter(child => child.parentCommandIndex === unit.commandIndex)
    .sort((left, right) => (left.sectionIndex ?? 0) - (right.sectionIndex ?? 0))
}

function observationStatus(unit: GenericUnit): ObservationStatus | undefined {
  if (unit.parentCommandIndex == null) {
    return props.observations.find(item => item.commandIndex === unit.commandIndex)?.status
  }
  return props.nestedObservations.find(item =>
    item.parentCommandIndex === unit.parentCommandIndex && item.sectionIndex === unit.sectionIndex)?.status
}

function semanticStatus(unit: GenericUnit): string {
  const status = observationStatus(unit)
  if (status === 'UNPARSED' && unit.structureStatus === 'STRUCTURED') return '已结构化、未语义映射'
  return status ? `语义 ${status}` : '未记录语义状态'
}

function structureStatusLabel(unit: GenericUnit): string {
  return ({
    STRUCTURED: '已结构化',
    PARTIAL: '部分结构化',
    TEXT_ONLY: '保留文本',
    EMPTY: '空结果',
    LIMITED: '达到解析限制'
  } as Record<string, string>)[unit.structureStatus] ?? unit.structureStatus
}

function lineRange(unit: GenericUnit): string {
  return `行 ${unit.sourceLineStart}${unit.sourceLineEnd === unit.sourceLineStart ? '' : `–${unit.sourceLineEnd}`}`
}

function isUnitOpen(unit: GenericUnit): boolean {
  return activeUnits.value.includes(unitKey(unit))
}

function isSectionOpen(unit: GenericUnit, section: GenericSection): boolean {
  return activeSections.value.includes(sectionKey(unit, section))
}
</script>

<template>
  <section
    class="generic-structure"
    aria-label="通用结构解析结果"
  >
    <el-empty
      v-if="!topUnits.length"
      :image-size="48"
      description="没有可展示的通用结构"
    />
    <el-collapse
      v-else
      v-model="activeUnits"
      class="generic-structure__units"
    >
      <el-collapse-item
        v-for="unit in topUnits"
        :key="unitKey(unit)"
        :name="unitKey(unit)"
        :data-unit-key="unitKey(unit)"
      >
        <template #title>
          <span class="generic-structure__unit-title">
            <code>{{ unit.commandText }}</code>
            <el-tag
              size="small"
              type="primary"
            >{{ structureStatusLabel(unit) }}</el-tag>
            <span>{{ semanticStatus(unit) }}</span>
            <small>{{ lineRange(unit) }} · {{ unit.sections.length }} 段 · {{ unit.warnings.length }} 条警告</small>
          </span>
        </template>

        <div
          v-if="isUnitOpen(unit)"
          class="generic-structure__unit-body"
        >
          <el-collapse
            v-if="unit.sections.length"
            v-model="activeSections"
          >
            <el-collapse-item
              v-for="section in unit.sections"
              :key="sectionKey(unit, section)"
              :name="sectionKey(unit, section)"
              :title="`结构段 #${section.sectionIndex} · ${section.type} · 行 ${section.startLine}–${section.endLine}`"
            >
              <SemanticGenericSection
                v-if="isSectionOpen(unit, section)"
                :section="section"
              />
            </el-collapse-item>
          </el-collapse>

          <section
            v-if="childrenFor(unit).length"
            class="generic-structure__nested"
          >
            <h4>内嵌命令块 {{ childrenFor(unit).length }} 项</h4>
            <el-collapse v-model="activeUnits">
              <el-collapse-item
                v-for="child in childrenFor(unit)"
                :key="unitKey(child)"
                :name="unitKey(child)"
                :data-unit-key="unitKey(child)"
                :data-nested-unit="child.sectionIndex"
                :data-structure-status="child.structureStatus"
              >
                <template #title>
                  <span class="generic-structure__unit-title">
                    <code>#{{ child.sectionIndex }} {{ child.commandText }}</code>
                    <el-tag
                      size="small"
                      :type="child.structureStatus === 'EMPTY' ? 'info' : 'primary'"
                    >
                      {{ structureStatusLabel(child) }}
                    </el-tag>
                    <span>{{ semanticStatus(child) }}</span>
                    <small>
                      {{ lineRange(child) }} · {{ child.sections.length }} 段 · {{ child.warnings.length }} 条警告
                      <template v-if="child.omittedLineCount"> · 省略 {{ child.omittedLineCount }} 行</template>
                    </small>
                    <el-tag
                      v-for="warning in child.warnings"
                      :key="`${warning.code}-${warning.lineNumber}`"
                      size="small"
                      type="warning"
                    >
                      {{ warning.code }} · 行 {{ warning.lineNumber }}
                    </el-tag>
                  </span>
                </template>

                <el-collapse
                  v-if="isUnitOpen(child) && child.sections.length"
                  v-model="activeSections"
                >
                  <el-collapse-item
                    v-for="section in child.sections"
                    :key="sectionKey(child, section)"
                    :name="sectionKey(child, section)"
                    :title="`结构段 #${section.sectionIndex} · ${section.type} · 行 ${section.startLine}–${section.endLine}`"
                  >
                    <SemanticGenericSection
                      v-if="isSectionOpen(child, section)"
                      :section="section"
                    />
                  </el-collapse-item>
                </el-collapse>
                <el-empty
                  v-else-if="isUnitOpen(child)"
                  :image-size="36"
                  description="该命令没有返回内容"
                />
              </el-collapse-item>
            </el-collapse>
          </section>
        </div>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<style scoped>
.generic-structure { min-width: 0; }
.generic-structure__unit-title { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; align-items: center; gap: 0.375rem; padding: 0.25rem 0; }
.generic-structure__unit-title code { overflow: hidden; max-width: 24rem; color: var(--ink); text-overflow: ellipsis; white-space: nowrap; }
.generic-structure__unit-title span, .generic-structure__unit-title small { color: var(--slate); }
.generic-structure__unit-title small { margin-left: auto; font-size: 0.75rem; }
.generic-structure__unit-body { padding: 0.25rem 0 0.75rem; }
.generic-structure__nested { margin-top: 0.75rem; }
.generic-structure__nested h4 { margin: 0 0 0.5rem; font-size: 0.8125rem; }
</style>
