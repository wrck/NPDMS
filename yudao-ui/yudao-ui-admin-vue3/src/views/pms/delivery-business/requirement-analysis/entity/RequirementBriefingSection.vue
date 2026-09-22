<template>
  <section v-if="readable" class="briefing-section" aria-label="工程交底书">
    <div class="briefing-heading">
      <h3>工程交底书</h3>
      <div><el-button :loading="loading" @click="load">刷新</el-button><el-button v-if="manage" @click="manage">生成与维护</el-button></div>
    </div>
    <p>查看本项目已生成的交底书。生成、审核和发布沿用工程交底流程。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading">
      <div v-for="item in rows" :key="item.id" class="briefing-document">
        <div><strong>{{ item.name }}</strong><span>{{ item.fileName || '尚未生成文件' }}</span></div>
        <dict-tag v-if="item.status !== undefined" :type="DICT_TYPE.PMS_BRIEFING_STATUS" :value="item.status" />
        <el-link v-if="fileLink(item)" :href="fileLink(item)" target="_blank" rel="noopener noreferrer" type="primary">查看 / 下载</el-link>
      </div>
      <el-empty v-if="!loading && !error && !rows.length" description="本项目尚无工程交底书" :image-size="56" />
    </div>
    <Pagination v-if="total > pageSize" v-model:page="pageNo" v-model:limit="pageSize" :total="total" @pagination="load" />
  </section>
</template>

<script setup lang="ts">
import * as BriefingApi from '@/api/pms/engineering/briefing'
import { checkPermi } from '@/utils/permission'
import { DICT_TYPE } from '@/utils/dict'
const props = defineProps<{ projectId: number; manage?: () => unknown }>()
const readable = computed(() => checkPermi(['pms:sol-briefing:query']))
const loading = ref(false), error = ref('')
const rows = ref<BriefingApi.BriefingVO[]>([])
const pageNo = ref(1), pageSize = ref(10), total = ref(0)
let generation = 0
const fileLink = (item: BriefingApi.BriefingVO) => {
  if (!item.fileUrl || item.status === 0 || item.status === 4) return undefined
  try { const url = new URL(item.fileUrl, window.location.origin); return ['http:', 'https:'].includes(url.protocol) ? url.href : undefined } catch { return undefined }
}
const load = async () => {
  const current = ++generation
  rows.value = []; total.value = 0; error.value = ''
  if (!readable.value || !props.projectId) return
  loading.value = true
  try {
    const result = await BriefingApi.getBriefingPage({ projectId: props.projectId, pageNo: pageNo.value, pageSize: pageSize.value })
    if (current !== generation) return
    rows.value = result.list || []; total.value = result.total || 0
  } catch { if (current === generation) error.value = '工程交底书加载失败，请重试。' }
  finally { if (current === generation) loading.value = false }
}
watch(() => props.projectId, () => { pageNo.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<style scoped>
.briefing-section { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--el-border-color-lighter); }
.briefing-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
h3 { margin: 0; font-size: 16px; }
p, .briefing-document span { color: var(--el-text-color-secondary); font-size: 13px; }
.briefing-document { display: flex; align-items: center; gap: 16px; padding: 16px 0; border-bottom: 1px solid var(--el-border-color-lighter); flex-wrap: wrap; }
.briefing-document > div { flex: 1; min-width: 180px; }
.briefing-document span { display: block; margin-top: 6px; }
</style>
