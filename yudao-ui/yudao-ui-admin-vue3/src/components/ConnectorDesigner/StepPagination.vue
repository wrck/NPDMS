<!-- src/components/ConnectorDesigner/StepPagination.vue -->
<script setup lang="ts">
/**
 * 连接器分步表单 — Step 5 分页配置（仅 REST）。
 *
 * <p>支持四种分页类型：
 * <ul>
 *   <li>NONE — 不分页</li>
 *   <li>OFFSET — offset/limit 偏移分页（totalCountPath 返回总数）</li>
 *   <li>PAGE — page/pageSize 页码分页（totalPagesPath 返回总页数）</li>
 *   <li>NEXT_LINK — 响应中包含下一页链接（nextLinkPath）</li>
 * </ul></p>
 */
import { reactive, watch } from 'vue'
import type { PaginationConfig, PaginationType } from '@/api/lowcode-connector'

const props = defineProps<{
  pagination: PaginationConfig
}>()

const emit = defineEmits<{
  'update:pagination': [value: PaginationConfig]
}>()

const form = reactive<PaginationConfig>({ ...props.pagination })

watch(
  () => props.pagination,
  (val) => {
    Object.assign(form, val)
  },
  { deep: true }
)

watch(
  form,
  (val) => {
    emit('update:pagination', { ...val })
  },
  { deep: true }
)

const TYPES: { label: string; value: PaginationType; desc: string }[] = [
  { label: '不分页', value: 'NONE', desc: '单次请求，不自动翻页' },
  { label: 'OFFSET 偏移', value: 'OFFSET', desc: '通过 offset/limit 参数分页，需返回总数' },
  { label: 'PAGE 页码', value: 'PAGE', desc: '通过 page/pageSize 参数分页，需返回总页数' },
  { label: 'NEXT_LINK 链接', value: 'NEXT_LINK', desc: '响应体含下一页链接字段' }
]
</script>

<template>
  <div class="step-pagination">
    <el-form :model="form" label-width="140px" class="pagination-form">
      <el-form-item label="分页类型">
        <el-radio-group v-model="form.type">
          <el-radio-button
            v-for="t in TYPES"
            :key="t.value"
            :value="t.value"
          >
            {{ t.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-alert
        :title="TYPES.find((t) => t.value === form.type)?.desc || ''"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <template v-if="form.type === 'OFFSET'">
        <el-form-item label="offset 参数名">
          <el-input v-model="form.offsetParam" placeholder="offset" style="width: 220px" />
        </el-form-item>
        <el-form-item label="limit 参数名">
          <el-input v-model="form.limitParam" placeholder="limit" style="width: 220px" />
        </el-form-item>
        <el-form-item label="总数 JSONPath">
          <el-input v-model="form.totalCountPath" placeholder="$.total" style="width: 320px" />
          <div class="field-hint">响应中总数的 JSONPath 路径</div>
        </el-form-item>
      </template>

      <template v-else-if="form.type === 'PAGE'">
        <el-form-item label="page 参数名">
          <el-input v-model="form.pageParam" placeholder="page" style="width: 220px" />
        </el-form-item>
        <el-form-item label="pageSize 参数名">
          <el-input v-model="form.pageSizeParam" placeholder="pageSize" style="width: 220px" />
        </el-form-item>
        <el-form-item label="总页数 JSONPath">
          <el-input v-model="form.totalPagesPath" placeholder="$.totalPages" style="width: 320px" />
          <div class="field-hint">响应中总页数的 JSONPath 路径</div>
        </el-form-item>
      </template>

      <template v-else-if="form.type === 'NEXT_LINK'">
        <el-form-item label="下一页链接 JSONPath">
          <el-input v-model="form.nextLinkPath" placeholder="$.nextLink" style="width: 320px" />
          <div class="field-hint">响应中下一页 URL 的 JSONPath 路径，存在则继续翻页</div>
        </el-form-item>
      </template>

      <el-form-item v-else>
        <span class="muted">该类型无需额外配置</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script lang="ts">
export default { name: 'StepPagination' }
</script>

<style scoped lang="scss">
.step-pagination {
  max-width: 720px;
  .field-hint {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .muted {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}
</style>
