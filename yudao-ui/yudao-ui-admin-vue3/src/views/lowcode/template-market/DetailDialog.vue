<script setup lang="ts">
/**
 * 模板市场详情对话框。
 *
 * <p>展示完整模板信息 + configJson 预览（`<pre>`）+ 版本列表 + 参数化下载表单
 * （按 parameters JSON 动态生成输入框）。支持直接评分与参数化下载。</p>
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  download,
  formatConfigJson,
  getById,
  listVersions,
  parseParameters,
  rate,
  splitTags,
  type ConfigTemplateParameter,
  type LowCodeConfigTemplate
} from '@/api/lowcode-template'

const props = defineProps<{
  /** 受控可见性 */
  modelValue: boolean
  /** 模板 ID（打开时按需加载详情与版本） */
  templateId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  /** 下载/评分后通知父组件刷新列表 */
  (e: 'changed'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const template = ref<LowCodeConfigTemplate | null>(null)
const versions = ref<LowCodeConfigTemplate[]>([])
const downloading = ref(false)
const ratingValue = ref(0)
const ratingSaving = ref(false)
/** 参数化下载表单值（key → value） */
const paramValues = ref<Record<string, string>>({})

const parameters = computed<ConfigTemplateParameter[]>(() =>
  parseParameters(template.value?.parameters)
)

const configJsonPretty = computed(() => formatConfigJson(template.value?.configJson))

const tags = computed(() => splitTags(template.value?.tags))

watch(
  () => props.templateId,
  async (id) => {
    if (!id) {
      template.value = null
      versions.value = []
      return
    }
    loading.value = true
    try {
      const detail = await getById(id)
      template.value = detail
      ratingValue.value = detail.rating ? Number(detail.rating) : 0
      // 初始化参数表单默认值
      const initVals: Record<string, string> = {}
      for (const p of parseParameters(detail.parameters)) {
        initVals[p.key] = p.defaultValue ?? ''
      }
      paramValues.value = initVals
      // 详情加载完成后补查版本列表（code 在 detail 中可得）
      versions.value = detail.code ? await listVersions(detail.code) : []
    } catch (e) {
      ElMessage.error('加载详情失败：' + (e instanceof Error ? e.message : String(e)))
      template.value = null
      versions.value = []
    } finally {
      loading.value = false
    }
  }
)

function validateParams(): boolean {
  for (const p of parameters.value) {
    if (p.required && !paramValues.value[p.key]?.trim()) {
      ElMessage.warning(`请填写参数：${p.label || p.key}`)
      return false
    }
  }
  return true
}

async function doDownload() {
  if (!template.value?.id) return
  if (!validateParams()) return
  downloading.value = true
  try {
    // 将参数值按 type 做轻量类型转换
    const params: Record<string, unknown> = {}
    for (const p of parameters.value) {
      const raw = paramValues.value[p.key]
      if (raw === undefined || raw === '') continue
      if (p.type === 'number') {
        const n = Number(raw)
        params[p.key] = Number.isNaN(n) ? raw : n
      } else if (p.type === 'boolean') {
        params[p.key] = raw === 'true' || raw === '1'
      } else {
        params[p.key] = raw
      }
    }
    const result = await download(template.value.id, params)
    ElMessage.success('下载成功，已应用参数化替换')
    // 弹出替换后的 configJson 供复制
    const text = formatConfigJson(result.configJson)
    if (text) {
      try {
        await navigator.clipboard.writeText(text)
        ElMessage.info('配置 JSON 已复制到剪贴板')
      } catch {
        /* 剪贴板不可用时静默 */
      }
    }
    emit('changed')
  } catch (e) {
    ElMessage.error('下载失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    downloading.value = false
  }
}

async function doRate() {
  if (!template.value?.id) return
  if (!ratingValue.value || ratingValue.value < 1) {
    ElMessage.warning('请选择评分（1-5 星）')
    return
  }
  ratingSaving.value = true
  try {
    const updated = await rate(template.value.id, ratingValue.value)
    template.value = updated
    ElMessage.success('评分成功')
    emit('changed')
  } catch (e) {
    ElMessage.error('评分失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    ratingSaving.value = false
  }
}

function paramInputType(p: ConfigTemplateParameter): string {
  if (p.type === 'number') return 'number'
  return 'text'
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="模板详情"
    width="820px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-loading="loading">
      <template v-if="template">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="名称">{{ template.name }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ template.code }}</el-descriptions-item>
          <el-descriptions-item label="配置类型">{{ template.configType }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ template.category || '—' }}</el-descriptions-item>
          <el-descriptions-item label="作者">{{ template.author || '—' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ template.version || '—' }}</el-descriptions-item>
          <el-descriptions-item label="下载量">{{ template.downloadCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="评分">
            <el-rate
              :model-value="Number(template.rating) || 0"
              disabled
              show-score
              :score-template="`${template.rating ?? 0} (${template.ratingCount ?? 0})`"
            />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag
              v-if="template.status"
              :type="template.status === 'PUBLISHED' ? 'success' : template.status === 'ARCHIVED' ? 'info' : 'warning'"
              size="small"
            >
              {{ template.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            <el-tag v-for="t in tags" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
            <span v-if="tags.length === 0">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ template.description || '—' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 缩略图 -->
        <div v-if="template.thumbnail" class="block">
          <div class="block-title">缩略图</div>
          <el-image
            :src="template.thumbnail"
            fit="contain"
            style="max-height: 180px; border-radius: 4px; border: 1px solid var(--el-border-color-lighter)"
          />
        </div>

        <!-- 参数化下载表单 -->
        <div class="block">
          <div class="block-title">
            参数化下载
            <span v-if="parameters.length === 0" class="block-hint">（该模板无可配置参数，直接下载即可）</span>
          </div>
          <el-form v-if="parameters.length > 0" label-width="140px" size="small">
            <el-form-item
              v-for="p in parameters"
              :key="p.key"
              :label="p.label || p.key"
              :required="!!p.required"
            >
              <el-select
                v-if="p.type === 'select' && p.options && p.options.length > 0"
                v-model="paramValues[p.key]"
                :placeholder="`请选择 ${p.label || p.key}`"
                style="width: 100%"
              >
                <el-option v-for="o in p.options" :key="o" :label="o" :value="o" />
              </el-select>
              <el-switch
                v-else-if="p.type === 'boolean'"
                :model-value="paramValues[p.key] === 'true'"
                @update:model-value="paramValues[p.key] = $event ? 'true' : 'false'"
              />
              <el-input
                v-else
                v-model="paramValues[p.key]"
                :type="paramInputType(p)"
                :placeholder="`请输入 ${p.label || p.key}`"
              />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="downloading" @click="doDownload">下载（应用参数替换）</el-button>
        </div>

        <!-- 评分 -->
        <div class="block">
          <div class="block-title">为该模板评分</div>
          <el-rate v-model="ratingValue" :max="5" allow-half />
          <el-button
            type="primary"
            plain
            size="small"
            style="margin-left: 12px"
            :loading="ratingSaving"
            @click="doRate"
          >
            提交评分
          </el-button>
        </div>

        <!-- configJson 预览 -->
        <div class="block">
          <div class="block-title">配置 JSON 预览</div>
          <pre v-if="configJsonPretty" class="json-pre">{{ configJsonPretty }}</pre>
          <el-empty v-else description="暂无配置 JSON" :image-size="60" />
        </div>

        <!-- 版本列表 -->
        <div class="block">
          <div class="block-title">版本历史</div>
          <el-table :data="versions" size="small" max-height="240" empty-text="暂无版本记录">
            <el-table-column label="版本" prop="version" width="120" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag
                  v-if="row.status"
                  :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'ARCHIVED' ? 'info' : 'warning'"
                  size="small"
                >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下载量" prop="downloadCount" width="100" />
            <el-table-column label="更新时间" prop="updateTime" min-width="160">
              <template #default="{ row }">{{ row.updateTime || '—' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="未加载到模板信息" />
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.block {
  margin-top: 16px;
}

.block-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.block-hint {
  font-weight: normal;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 4px;
}

.json-pre {
  margin: 0;
  padding: 12px;
  max-height: 280px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
