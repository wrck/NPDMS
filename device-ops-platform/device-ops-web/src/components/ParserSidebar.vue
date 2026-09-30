<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { getParserSelectionOptions } from '@/api/device-ops'
import PanelHeader from '@/components/PanelHeader.vue'
import type { ScriptDraft } from '@/types/collection'
import type { ParserOption, ParserSelectionOptions, SemanticParserSelection } from '@/types/parser'
import { parserConfigError, parserExamples } from '@/utils/parser-config'

const props = withDefaults(defineProps<{
  collapsed?: boolean
}>(), {
  collapsed: false
})

const emit = defineEmits<{ toggle: [] }>()
const script = defineModel<ScriptDraft>({ required: true })
const selection = defineModel<SemanticParserSelection>('selection', { required: true })
const options = ref<ParserSelectionOptions>()
const loading = ref(false)
const loadError = ref('')

const publishedReleases = computed(() => options.value?.options ?? [])
const selectedRelease = computed<ParserOption | undefined>(() =>
  publishedReleases.value.find((item) => item.releaseId === selection.value.releaseId)
)
const summary = computed(() => {
  if (selection.value.mode === 'DISABLED') return '本次不解析'
  if (selection.value.mode === 'RELEASE') return selectedRelease.value?.displayName ?? '指定版本'
  return options.value?.defaultAvailable ? '结构化解析' : '保留原始输出'
})
const compatibilityExample = computed(() => script.value.parserType === 'NONE'
  ? undefined
  : parserExamples[script.value.parserType])
const compatibilityError = computed(() => parserConfigError(script.value.parserType, script.value.parserConfig))
const compatibilityPlaceholder = computed(() => script.value.parserType === 'JSON'
  ? 'JSON 解析不需要配置'
  : '{"separator":"=","ignoreBlankLines":true}')

function applyCompatibilityExample() {
  if (compatibilityExample.value) script.value.parserConfig = compatibilityExample.value.config
}

async function loadOptions() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    options.value = await getParserSelectionOptions()
    if (!options.value.automaticEnabled && selection.value.mode === 'AUTO') {
      selection.value = { mode: 'DISABLED' }
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '解析版本加载失败。'
  } finally {
    loading.value = false
  }
}

watch(() => selection.value.releaseId, (releaseId) => {
  if (selection.value.mode !== 'RELEASE' || !releaseId) return
  const release = publishedReleases.value.find((item) => item.releaseId === releaseId)
  if (release) selection.value = { mode: 'RELEASE', releaseId, logType: release.logType }
})

watch(() => selection.value.mode, (mode) => {
  if (mode !== 'RELEASE') return
  const release = selectedRelease.value ?? publishedReleases.value[0]
  if (release) selection.value = { mode: 'RELEASE', releaseId: release.releaseId, logType: release.logType }
})

onMounted(loadOptions)

async function ensureOptions(): Promise<ParserSelectionOptions | undefined> {
  if (!options.value && !loading.value && !loadError.value) await loadOptions()
  return options.value
}

defineExpose({ ensureOptions })
</script>

<template>
  <el-card class="parser-sidebar" shadow="never" aria-labelledby="parser-title">
    <template #header>
      <PanelHeader
        id="parser-title"
        title="结构化解析"
        icon="⌗"
        :summary="props.collapsed ? summary : undefined"
        collapsible
        :collapsed="props.collapsed"
        @toggle="emit('toggle')"
      />
    </template>

    <template v-if="!props.collapsed">
      <el-form v-loading="loading" label-position="top">
        <el-form-item label="解析策略">
          <el-radio-group v-model="selection.mode" class="parser-sidebar__strategy">
            <el-radio-button value="AUTO" :disabled="options && !options.automaticEnabled">自动</el-radio-button>
            <el-radio-button value="RELEASE">指定版本</el-radio-button>
            <el-radio-button value="DISABLED">不解析</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="selection.mode === 'RELEASE'" label="解析版本">
          <el-select v-model="selection.releaseId" placeholder="选择已发布版本" filterable>
            <el-option v-for="option in publishedReleases" :key="option.releaseId"
              :label="`${option.displayName} · ${option.releaseVersion}`" :value="option.releaseId" />
          </el-select>
        </el-form-item>
      </el-form>

      <template v-if="loadError">
        <el-alert :title="loadError" type="error" :closable="false" show-icon />
        <el-button size="small" :loading="loading" @click="loadOptions">重试加载解析版本</el-button>
      </template>
      <el-alert v-else-if="options && !options.automaticEnabled"
        title="未启用结构化解析，可选已发布版本" type="info" :closable="false" show-icon />
      <el-alert v-else-if="selection.mode === 'AUTO' && options && !options.defaultAvailable"
        title="暂无已激活的结构化解析版本" type="warning" :closable="false" show-icon />
      <p v-else class="parser-sidebar__hint">
        {{ selection.mode === 'AUTO'
          ? '使用已激活版本做结构化解析，不丢原始输出'
          : selection.mode === 'RELEASE'
            ? '固定使用所选版本'
            : '仅执行采集，不生成结构化结果' }}
      </p>

      <el-alert v-if="!loading && !loadError && options && !publishedReleases.length"
        title="暂无已发布解析版本" type="info" :closable="false" show-icon />
      <el-button v-if="!loadError && options && !publishedReleases.length" size="small"
        :loading="loading" @click="loadOptions">刷新解析版本</el-button>

      <el-collapse class="parser-sidebar__legacy">
        <el-collapse-item title="兼容解析配置" name="legacy">
          <el-form label-position="top">
            <el-form-item label="命令内联解析器">
              <el-select v-model="script.parserType">
                <el-option label="不启用" value="NONE" />
                <el-option label="JSON" value="JSON" />
                <el-option label="键值对" value="KEY_VALUE" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="script.parserType !== 'NONE'" label="解析配置">
              <el-input v-model="script.parserConfig" type="textarea" :rows="3"
                :placeholder="compatibilityPlaceholder" />
              <p v-if="compatibilityError" class="parser-sidebar__error" role="alert">{{ compatibilityError }}</p>
            </el-form-item>
            <div v-if="compatibilityExample" class="parser-sidebar__example">
              <div class="parser-sidebar__example-title">
                <strong>使用示例</strong>
                <el-button size="small" text type="primary" @click="applyCompatibilityExample">填入示例配置</el-button>
              </div>
              <p>{{ compatibilityExample.description }}</p>
              <dl>
                <dt>命令输出</dt><dd><code>{{ compatibilityExample.input }}</code></dd>
                <dt>兼容事实</dt><dd><code>{{ compatibilityExample.output }}</code></dd>
              </dl>
              <el-alert title="兼容解析仅作用于当前命令块"
                type="info" :closable="false" show-icon />
            </div>
          </el-form>
        </el-collapse-item>
      </el-collapse>
    </template>
  </el-card>
</template>

<style scoped>
.parser-sidebar__strategy { display: flex; width: 100%; }
.parser-sidebar__strategy :deep(.el-radio-button) { flex: 1; }
.parser-sidebar__strategy :deep(.el-radio-button__inner) { width: 100%; }
.parser-sidebar__hint { margin: 0; color: var(--slate); font-size: 0.8125rem; line-height: 1.6; }
.parser-sidebar__legacy { margin-top: 1rem; border-top: 1px solid var(--line); }
.parser-sidebar__error { margin: 0.375rem 0 0; color: var(--el-color-danger); font-size: 0.75rem; }
.parser-sidebar__example { display: grid; gap: 0.5rem; padding: 0.75rem; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-lighter); }
.parser-sidebar__example-title { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
.parser-sidebar__example p, .parser-sidebar__example dl { margin: 0; color: var(--slate); font-size: 0.75rem; line-height: 1.5; }
.parser-sidebar__example dt { margin-top: 0.375rem; font-weight: 600; color: var(--ink); }
.parser-sidebar__example dd { margin: 0.125rem 0 0; overflow-wrap: anywhere; white-space: pre-wrap; }
</style>
