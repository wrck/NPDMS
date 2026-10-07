import { defineComponent, h } from 'vue'
import { expect, it, vi } from 'vitest'
import List from './BusinessEntityList.vue'
import { mount, passthrough, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const column=defineComponent({inheritAttrs:false,setup:(_,{attrs})=>()=>h('column',attrs)})
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
it('honors inherited column order, visibility and server-side sort capability',()=>{
 const onSort=vi.fn(),mounted=mount(List,{rows:[],enableSorting:true,onSort,readableFields:[
  {code:'later',name:'Later',type:'TEXT',readable:true,writable:true,required:false,displayOrder:20,sortable:false},
  {code:'hidden',name:'Hidden',type:'TEXT',readable:true,writable:true,required:false,listVisible:false},
  {code:'first',name:'First',type:'TEXT',readable:true,writable:true,required:false,displayOrder:10,sortable:true}
 ]},{ElTable:passthrough,ElTableColumn:column,ElSelect:passthrough,ElOption:passthrough,ElInput:passthrough,ElTooltip:passthrough})
 try{
  const columns=all(mounted.root).filter(node=>node.type==='column'&&node.props?.prop)
  expect(columns.map(node=>node.props!.prop)).toEqual(['first','later'])
  expect(columns.map(node=>node.props!.sortable)).toEqual(['custom',false])
  const table=all(mounted.root).find(node=>node.props?.onSortChange)!
  ;(table.props!.onSortChange as Function)({prop:'first',order:'ascending'})
  expect(onSort).toHaveBeenLastCalledWith([{fieldCode:'first',direction:'ASC'}])
  ;(table.props!.onSortChange as Function)({prop:'later',order:'descending'})
  expect(onSort).toHaveBeenCalledTimes(1)
  ;(table.props!.onSortChange as Function)({prop:'first',order:null})
  expect(onSort).toHaveBeenLastCalledWith([])
 }finally{mounted.app.unmount()}
})
