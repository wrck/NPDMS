<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import PanelHeader from '@/components/PanelHeader.vue'
import { commonCommands } from '@/data/common-commands'
import type { CommonCommand } from '@/data/common-commands'

import type { ScriptDraft } from '@/types/collection'

const props = withDefaults(
  defineProps<{
    collapsed?: boolean
  }>(),
  {
    collapsed: false
  }
)

const emit = defineEmits<{ toggle: [] }>()
const model = defineModel<ScriptDraft>({ required: true })
const hashing = ref(false)
let hashRevision = 0

const firstCommandLine = computed(
  () => model.value.content.split(/\r?\n/).find((line) => line.trim())?.trim() ?? ''
)
const panelSummary = computed(() => {
  const identity = `${model.value.key || '未命名脚本'} · ${model.value.version || '未指定版本'}`
  return props.collapsed && firstCommandLine.value
    ? `${identity} · ${firstCommandLine.value}`
    : identity
})

function applyCommonCommand(command: CommonCommand) {
  model.value.content = command.command
  if (!model.value.key.trim()) model.value.key = command.id
}

watch(
  () => model.value.content,
  async (content) => {
    const revision = ++hashRevision
    hashing.value = true
    const bytes = new TextEncoder().encode(content)
    const digest = await crypto.subtle.digest('SHA-256', bytes)
    if (revision === hashRevision) {
      model.value.sha256 = Array.from(new Uint8Array(digest))
        .map((byte) => byte.toString(16).padStart(2, '0'))
        .join('')
      hashing.value = false
    }
  },
  { immediate: true }
)

watch(
  () => model.value.source,
  (source) => {
    model.value.policy = source === 'LOCAL_MANAGED' ? 'REGISTER_VERSION' : 'EXECUTION_ONLY'
  }
)
</script>

<template>
  <el-card class="script-editor" shadow="never" aria-labelledby="script-title">
    <template #header>
    <PanelHeader
      id="script-title"
      title="采集命令"
      icon="⌘"
      :summary="collapsed ? panelSummary : undefined"
      collapsible
      :collapsed="collapsed"
      @toggle="emit('toggle')"
    />
    </template>

    <template v-if="!collapsed">
      <div class="script-editor__body">
        <div class="script-editor__meta">
          <label class="script-editor__source">
            <span>来源</span>
            <el-select v-model="model.source" size="small" aria-label="采集命令来源">
              <el-option label="平台登记版本" value="LOCAL_MANAGED" />
              <el-option label="外部系统交付" value="EXTERNAL_DELIVERED" />
              <el-option label="本次内联脚本" value="ADHOC_INLINE" />
            </el-select>
          </label>

          <details class="artifact-details">
            <summary>
              <span>制品</span>
              <strong>{{ model.key || '未命名脚本' }} @ {{ model.version || '未指定版本' }}</strong>
              <small>编辑标识</small>
            </summary>
            <div class="artifact-details__body">
              <label>
                <span>脚本标识</span>
                <el-input v-model="model.key" size="small" autocomplete="off" />
              </label>
              <label>
                <span>不可变版本</span>
                <el-input v-model="model.version" size="small" autocomplete="off" />
              </label>
              <dl class="artifact-identity">
                <div>
                  <dt>来源 / 策略</dt>
                  <dd>{{ model.source }} / {{ model.policy }}</dd>
                </div>
                <div class="artifact-identity__hash">
                  <dt>sha256</dt>
                  <dd :aria-busy="hashing">
                    {{ hashing ? '计算中…' : model.sha256 || '—' }}
                  </dd>
                </div>
              </dl>
            </div>
          </details>
        </div>

        <label class="script-editor__command" for="collection-command">命令</label>
        <el-input
          id="collection-command"
          v-model="model.content"
          type="textarea"
          :rows="2"
          resize="none"
          class="command-input"
          aria-label="采集命令内容"
          spellcheck="false"
        />

        <section class="common-commands" aria-labelledby="common-commands-title">
          <h3 id="common-commands-title">常用命令</h3>
          <div class="common-commands__list">
            <el-button
              v-for="command in commonCommands"
              :key="command.id"
              class="common-commands__button"
              size="small"
              plain
              :title="command.category"
              @click="applyCommonCommand(command)"
            >
              {{ command.label }}
            </el-button>
          </div>
        </section>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.script-editor {
  flex: 0 0 auto;
  min-width: 0;
}

.script-editor__body {
  display: grid;
  gap: 0.375rem;
}

.script-editor__meta {
  position: relative;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
  gap: 0.5rem;
}

.script-editor__source {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  color: var(--slate);
  font-size: 0.75rem;
}

.artifact-details {
  min-width: 0;
}

.artifact-details summary {
  display: grid;
  min-height: 2rem;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  border: 1px solid var(--line);
  border-radius: 0.25rem;
  color: var(--slate);
  font-size: 0.75rem;
  cursor: pointer;
  list-style: none;
}

.artifact-details summary::-webkit-details-marker {
  display: none;
}

.artifact-details summary::after {
  color: var(--signal);
  content: "⌄";
}

.artifact-details[open] summary::after {
  content: "⌃";
}

.artifact-details summary strong {
  overflow-wrap: anywhere;
  color: var(--ink);
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  white-space: normal;
}

.artifact-details summary small {
  color: var(--signal);
  white-space: nowrap;
}

.artifact-details__body {
  position: absolute;
  z-index: 8;
  top: calc(100% + 0.25rem);
  right: 0;
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
  padding: 0.75rem;
  border: 1px solid var(--line);
  border-radius: 0.375rem;
  background: var(--surface);
  box-shadow: 0 0.75rem 2rem rgb(22 38 45 / 16%);
}

.artifact-details__body > label {
  display: grid;
  gap: 0.25rem;
  color: var(--slate);
  font-size: 0.75rem;
}

.artifact-details__body .artifact-identity,
.artifact-details__body .security-note {
  grid-column: 1 / -1;
}

.script-editor__command {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

.command-input :deep(textarea) {
  min-height: 2.75rem !important;
  padding: 0.5rem 0.625rem;
  border-color: #27383f;
  background: #102026;
  color: #d2e5e1;
  font-size: 0.75rem;
  line-height: 1.35;
}

.common-commands {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
}

.common-commands h3 {
  flex: 0 0 auto;
  margin: 0;
  color: var(--slate);
  font-size: 0.75rem;
  font-weight: 500;
}

.common-commands__list {
  display: flex;
  min-width: 0;
  gap: 0.375rem;
  overflow-x: auto;
  padding-bottom: 0.125rem;
}

.common-commands__button {
  flex: 0 0 auto;
  margin: 0;
  padding: 0.25rem 0.5rem;
  border-radius: var(--el-border-radius-base);
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: var(--el-font-size-extra-small);
}

.common-commands__button:hover,
.common-commands__button:focus-visible {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

@media (min-width: 768px) {
  .script-editor,
  .script-editor :deep(.el-card__body) {
    overflow: visible;
  }
}

@media (max-width: 767px) {
  .script-editor__meta {
    grid-template-columns: minmax(0, 1fr);
  }

  .artifact-details__body {
    position: static;
    width: 100%;
    grid-template-columns: minmax(0, 1fr);
    margin-top: 0.375rem;
  }

  .artifact-details__body > *,
  .artifact-details__body .artifact-identity,
  .artifact-details__body .security-note {
    grid-column: 1;
    min-width: 0;
  }
}
</style>
