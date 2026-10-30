<script setup lang="ts">
import '@/styles/management.css'
import { onMounted, ref } from 'vue'
import { isNavigationFailure, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import RequestState from '@/components/management/RequestState.vue'
import { managementApi } from '@/api/management'
import { createRequest } from '@/management/use-request'
import { offerScript, revokeScript } from '@/management/script-handoff'
import { useAccess } from '@/management/access'
import type { Page, ScriptSummary, ScriptContent } from '@/types/management'
const router = useRouter(), { can } = useAccess()
const { data, loading, error, run } = createRequest<Page<ScriptSummary>>()
const content = createRequest<ScriptContent>()
const namespace = ref(''), project = ref(''), page = ref(1), selected = ref<ScriptSummary>(), transferring = ref(false)
function load(reset = false) { if (reset) page.value = 1; selected.value = undefined; content.clear(); return run(signal => managementApi.scripts({ namespace: namespace.value || undefined, project: project.value || undefined, page: page.value - 1, size: 20 }, signal)) }
function read(row: ScriptSummary) { selected.value = row; void content.run(signal => managementApi.content(row.collectionId, signal)) }
async function transfer() {
  const value = content.data.value
  if (!value || transferring.value) return
  transferring.value = true
  let token: symbol | undefined
  try {
    await ElMessageBox.confirm(`载入 ${value.scriptKey} / ${value.version} 到连接工作台？仅替换脚本输入，不会自动连接或执行。`, '显式载入脚本', { confirmButtonText: '载入工作台', cancelButtonText: '取消', type: 'warning' })
    if (value !== content.data.value) return
    token = offerScript({ content: value.content, scriptKey: value.scriptKey, scriptVersion: value.version })
    const failure = await router.push('/projects/direct')
    if (isNavigationFailure(failure)) throw new Error('Navigation failed')
  } catch {
    if (token) { revokeScript(token); content.error.value = '未能进入连接工作台，本次脚本交接已撤销。请重新读取后载入。' }
  }
  finally { transferring.value = false }
}
onMounted(() => load())
</script>
<template>
  <AppShell
    class="management-page"
    element-layout
    title="脚本库"
    subtitle="仅展示授权引用的不可变版本"
  >
    <div class="scripts">
      <el-card shadow="never">
        <el-form
          inline
          @submit.prevent="load(true)"
        >
          <el-form-item label="命名空间">
            <el-input
              v-model="namespace"
              aria-label="脚本 命名空间"
            />
          </el-form-item><el-form-item label="项目">
            <el-input
              v-model="project"
              aria-label="脚本项目"
            />
          </el-form-item><el-button
            native-type="submit"
            :loading="loading"
          >
            查询
          </el-button>
        </el-form>
        <RequestState
          :loading="loading"
          :error="error"
          :empty="data?.items.length === 0"
          empty-text="暂无可访问脚本版本"
          @retry="load()"
        >
          <el-table :data="data?.items">
            <el-table-column
              prop="scriptKey"
              label="脚本"
            /><el-table-column
              prop="version"
              label="版本"
            /><el-table-column
              prop="source"
              label="来源"
            /><el-table-column
              prop="parserType"
              label="兼容解析类型"
            /><el-table-column
              prop="sha256"
              label="SHA-256"
              min-width="180"
            /><el-table-column label="内容">
              <template #default="{ row }">
                <el-button
                  v-if="row.contentReadable && row.source === 'LOCAL_MANAGED'"
                  text
                  @click="read(row)"
                >
                  安全读取
                </el-button><el-text
                  v-else
                  type="info"
                >
                  此版本不支持安全读取
                </el-text>
              </template>
            </el-table-column>
          </el-table>
        </RequestState>
        <el-pagination
          v-if="data"
          :current-page="page"
          :page-size="20"
          :total="data.total"
          layout="total, prev, pager, next"
          @current-change="(value: number) => { page = value; load() }"
        />
      </el-card>
      <el-card
        v-if="selected"
        shadow="never"
      >
        <template #header>
          <h2>已选择：{{ selected.scriptKey }} / {{ selected.version }}</h2>
        </template><RequestState
          :loading="content.loading.value"
          :error="content.error.value"
          @retry="read(selected)"
        >
          <template v-if="content.data.value">
            <pre>{{ content.data.value.content }}</pre><el-button
              type="primary"
              :disabled="!can('device-ops:collections:execute')"
              :loading="transferring"
              @click="transfer"
            >
              载入连接工作台
            </el-button><p>仅内存一次性交接，不落盘</p>
          </template>
        </RequestState>
      </el-card>
    </div>
  </AppShell>
</template>
