<script setup lang="ts">
/**
 * 应用源码导出 UI（批次5-T10 前端）。
 *
 * <p>借鉴网易轻舟源码导出 — 无黑盒引擎。前端提供应用选择 + 清单预览 + ZIP 下载：
 * 顶部应用选择器（含「全部应用」选项）+ 预览/导出按钮；中部以 el-descriptions
 * 展示清单元数据、el-table 展示配置数量、el-tag 云展示实体表；底部以 `<pre>`
 * 展示部署指南。导出 ZIP 时取 Blob → ObjectURL → 触发浏览器下载。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  exportAndDownload,
  listApps,
  previewManifest,
  type AppSourceManifest
} from '@/api/lowcode-app-source'

defineOptions({ name: 'LowcodeAppSourceExportView' })

const apps = ref<string[]>([])
/** 选中的应用 bizType；空串表示「全部应用」 */
const selectedBizType = ref('')
const manifest = ref<AppSourceManifest | null>(null)

const loading = ref(false)
const exporting = ref(false)

/** 配置数量表格行（由 configCounts Map 转换） */
interface ConfigCountRow {
  type: string
  count: number
}
const configCountRows = computed<ConfigCountRow[]>(() => {
  const map = manifest.value?.configCounts
  if (!map) return []
  return Object.entries(map).map(([type, count]) => ({ type, count }))
})

const entityTables = computed<string[]>(() => manifest.value?.entityTables ?? [])

const totalConfigCount = computed(() =>
  configCountRows.value.reduce((a, r) => a + r.count, 0)
)

async function loadApps() {
  try {
    apps.value = await listApps()
  } catch (e) {
    ElMessage.error('加载应用列表失败：' + (e instanceof Error ? e.message : String(e)))
    apps.value = []
  }
}

async function preview() {
  loading.value = true
  try {
    manifest.value = await previewManifest(selectedBizType.value || undefined)
    ElMessage.success('清单预览已加载')
  } catch (e) {
    ElMessage.error('预览清单失败：' + (e instanceof Error ? e.message : String(e)))
    manifest.value = null
  } finally {
    loading.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    await exportAndDownload(selectedBizType.value || undefined)
    ElMessage.success('导出成功，已开始下载 ZIP')
  } catch (e) {
    ElMessage.error('导出失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    exporting.value = false
  }
}

function formatTime(t?: string): string {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  loadApps()
})
</script>

<template>
  <div style="padding: 16px" v-loading="loading">
    <!-- 顶部：应用选择 + 操作按钮 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <div class="action-bar">
        <span class="label">选择应用：</span>
        <el-select
          v-model="selectedBizType"
          placeholder="选择要导出的应用"
          clearable
          style="width: 280px"
        >
          <el-option label="全部应用（bizType=null）" value="" />
          <el-option
            v-for="app in apps"
            :key="app"
            :label="app"
            :value="app"
          />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="preview">
          预览清单
        </el-button>
        <el-button type="success" :loading="exporting" @click="doExport">
          导出 ZIP
        </el-button>
        <span class="hint">导出包含 JSON + DDL + POM + README，可独立部署（无黑盒引擎）</span>
      </div>
    </el-card>

    <!-- 清单详情 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <span>导出清单</span>
      </template>
      <el-empty v-if="!manifest" description="尚未预览清单，请选择应用后点击「预览清单」" />
      <template v-else>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="清单版本">{{ manifest.manifestVersion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="应用编码">{{ manifest.appCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="应用名称">{{ manifest.appName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="源系统">{{ manifest.sourceSystem || '—' }}</el-descriptions-item>
          <el-descriptions-item label="导出人">{{ manifest.exportBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="导出时间">{{ formatTime(manifest.exportTime) }}</el-descriptions-item>
          <el-descriptions-item label="平台版本">{{ manifest.platformVersion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="凭据脱敏">
            <el-tag :type="manifest.credentialsRedacted ? 'success' : 'warning'" size="small">
              {{ manifest.credentialsRedacted ? '已脱敏' : '未脱敏' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ manifest.description || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>

    <!-- 配置数量 + 实体表 -->
    <el-row :gutter="16" v-if="manifest">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="section-card">
          <template #header>
            <span>配置数量统计</span>
            <span class="header-extra">共 {{ totalConfigCount }} 项</span>
          </template>
          <el-table :data="configCountRows" size="small" max-height="320" empty-text="暂无配置">
            <el-table-column label="配置类型" prop="type" min-width="180" />
            <el-table-column label="数量" prop="count" width="120" align="right" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="section-card">
          <template #header>
            <span>实体表列表</span>
            <span class="header-extra">共 {{ entityTables.length }} 张</span>
          </template>
          <div v-if="entityTables.length > 0" class="tag-cloud">
            <el-tag
              v-for="t in entityTables"
              :key="t"
              size="small"
              effect="plain"
              class="entity-tag"
            >
              {{ t }}
            </el-tag>
          </div>
          <el-empty v-else description="暂无实体表" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 部署指南 -->
    <el-card shadow="never" v-if="manifest && manifest.deploymentGuide" class="section-card">
      <template #header>
        <span>部署指南</span>
      </template>
      <pre class="guide-pre">{{ manifest.deploymentGuide }}</pre>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.action-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;

  .label {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .hint {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    margin-left: 8px;
  }
}

.section-card {
  margin-bottom: 16px;
}

.header-extra {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: normal;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 320px;
  overflow: auto;

  .entity-tag {
    font-family: monospace;
  }
}

.guide-pre {
  margin: 0;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-regular);
}
</style>
