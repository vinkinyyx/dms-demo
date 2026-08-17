<template>
  <div class="flow-editor">
    <div class="flow-toolbar">
      <el-button-group>
        <el-button size="small" @click="addApprovalNode">
          <el-icon><Plus /></el-icon>&nbsp;添加审批节点
        </el-button>
        <el-button size="small" @click="autoLayout" title="重新排列节点">
          <el-icon><Sort /></el-icon>&nbsp;自动排版
        </el-button>
        <el-button size="small" @click="zoomIn"><el-icon><ZoomIn /></el-icon></el-button>
        <el-button size="small" @click="zoomOut"><el-icon><ZoomOut /></el-icon></el-button>
        <el-button size="small" @click="zoomReset">1:1</el-button>
      </el-button-group>
      <div class="flow-tip">点击节点在右侧编辑；拖动节点调整位置；节点按从上到下顺序执行。</div>
    </div>

    <div class="flow-body">
      <div ref="containerRef" class="flow-canvas"></div>

      <el-drawer v-model="panelVisible" :title="panelTitle" direction="rtl" size="400px"
        :close-on-click-modal="false" append-to-body>
        <el-form v-if="activeNode" label-width="92px" size="default">
          <el-form-item label="节点名称" required>
            <el-input v-model="activeNode.name" placeholder="如：部门经理审批" maxlength="40" @input="syncActiveNode" />
          </el-form-item>
          <el-form-item label="多人审批">
            <el-radio-group v-model="activeNode.approveMode" @change="syncActiveNode">
              <el-radio-button value="ANY">任一人通过</el-radio-button>
              <el-radio-button value="ALL">所有人通过</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="超时(小时)">
            <el-input-number v-model="activeNode.timeoutHours" :min="0" :max="9999" controls-position="right" style="width:100%;" @change="syncActiveNode" />
          </el-form-item>
          <el-form-item label="允许操作">
            <el-checkbox v-model="activeNode.allowTransfer" @change="syncActiveNode">允许转办</el-checkbox>
            <el-checkbox v-model="activeNode.allowAddSign" @change="syncActiveNode">允许加签</el-checkbox>
          </el-form-item>

          <el-divider content-position="left">审批人</el-divider>
          <div v-for="(a, ai) in activeNode.assignees" :key="ai" class="assignee-row">
            <AssigneePicker v-model="activeNode.assignees[ai]" @update:modelValue="syncActiveNode" />
            <el-button link type="danger" size="small" @click="activeNode.assignees.splice(ai, 1); syncActiveNode()">移除</el-button>
          </div>
          <el-button size="small" @click="activeNode.assignees.push({ assigneeType: 'USER', refId: null, displayName: null }); syncActiveNode()">+ 添加审批人</el-button>

          <el-divider content-position="left">节点抄送</el-divider>
          <div v-for="(a, ai) in activeNode.ccs" :key="'c'+ai" class="assignee-row">
            <AssigneePicker v-model="activeNode.ccs[ai]" @update:modelValue="syncActiveNode" />
            <el-button link type="danger" size="small" @click="activeNode.ccs.splice(ai, 1); syncActiveNode()">移除</el-button>
          </div>
          <el-button size="small" @click="activeNode.ccs.push({ assigneeType: 'USER', refId: null, displayName: null }); syncActiveNode()">+ 添加抄送人</el-button>

          <div style="margin-top:24px;text-align:right;">
            <el-button type="danger" plain @click="deleteActiveNode">删除节点</el-button>
            <el-button type="primary" @click="panelVisible = false">完成</el-button>
          </div>
        </el-form>
      </el-drawer>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/index.css'
import { Plus, Sort, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import AssigneePicker from './AssigneePicker.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])

const containerRef = ref(null)
let lf = null
let internalChange = false
const panelVisible = ref(false)
const activeNodeId = ref(null)
const activeNode = ref(null)
let uid = 1

const panelTitle = '编辑审批节点'

function nodeNameFor(n) {
  if (!n) return ''
  if (n.name) return n.name
  return n.approveMode === 'ALL' ? '会签节点' : '或签节点'
}

function buildGraph(nodes) {
  uid = 1
  const lfNodes = []
  const lfEdges = []
  const xStart = 120
  const yStart = 60
  const gapY = 140
  let lastId = 'start'
  lfNodes.push({
    id: 'start', type: 'circle', x: xStart, y: yStart,
    text: '开始', properties: { _kind: 'start' }
  })
  ;(nodes || []).forEach((n, i) => {
    const id = 'n_' + (n.id != null ? n.id : ('tmp_' + (++uid)))
    lfNodes.push({
      id, type: 'rect', x: xStart, y: yStart + (i + 1) * gapY,
      text: nodeNameFor(n),
      properties: { _kind: 'approval', _data: n }
    })
    lfEdges.push({ id: 'e_' + lastId + '_' + id, type: 'polyline', sourceNodeId: lastId, targetNodeId: id })
    lastId = id
  })
  const endY = yStart + ((nodes || []).length + 1) * gapY
  lfNodes.push({
    id: 'end', type: 'circle', x: xStart, y: endY,
    text: '结束', properties: { _kind: 'end' }
  })
  lfEdges.push({ id: 'e_' + lastId + '_end', type: 'polyline', sourceNodeId: lastId, targetNodeId: 'end' })
  return { nodes: lfNodes, edges: lfEdges }
}

function renderNodes(nodes) {
  if (!lf) return
  internalChange = true
  lf.render(buildGraph(nodes))
  nextTick(() => { internalChange = false })
}

function cleanNode(n, i) {
  return {
    id: n.id,
    name: n.name || ('节点' + (i + 1)),
    approveMode: n.approveMode || 'ANY',
    allowTransfer: n.allowTransfer !== false,
    allowAddSign: n.allowAddSign !== false,
    timeoutHours: n.timeoutHours || 0,
    assignees: (n.assignees || []).filter(a => a && a.refId),
    ccs: (n.ccs || []).filter(a => a && a.refId)
  }
}

function signature(nodes) {
  return JSON.stringify((nodes || []).map((n) => ({
    id: n.id,
    name: n.name,
    approveMode: n.approveMode,
    allowTransfer: n.allowTransfer,
    allowAddSign: n.allowAddSign,
    timeoutHours: n.timeoutHours,
    assignees: (n.assignees || []).filter(a => a && a.refId),
    ccs: (n.ccs || []).filter(a => a && a.refId)
  })))
}

function extractNodes() {
  const data = lf ? lf.getGraphData() : { nodes: [], edges: [] }
  const approvals = (data.nodes || [])
    .filter(n => n.properties && n.properties._kind === 'approval')
    .map(n => {
      const d = { ...(n.properties._data || {}) }
      d._lfId = n.id
      d._y = n.y
      return d
    })
  approvals.sort((a, b) => (a._y || 0) - (b._y || 0))
  return approvals
}

function emitValue() {
  if (internalChange || !lf) return
  const cleaned = extractNodes().map(cleanNode)
  emit('update:modelValue', cleaned)
}

function addApprovalNode() {
  const graph = lf.getGraphData()
  const existing = extractNodes().length
  const endNode = (graph.nodes || []).find(n => n.properties && n.properties._kind === 'end')
  const lastApproval = [...(graph.nodes || [])]
    .filter(n => n.properties && n.properties._kind === 'approval')
    .sort((a, b) => b.y - a.y)[0]
  const source = lastApproval ? lastApproval.id : 'start'
  const id = 'new_' + Date.now()
  const data = {
    id: null, name: '审批节点' + (existing + 1), approveMode: 'ANY',
    allowTransfer: true, allowAddSign: true, timeoutHours: 24,
    assignees: [], ccs: []
  }
  const y = endNode ? endNode.y : 200
  lf.addNode({
    id, type: 'rect', x: 120, y,
    text: data.name,
    properties: { _kind: 'approval', _data: data }
  })
  const oldEdge = (graph.edges || []).find(e => e.sourceNodeId === source && e.targetNodeId === 'end')
  if (oldEdge) lf.deleteEdge(oldEdge.id)
  lf.addEdge({ type: 'polyline', sourceNodeId: source, targetNodeId: id })
  lf.addEdge({ type: 'polyline', sourceNodeId: id, targetNodeId: 'end' })
  autoLayout()
  openPanel(id)
  emitValue()
}

function openPanel(nodeId) {
  const node = lf.getNodeModelById(nodeId)
  if (!node || !node.properties || node.properties._kind !== 'approval') return
  const data = JSON.parse(JSON.stringify(node.properties._data || {}))
  if (!data.assignees) data.assignees = []
  if (!data.ccs) data.ccs = []
  activeNodeId.value = nodeId
  activeNode.value = data
  panelVisible.value = true
}

function syncActiveNode() {
  if (!activeNode.value || !activeNodeId.value) return
  const node = lf.getNodeModelById(activeNodeId.value)
  if (!node) return
  const data = JSON.parse(JSON.stringify(activeNode.value))
  node.setProperties({ _data: data })
  lf.updateText(activeNodeId.value, nodeNameFor(data))
  emitValue()
}

function deleteActiveNode() {
  if (!activeNodeId.value) return
  const targetId = activeNodeId.value
  const graph = lf.getGraphData()
  const incoming = (graph.edges || []).find(e => e.targetNodeId === targetId)
  const outgoing = (graph.edges || []).find(e => e.sourceNodeId === targetId)
  lf.deleteNode(targetId)
  if (incoming && outgoing) {
    lf.addEdge({ type: 'polyline', sourceNodeId: incoming.sourceNodeId, targetNodeId: outgoing.targetNodeId })
  }
  panelVisible.value = false
  activeNode.value = null
  activeNodeId.value = null
  emitValue()
}

function autoLayout() {
  if (!lf) return
  const data = lf.getGraphData()
  const xStart = 120
  const yStart = 60
  const gapY = 140
  const startNode = (data.nodes || []).find(n => n.properties && n.properties._kind === 'start')
  const endNode = (data.nodes || []).find(n => n.properties && n.properties._kind === 'end')
  const approvals = (data.nodes || [])
    .filter(n => n.properties && n.properties._kind === 'approval')
    .sort((a, b) => a.y - b.y)
  const move = (id, y) => {
    const model = lf.getNodeModelById(id)
    if (model) model.moveTo(xStart, y, false)
  }
  if (startNode) move(startNode.id, yStart)
  approvals.forEach((n, i) => move(n.id, yStart + (i + 1) * gapY))
  if (endNode) move(endNode.id, yStart + (approvals.length + 1) * gapY)
}

function zoomIn() { lf && lf.zoom(true) }
function zoomOut() { lf && lf.zoom(false) }
function zoomReset() { lf && lf.resetZoom() }

onMounted(() => {
  lf = new LogicFlow({
    container: containerRef.value,
    grid: { size: 10, visible: true, type: 'dot', config: { color: '#ebeef5' } },
    background: { color: '#fafbfc' },
    keyboard: { enabled: true },
    edgeType: 'polyline'
  })
  lf.setTheme({
    rect: { radius: 8, fill: '#eef4ff', stroke: '#2c6ef0', strokeWidth: 1.5 },
    circle: { r: 22, fill: '#f6ffed', stroke: '#52c41a', strokeWidth: 1.5 },
    polyline: { stroke: '#888', strokeWidth: 1.5, offset: 30 },
    nodeText: { fontSize: 13, color: '#303133' }
  })
  lf.on('node:click', ({ data }) => {
    if (data.properties && data.properties._kind === 'approval') openPanel(data.id)
  })
  lf.on('node:drag:stop', () => emitValue())
  lf.on('node:delete', () => emitValue())
  lf.on('edge:add', () => emitValue())
  lf.on('edge:delete', () => emitValue())
  renderNodes(props.modelValue || [])
})

onBeforeUnmount(() => { lf = null })

watch(() => props.modelValue, (val) => {
  if (!lf) return
  const current = signature(extractNodes().map(cleanNode))
  const incoming = signature(val || [])
  if (current !== incoming) renderNodes(val || [])
}, { deep: true })
</script>

<style scoped>
.flow-editor { border: 1px solid var(--dms-border-1); border-radius: 6px; background: var(--dms-bg-container); overflow: hidden; }
.flow-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border-bottom: 1px solid var(--dms-border-2); background: var(--dms-gray-50); }
.flow-tip { color: var(--dms-text-4); font-size: 12px; margin-left: auto; }
.flow-body { position: relative; }
.flow-canvas { width: 100%; height: 560px; }
.assignee-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
.assignee-row :deep(.el-select) { flex: 1; }
</style>
