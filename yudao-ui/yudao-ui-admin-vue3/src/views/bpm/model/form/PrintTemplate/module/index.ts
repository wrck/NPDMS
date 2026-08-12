import { IModuleConf } from '@wangeditor-next/editor'
import withProcessRecord from './plugin'
import renderElemConf from './render-elem'
import elemToHtmlConf from './elem-to-html'
import parseHtmlConf from './parse-elem-html'
import processRecordMenu from './menu/ProcessRecordMenu'

// 可参考 wangEditor 官方文档进行自定义扩展插件
const module: Partial<IModuleConf> = {
  editorPlugin: withProcessRecord,
  renderElems: [renderElemConf],
  elemsToHtml: [elemToHtmlConf],
  parseElemsHtml: [parseHtmlConf],
  menus: [processRecordMenu]
}

export default module
