import { defineAsyncComponent, type Component } from 'vue'
import ProjectBusinessPage from './ProjectBusinessPage.vue'
import { nativeBusinessKind } from './native/nativeBusinessKinds'
const nativePage = defineAsyncComponent(() => import('./native/NativeBusinessHost.vue'))
/** Project task and independent entry points share the original dedicated component and a scoped API adapter. */
export const projectBusinessPage = (stableCode: string): Component => nativeBusinessKind(stableCode) ? nativePage : ProjectBusinessPage
