<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getUserManager } from '@/auth/oidc'

const router = useRouter()
const errorMessage = ref('')

onMounted(async () => {
  try {
    const user = await (await getUserManager()).signinRedirectCallback()
    const destination = user.state && typeof user.state === 'string' ? user.state : '/projects/direct'
    await router.replace(destination)
  } catch {
    errorMessage.value = '登录回调无效或已经过期，请重新登录。'
  }
})
</script>

<template>
  <main class="centered-state" aria-live="polite">
    <h1>设备采集工作台</h1>
    <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>
    <p v-else>正在完成安全登录…</p>
  </main>
</template>

