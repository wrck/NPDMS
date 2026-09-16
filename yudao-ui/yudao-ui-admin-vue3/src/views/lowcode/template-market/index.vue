<script setup lang="ts">
/**
 * 低代码配置模板市场（批次5-T8 前端）。
 *
 * <p>顶部筛选区（关键字搜索 + configType 下拉 + category 下拉）+ 模板卡片网格
 * （每行 4 个 el-card）。卡片展示缩略图、名称、作者、tags、下载量、评分
 * （el-rate 只读）、版本、状态徽章，并提供详情/下载/评分/上架/下架操作。</p>
 *
 * <p>借鉴 Zoho 模板市场 / Appsmith 模板 / Mendix App Store。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archive,
  download,
  marketplace,
  publish,
  splitTags,
  unpublish,
  type LowCodeConfigTemplate
} from '@/api/lowcode-template'
import DetailDialog from './DetailDialog.vue'

defineOptions({ name: 'LowcodeTemplateMarketView' })

/** 配置类型选项（与后端 configType 枚举对齐） */
const configTypeOptions = [
  { label: '表单 FORM', value: 'FORM' },
  { label: '列表 LIST', value: 'LIST' },
  { label: '实体 ENTITY', value: 'ENTITY' },
  { label: '微流 MICROFLOW', value: 'MICROFLOW' },
  { label: '连接器 CONNECTOR', value: 'CONNECTOR' },
  { label: '规则 RULE', value: 'RULE' },
  { label: '标签页 TAB', value: 'TAB' },
  { label: '关联页 RELATED_PAGE', value: 'RELATED_PAGE' }
]

/** 分类候选（取自已加载模板的去重值） */
const categoryOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach((t) => {
    if (t.category) set.add(t.category)
  })
  return Array.from(set).map((c) => ({ label: c, value: c }))
})

const list = ref<LowCodeConfigTemplate[]>([])
const loading = ref(false)

/** 筛选条件 */
const keyword = ref('')
const filterConfigType = ref('')
const filterCategory = ref('')

/** 详情对话框 */
const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function statusTagType(status?: string): 'success' | 'warning' | 'info' | '' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  if (status === 'DRAFT') return 'warning'
  return ''
}

function ratingNumber(t: LowCodeConfigTemplate): number {
  const n = Number(t.rating)
  return Number.isNaN(n) ? 0 : n
}

async function load() {
  loading.value = true
  try {
    list.value = await marketplace(
      keyword.value || undefined,
      filterConfigType.value || undefined,
      filterCategory.value || undefined
    )
  } catch (e) {
    ElMessage.error('加载模板市场失败：' + (e instanceof Error ? e.message : String(e)))
    list.value = []
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  keyword.value = ''
  filterConfigType.value = ''
  filterCategory.value = ''
  load()
}

function openDetail(t: LowCodeConfigTemplate) {
  if (!t.id) return
  detailId.value = t.id
  detailVisible.value = true
}

async function quickDownload(t: LowCodeConfigTemplate) {
  if (!t.id) return
  try {
    await ElMessageBox.confirm(
      `确认下载模板「${t.name}」？下载将增加下载计数，无参数模板直接应用原始配置。`,
      '确认下载',
      { type: 'info' }
    )
  } catch {
    return /* cancelled */
  }
  try {
    await download(t.id)
    ElMessage.success('下载成功')
    await load()
  } catch (e) {
    ElMessage.error('下载失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

/** 卡片「管理」下拉命令分发 */
function handleManageCommand(cmd: string, t: LowCodeConfigTemplate) {
  if (cmd === 'toggle') {
    void togglePublish(t)
  } else if (cmd === 'archive') {
    void archiveTemplate(t)
  }
}

async function togglePublish(t: LowCodeConfigTemplate) {
  if (!t.id) return
  const isPublished = t.status === 'PUBLISHED'
  try {
    if (isPublished) {
      await ElMessageBox.confirm(`确认下架模板「${t.name}」？`, '确认下架', { type: 'warning' })
      await unpublish(t.id)
      ElMessage.success('已下架')
    } else {
      await publish(t.id)
      ElMessage.success('已上架')
    }
    await load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('操作失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

async function archiveTemplate(t: LowCodeConfigTemplate) {
  if (!t.id) return
  try {
    await ElMessageBox.confirm(
      `确认归档模板「${t.name}」？归档后将从市场下架。`,
      '确认归档',
      { type: 'warning' }
    )
    await archive(t.id)
    ElMessage.success('已归档')
    await load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('归档失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

onMounted(() => {
  load()
})
</script>

<template>
  <div style="padding: 16px">
    <!-- 顶部筛选区 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索模板名称 / 描述 / 作者"
          clearable
          style="width: 260px"
          @keyup.enter="load"
        />
        <el-select
          v-model="filterConfigType"
          placeholder="配置类型"
          clearable
          style="width: 180px"
        >
          <el-option v-for="o in configTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select
          v-model="filterCategory"
          placeholder="分类"
          clearable
          style="width: 180px"
        >
          <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" @click="load">搜索</el-button>
        <el-button @click="resetFilter">重置</el-button>
        <span class="filter-summary">共 {{ list.length }} 个模板</span>
      </div>
    </el-card>

    <!-- 模板卡片网格 -->
    <div v-loading="loading">
      <el-row v-if="list.length > 0" :gutter="16">
        <el-col
          v-for="t in list"
          :key="t.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
        >
          <el-card shadow="hover" class="tpl-card">
            <!-- 缩略图 -->
            <div class="thumb-wrap">
              <el-image
                v-if="t.thumbnail"
                :src="t.thumbnail"
                fit="cover"
                class="thumb"
              />
              <div v-else class="thumb-placeholder">
                <el-icon :size="40"><Goods /></el-icon>
                <span>{{ t.configType }}</span>
              </div>
              <el-tag
                v-if="t.status"
                :type="statusTagType(t.status)"
                size="small"
                class="status-badge"
              >
                {{ t.status }}
              </el-tag>
            </div>

            <!-- 名称 + 作者 -->
            <div class="title-line">
              <span class="title" :title="t.name">{{ t.name }}</span>
            </div>
            <div class="author-line">
              <span>{{ t.author || '匿名' }}</span>
              <el-tag size="small" type="info">{{ t.configType }}</el-tag>
            </div>

            <!-- tags -->
            <div class="tags-line">
              <el-tag
                v-for="tag in splitTags(t.tags).slice(0, 3)"
                :key="tag"
                size="small"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
              <span v-if="splitTags(t.tags).length === 0" class="muted">无标签</span>
            </div>

            <!-- 统计：下载量 / 评分 / 版本 -->
            <div class="stats-line">
              <span class="stat-item">
                <el-icon><Download /></el-icon>
                {{ t.downloadCount ?? 0 }}
              </span>
              <el-rate
                :model-value="ratingNumber(t)"
                disabled
                size="small"
                :max="5"
              />
              <span class="stat-item version">v{{ t.version || '1.0.0' }}</span>
            </div>

            <!-- 卡片操作 -->
            <div class="actions">
              <el-button size="small" @click="openDetail(t)">详情</el-button>
              <el-button size="small" type="primary" plain @click="quickDownload(t)">下载</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleManageCommand(cmd, t)">
                <el-button size="small">管理<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="toggle">
                      {{ t.status === 'PUBLISHED' ? '下架' : '上架' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="archive">归档</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-else-if="!loading" description="暂无模板，可调整筛选条件后重试" />
    </div>

    <!-- 详情对话框 -->
    <DetailDialog
      v-model="detailVisible"
      :template-id="detailId"
      @changed="load"
    />
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.filter-summary {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.tpl-card {
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding: 12px;
  }
}

.thumb-wrap {
  position: relative;
  width: 100%;
  height: 130px;
  margin-bottom: 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb {
  width: 100%;
  height: 100%;
}

.thumb-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);

  span {
    font-size: 12px;
  }
}

.status-badge {
  position: absolute;
  top: 6px;
  right: 6px;
}

.title-line {
  margin-bottom: 4px;
}

.title {
  font-weight: 600;
  font-size: 15px;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.author-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 6px;
}

.tags-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 24px;
  margin-bottom: 8px;

  .muted {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }
}

.stats-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  .stat-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  .stat-item.version {
    font-family: monospace;
  }
}

.actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
