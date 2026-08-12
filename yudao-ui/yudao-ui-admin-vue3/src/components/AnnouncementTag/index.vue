<template>
  <span>{{ announcementTitle }}</span>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as AnnouncementApi from '@/api/pms/engineering/announcement'

defineOptions({ name: 'AnnouncementTag' })

const props = defineProps({
  announcementId: {
    type: [Number, String] as any,
    default: undefined
  }
})

// 模块级缓存：避免同一公告ID重复请求
const announcementCache = new Map<number, string>()

const announcementTitle = ref<string>('')

const loadAnnouncementTitle = async (id: number) => {
  if (!id) {
    announcementTitle.value = '-'
    return
  }
  // 命中缓存直接返回
  if (announcementCache.has(id)) {
    announcementTitle.value = announcementCache.get(id) || '-'
    return
  }
  try {
    const res = await AnnouncementApi.getAnnouncement(id)
    const title = res?.title || `公告#${id}`
    announcementCache.set(id, title)
    announcementTitle.value = title
  } catch {
    const fallback = `公告#${id}`
    announcementCache.set(id, fallback)
    announcementTitle.value = fallback
  }
}

watch(
  () => props.announcementId,
  (val) => {
    if (val) loadAnnouncementTitle(Number(val))
    else announcementTitle.value = '-'
  },
  { immediate: true }
)
</script>
