<script setup lang="ts">
import { ref } from 'vue'

import {
  clearRecentConnections,
  loadRecentConnections,
  rememberRecentConnection,
  removeRecentConnection,
  type RecentConnection
} from '@/stores/recent-connections'
import type { ConnectionSource } from '@/types/collection'

const source = defineModel<ConnectionSource>({ required: true })
const emit = defineEmits<{ select: [connection: RecentConnection] }>()
const recent = ref(loadRecentConnections())

function select(item: RecentConnection) {
  emit('select', item)
}

function remove(item: RecentConnection) {
  recent.value = removeRecentConnection(item, recent.value)
}

function clearAll() {
  recent.value = clearRecentConnections()
}

function remember(item: RecentConnection) {
  recent.value = rememberRecentConnection(item, recent.value)
}

defineExpose({ remember })
</script>

<template>
  <el-tabs v-model="source" class="connection-sources">
    <el-tab-pane label="项目设备" name="PROJECT_DEVICE" />
    <el-tab-pane label="快速连接" name="QUICK" />
    <el-tab-pane name="RECENT">
      <template #label>最近连接 <span class="recent-count">{{ recent.length }}</span></template>
    </el-tab-pane>
  </el-tabs>

  <section v-if="source === 'RECENT'" class="recent-panel" aria-label="最近连接">
    <header>
      <p>只保留脱敏端点，密码、私钥和脚本不会进入浏览器存储。</p>
      <el-button v-if="recent.length" text type="danger" @click="clearAll">清空全部</el-button>
    </header>
    <div v-if="!recent.length" class="recent-empty">暂无最近连接。测试成功或提交采集后会显示在这里。</div>
    <ul v-else class="recent-list">
      <li v-for="item in recent" :key="`${item.protocol}|${item.host}|${item.port}|${item.username}`">
        <button type="button" class="recent-list__select" @click="select(item)">
          <strong>{{ item.deviceLabel || item.host }}</strong>
          <span>{{ item.protocol }} · {{ item.username }}@{{ item.host }}:{{ item.port }}</span>
        </button>
        <el-button text type="danger" aria-label="删除这条最近连接" @click="remove(item)">删除</el-button>
      </li>
    </ul>
  </section>
</template>
