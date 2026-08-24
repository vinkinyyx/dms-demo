<template>
  <div class="crud-container">
    <div class="panel-title">{{ config.label || config.title || '数据管理' }}</div>
    <div class="page-toolbar">
      <template v-for="f in layoutFilterFields" :key="f.filterKey">
        <el-input
          v-if="f.componentType === 'input'"
          v-model="layoutFilters[f.filterKey]"
          :placeholder="f.placeholder || f.label"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
        />
        <el-select
          v-else-if="f.componentType === 'select'"
          v-model="layoutFilters[f.filterKey]"
          :placeholder="f.placeholder || f.label"
          :multiple="!!f.multiple"
          clearable
          style="width: 200px"
          @change="reload"
        >
          <el-option v-for="o in filterOptions(f)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker
          v-else-if="f.componentType === 'date'"
          v-model="layoutFilters[f.filterKey]"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="f.placeholder || f.label"
          clearable
          style="width: 200px"
          @change="reload"
        />
        <el-date-picker
          v-else-if="f.componentType === 'date-range'"
          v-model="layoutRangeFilters[f.filterKey]"
          type="daterange"
          value-format="YYYY-MM-DD"
          :start-placeholder="f.label + '开始'"
          :end-placeholder="f.label + '结束'"
          clearable
          style="width: 260px"
          @change="reload"
        />
      </template>

      <el-input v-if="searchable" v-model="keyword" :placeholder="keywordPlaceholder" clearable style="width: 260px" @keyup.enter="reload">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template v-for="f in legacyFilterFields" :key="f.k">
        <el-select v-if="f.filter.type === 'select'" v-model="colFilters[f.k]" :placeholder="f.l" clearable
          filterable style="width: 200px" @change="reload">
          <el-option v-for="o in selectFilterOptions(f)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <ResourcePicker
          v-else-if="f.filter.type === 'resource'"
          v-model="colFilters[f.filter.paramKey || f.k]"
          :resource="f.filter.resource"
          :placeholder="f.l"
          :clearable="true"
          style="width: 200px"
          @pick="reload"
        />
        <el-date-picker v-else-if="f.filter.type === 'date'" v-model="colFilters[f.k]" type="date" value-format="YYYY-MM-DD"
          :placeholder="f.l" clearable style="width: 200px" @change="reload" />
        <el-input v-else-if="f.filter.type === 'text'" v-model="colFilters[f.k]" :placeholder="f.l" clearable
          style="width: 200px" @keyup.enter="reload" />
        <el-input-number v-else-if="f.filter.type === 'number'" v-model="colFilters[f.k]" :placeholder="f.l" controls-position="right"
          :min="0" style="width: 200px" @change="reload" />
      </template>

            <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="onResetForm"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <div class="spacer" />
      <slot name="extra-actions" />
      <el-button v-if="canImport" type="primary" plain @click="importVisible = true"><el-icon><Upload /></el-icon>导入</el-button>
      <el-button v-if="canExport" type="primary" plain @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
      <el-button v-if="canCreate" type="primary" @click="onCreate"><el-icon><Plus /></el-icon>新增</el-button>
      <el-popover
        v-model:visible="columnSettingVisible"
        placement="bottom-end"
        :width="240"
        :show-arrow="false"
        popper-class="crud-col-popover"
        @show="openColumnSetting"
      >
        <template #reference>
          <el-button :icon="Setting" title="列设置">列设置</el-button>
        </template>
        <div style="margin-bottom:6px;color:var(--dms-text-2);font-size:12px">拖拽调整显示顺序（按账号保存）</div>
        <ul ref="columnSettingRef" class="crud-col-list">
          <li v-for="c in displayCols" :key="c.k" :data-col-key="c.k" class="crud-col-item">
            <el-icon class="col-handle"><Rank /></el-icon>
            <span class="crud-col-label">{{ c.l }}</span>
          </li>
        </ul>
        <div style="margin-top:8px;text-align:right">
          <el-button size="small" link @click="resetColumnOrder">重置默认</el-button>
        </div>
      </el-popover>
      <template v-for="b in visibleExtraToolbarButtons" :key="b.buttonKey">
        <el-button
          :type="b.buttonType || 'default'"
          v-has="b.permissionCode"
          @click="onToolbarButtonClick(b)"
        >{{ b.label }}</el-button></template>
    </div>

    <el-table ref="tableRef" :data="rows" v-loading="loading" border stripe size="small" @sort-change="onSortChange" :default-sort="{ prop: 'updatedAt', order: 'descending' }">
      <el-table-column v-for="c in displayCols" :key="c.k" :prop="c.k" :label="c.l"
        :width="c.fixedWidth || (c.minWidth == null && c.w != null && c.w <= 90 ? c.w : undefined)"
        :min-width="c.minWidth != null ? c.minWidth : (c.w != null && c.w > 90 ? Math.max(c.w, 120) : 120)"
        :sortable="c.sortable === false ? false : 'custom'" show-overflow-tooltip>
        <template #header>
          <span>{{ c.l }}</span>
          <el-icon v-if="c.filter" class="filter-icon" @click.stop="openFilter(c, $event)">
            <Filter :color="colFilters[c.k] != null && colFilters[c.k] !== '' ? '#1677ff' : '#c0c4cc'" />
          </el-icon>
        </template>
        <template #default="{ row }">
          <el-tag v-if="c.isStatus || c.k === 'status'" :type="statusTagType(row[c.k])" size="small">{{ statusText(row[c.k]) }}</el-tag>
          <el-tag v-else-if="typeof c.tag === 'function'" :type="(c.tag(row) || {}).type || 'info'" size="small">{{ (c.tag(row) || {}).text || displayCellValue(c, row) }}</el-tag>
          <el-link v-else-if="c.link && row[c.link.valueKey] != null" type="primary" @click="goLink(c.link, row)">{{ linkLabel(c, row) }}</el-link>
          <span v-else>{{ displayCellValue(c, row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" :fixed="tableOverflowX ? false : 'right'" :width="operationWidth">
        <template #default="{ row }">
          <div class="row-actions">
            <template v-for="(b, idx) in visibleFlatRowButtons(row)" :key="b.buttonKey + '_' + idx">
              <el-button
                v-if="idx < maxFlatRowButtons"
                size="small"
                :type="b.buttonType || 'default'"
                v-has="b.permissionCode"
                @click="onRowButtonClick(b, row)"
              >{{ b.label }}</el-button>
            </template>
            <el-dropdown v-if="overflowRowButtons(row).length" size="small" trigger="click" @command="(cmd) => onRowButtonClick(cmd, row)">
              <el-button size="small">更多<i class="el-icon--right"><el-icon><ArrowDown /></el-icon></i></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <template v-for="b in overflowRowButtons(row)" :key="b.buttonKey">
                    <el-dropdown-item
                      v-has="b.permissionCode"
                      :command="b"
                      :divided="b.rowButtonPosition === 'danger'"
                    >
                      <span :class="{ 'text-danger': b.buttonType === 'danger' || b.rowButtonPosition === 'danger' }">
                        {{ b.label }}
                      </span>
                    </el-dropdown-item>
                  </template>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- v4.2.4 漏斗过滤 popover：支持 text / number(-range) / date(-range) / select / resource / dict -->
    <el-popover
      ref="filterPopoverRef"
      :virtual-ref="filterTriggerRef"
      virtual-triggering
      :show-arrow="false"
      placement="bottom-start"
      :width="filterPopoverWidth"
      :visible="filterPopoverVisible"
      :teleported="false"
      popper-class="crud-filter-popover"
    >
      <div v-if="currentFilterCol" class="filter-pop-body">
        <div class="filter-pop-title">{{ currentFilterCol.l }} 过滤</div>

        <!-- 文本：模糊搜索（后端已默认 ILIKE） -->
        <el-input
          v-if="currentFilterCol.filter?.type === 'text'"
          v-model="colFilters[currentFilterCol.k]"
          placeholder="模糊搜索（支持部分匹配）"
          clearable
          @click.stop
          @keyup.enter="applyFilter"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <!-- 数字：单值或范围 -->
        <template v-else-if="currentFilterCol.filter?.type === 'number'">
          <el-input-number
            v-if="currentFilterCol.filter?.range !== true"
            v-model="colFilters[currentFilterCol.k]"
            :placeholder="'输入' + currentFilterCol.l"
            :min="0"
            controls-position="right"
            style="width: 100%"
            @click.stop
          />
          <div v-else class="filter-range-row" @click.stop>
            <el-input-number v-model="colRangeFilters[currentFilterCol.k + 'From']" placeholder="最小值" :min="0" controls-position="right" style="width: 48%" />
            <span class="filter-range-sep">~</span>
            <el-input-number v-model="colRangeFilters[currentFilterCol.k + 'To']" placeholder="最大值" :min="0" controls-position="right" style="width: 48%" />
          </div>
        </template>

        <!-- 日期/日期时间：单值或范围（datetime 支持选到时分秒） -->
        <template v-else-if="currentFilterCol.filter?.type === 'date' || currentFilterCol.filter?.type === 'datetime'">
          <el-date-picker
            v-if="currentFilterCol.filter?.range !== true"
            v-model="colFilters[currentFilterCol.k]"
            :type="currentFilterCol.filter?.type === 'datetime' ? 'datetime' : 'date'"
            :value-format="currentFilterCol.filter?.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
            :placeholder="'选择' + currentFilterCol.l"
            :teleported="false"
            style="width: 100%"
            @click.stop
          />
          <el-date-picker
            v-else
            v-model="colRangeFilters[currentFilterCol.k]"
            :type="currentFilterCol.filter?.type === 'datetime' ? 'datetimerange' : 'daterange'"
            :value-format="currentFilterCol.filter?.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
            range-separator="~"
            :start-placeholder="currentFilterCol.l + '开始'"
            :end-placeholder="currentFilterCol.l + '结束'"
            :teleported="false"
            style="width: 100%"
            @click.stop
          />
        </template>

        <!-- select / dict / remote / resource：统一走 selectFilterOptions 解析数据源 -->
        <template v-else-if="currentFilterCol.filter?.type === 'select' || currentFilterCol.filter?.type === 'dict' || currentFilterCol.filter?.remote || currentFilterCol.filter?.resource">
          <el-select
            v-model="colFilters[currentFilterCol.filter.paramKey || currentFilterCol.k]"
            :placeholder="'选择' + currentFilterCol.l"
            clearable
            filterable
            :teleported="false"
            style="width: 100%"
            @click.stop
          >
            <el-option v-for="o in currentFilterOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </template>

        <!-- 兜底：text -->
        <el-input
          v-else
          v-model="colFilters[currentFilterCol.k]"
          :placeholder="'输入' + (currentFilterCol.l || currentFilterCol.k)"
          clearable
          @click.stop
          @keyup.enter="applyFilter"
        />

        <div class="filter-pop-actions">
          <el-button link @click="clearCurrentFilter">清除</el-button>
          <el-button type="primary" @click="applyFilter">应用筛选</el-button>
        </div>
      </div>
    </el-popover>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next, sizes, jumper" :total="total"
        v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]"
        @current-change="fetchData" @size-change="onSizeChange" />
    </div>

    <!-- 表单抽屉 -->
    <el-drawer v-model="formVisible" direction="rtl" :size="drawerSize" :title="editing ? '编辑' : '新增'" :modal="true" :close-on-click-modal="false" destroy-on-close class="crud-area-drawer" :append-to-body="true">
      <div class="crud-form-container" :class="{ 'has-lines': hasLines }">
        <el-form :model="formData" label-width="110px">
          <template v-for="grp in groupedFields" :key="grp.name">
            <el-divider v-if="grp.name" content-position="left">{{ grp.name }}</el-divider>
            <el-row :gutter="16">
              <el-col v-for="f in visibleFormFields(grp.items)" :key="f.key" :span="formColSpan(f)">
                <div v-if="f.type === 'component-prices'" class="component-price-panel form-component-block">
                  <el-alert v-if="!formData.partnerId" type="warning" :closable="false" title="请先选择经销商，再维护BOM子件销售价" />
                  <el-table v-else :data="formData[f.key] || []" border size="small">
                    <el-table-column prop="productCode" label="子件SKU" width="150" />
                    <el-table-column prop="productName" label="子件名称" min-width="180" show-overflow-tooltip />
                    <el-table-column label="含税销售价" width="170">
                      <template #default="{ row }"><el-input-number v-model="row.inclPrice" :min="0" :precision="4" controls-position="right" size="small" style="width:100%" :disabled="!!row.existingId" @change="v => recalcComponentExcl(row)" /></template>
                    </el-table-column>
                    <el-table-column label="税率" width="140">
                      <template #default="{ row }"><el-input-number v-model="row.taxRate" :min="0" :max="1" :step="0.01" :precision="4" controls-position="right" size="small" style="width:100%" :disabled="!!row.existingId" @change="() => recalcComponentExcl(row)" /></template>
                    </el-table-column>
                    <el-table-column prop="exclPrice" label="不含税销售价" width="160">
                      <template #default="{ row }">{{ Number(row.exclPrice || 0).toFixed(4) }}</template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-form-item v-else-if="f.type !== 'lines'" :label="f.label" :required="f.required">
                  <ResourcePicker v-if="f.picker || f.type === 'product-picker'" v-model="formData[f.key]" :disabled="f.readonly || (editing && f.readonlyOnEdit)"
                    :resource="pickerResource(f)" :placeholder="f.label" :display-value="displayMap[f.key]" :extra-params="pickerExtraParams(f)" @pick="p => onFormPickerPick(f, p)" />
                  <el-input v-else-if="!f.type || f.type === 'text' || f.type === 'email'" v-model="formData[f.key]" :placeholder="f.placeholder" :readonly="f.readonly || (editing && f.readonlyOnEdit)" />
                  <el-input v-else-if="f.type === 'password'" v-model="formData[f.key]" type="password" show-password :placeholder="f.placeholder" />
                  <el-input v-else-if="f.type === 'textarea'" v-model="formData[f.key]" type="textarea" :rows="3" :placeholder="f.placeholder" />
                  <el-input-number v-else-if="f.type === 'number'" v-model="formData[f.key]" :controls="false" :precision="f.precision" :min="f.min != null ? f.min : undefined" :max="f.max != null ? f.max : undefined" style="width:100%" @change="() => onNumberFieldChange(f)" />
                  <el-date-picker v-else-if="f.type === 'date' || f.type === 'datetime'" v-model="formData[f.key]" :type="f.type === 'datetime' ? 'datetime' : 'date'" :value-format="f.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'" style="width:100%" />
                  <el-select v-else-if="f.type === 'select'" v-model="formData[f.key]" style="width:100%" clearable :multiple="!!f.multiple" :collapse-tags="!!f.multiple" :collapse-tags-tooltip="!!f.multiple" :teleported="false" popper-class="crud-select-popper">
                    <el-option v-for="o in selectOptions(f)" :key="o.value !== undefined ? o.value : o.label" :label="o.label" :value="o.value" />
                  </el-select>
                  <el-switch v-else-if="f.type === 'boolean'" v-model="formData[f.key]" :disabled="f.readonly" />
                  <AttachmentUploader v-else-if="f.type === 'attachment'" v-model="formData[f.key]" />
                  <MultiSelectPicker v-else-if="f.type === 'multiselect'" v-model="formData[f.key]" :resource="f.picker && f.picker.resource" />
                  <el-input v-else v-model="formData[f.key]" :placeholder="f.placeholder" />
                </el-form-item>
                <LinesEditor v-else v-model="formData[f.key]" :field="f" :context="lineEditorContext" />
              </el-col>
            </el-row>
          </template>
        </el-form>
      </div>
      <template #footer>
        <div style="padding: 0 20px 20px;">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveForm()">保存</el-button>
          <el-button v-if="canSubmitForm" type="success" :loading="submitting" @click="onSubmitAndAction">提交</el-button>
          <el-button v-if="canCancelForm" type="warning" @click="cancelCurrentRecord">取消单据</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" direction="rtl" :size="drawerSize" title="详情" :modal="true" destroy-on-close class="crud-area-drawer" :append-to-body="true">
      <div class="crud-detail-container">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item v-for="k in detailKeys" :key="k" :label="labelOf(k)">
            <el-tag v-if="k === 'status'" :type="statusTagType(detailData[k])" size="small">{{ statusText(detailData[k]) }}</el-tag>
            <el-tag v-else-if="isEnumKey(k)" type="info" size="small">{{ fmt(detailData[k], k) }}</el-tag>
            <span v-else>{{ detailValue(k) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <template v-if="detailLines.length">
          <el-divider content-position="left">明细</el-divider>
          <el-table :data="detailLines" border size="small">
            <el-table-column v-for="k in detailLineKeys" :key="k" :prop="k" :label="labelOf(k)">
              <template #default="{ row }">{{ fmt(row[k], k) }}</template>
            </el-table-column>
          </el-table>
        </template>
        <el-divider content-position="left">操作日志</el-divider>
        <el-table :data="detailLogs" border style="width:100%;margin-bottom:10px" v-loading="operationLoading">
          <el-table-column prop="username" label="操作人" width="120" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">{{ actionText(row.action) }}</template>
          </el-table-column>
          <el-table-column label="变更内容">
            <template #default="{ row }">{{ enhanceChanges(row.changes, detailData) }}</template>
          </el-table-column>
          <el-table-column label="操作时间" width="160"><template #default="{ row }">{{ formatDateTime(row.atTime) }}</template></el-table-column>
        </el-table>
        <el-empty v-if="!detailLogs.length" description="暂无操作日志" />
      </div>
    </el-drawer>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importVisible" title="导入数据" width="500px">
      <el-upload ref="uploadRef" :action="importUrl" :headers="uploadHeaders"
        :on-success="onImportSuccess" :on-error="onImportError" :show-file-list="false" accept=".xlsx,.xls" :auto-upload="false"
        :before-upload="beforeImport">
        <el-button type="primary">选择Excel文件</el-button>
      </el-upload>
      <p style="margin-top:12px;color:var(--dms-text-4)">支持 .xlsx 和 .xls 格式，请按模板填写数据。导入按“编码”判断：编码已存在则更新该行（留空的列保留原值），不存在则新增。</p>
      <el-button v-if="canDownloadTemplate" size="small" type="text" :loading="tplDownloading" @click="downloadTemplate">下载导入模板</el-button>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onBeforeUnmount, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Download, Filter, Search, Plus, ArrowDown, RefreshLeft, Setting, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import ResourcePicker from '@/components/ResourcePicker.vue'
import MultiSelectPicker from '@/components/MultiSelectPicker.vue'
import LinesEditor from '@/components/LinesEditor.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import { listResource, createResource, updateResource, deleteResource, getDetail, actionResource, getOperationLogs, httpGet } from '@/api/crud'
import { statusText, statusTagType, fmt, labelOf, reloadDicts, loadDict, getDictOptions, actionText, enhanceChanges, ENUMS } from '@/utils/dict'
import { getToken, getUser } from '@/utils/auth'
import { formatAuto, formatDateTime } from '@/utils/format'
import { usePageLayout, invalidatePageLayoutCache } from '@/composables/usePageLayout'

function dictLabel(col, v) {
  if (v == null || v === '') return '-'
  const opts = col && col.filter && col.filter.type === 'select' ? col.filter.options : null
  if (opts && opts.length) {
    const hit = opts.find(o => String(o.value) === String(v))
    if (hit && hit.label) return hit.label
  }
  return fmt(v, col.k)
}

const DETAIL_HIDDEN_KEYS = new Set([
  'tenantId', 'version', 'deleted', 'createBy', 'updateBy', 'createdBy', 'updatedBy',
  'creatorId', 'updaterId', 'schemaName', 'orgId', 'orgCode'
])

function isEnumKey(k) {
  return Object.prototype.hasOwnProperty.call(ENUMS, k)
}

function detailValue(k) {
  const row = detailData.value || {}
  if (isEnumKey(k)) return fmt(row[k], k)
  const stem = k.replace(/Id$/, '')
  if (/Id$/.test(k) && !k.endsWith('LineId') && k !== 'id') {
    const nameKeys = [`${stem}Name`, 'partnerName', 'dealerName', 'regionName',
      'categoryName', 'productLineName', 'productName', 'warehouseName',
      'hospitalName', 'supplierName', 'orgName', 'roleName']
    for (const nk of nameKeys) {
      if (row[nk] != null && row[nk] !== '') return row[nk]
    }
  }
  return fmt(row[k], k)
}

const props = defineProps({ config: { type: Object, required: true } })
const router = useRouter()
const route = useRoute()
// === 列表本地持久化：按用户账号 + config.key 隔离 ===
function currentAccountKey() {
  try {
    const u = getUser() || {}
    return u.username || u.account || u.loginName || 'guest'
  } catch (_) { return 'guest' }
}
function listStateKey() {
  return `dms:listState:${currentAccountKey()}:${(props.config && props.config.key) || 'unknown'}`
}
function colOrderKey() {
  return `dms:colOrder:${currentAccountKey()}:${(props.config && props.config.key) || 'unknown'}`
}
function loadListState() {
  try {
    const raw = localStorage.getItem(listStateKey())
    if (!raw) return null
    return JSON.parse(raw)
  } catch (_) { return null }
}
function saveListState(patch) {
  try {
    const cur = loadListState() || {}
    const next = { ...cur, ...patch }
    localStorage.setItem(listStateKey(), JSON.stringify(next))
  } catch (_) { /* localStorage disabled, ignore */ }
}
function loadColOrder() {
  try {
    const raw = localStorage.getItem(colOrderKey())
    if (!raw) return null
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : null
  } catch (_) { return null }
}
function saveColOrder(order) {
  try { localStorage.setItem(colOrderKey(), JSON.stringify(order || [])) } catch (_) {}
}
function isDateColKey(k) {
  return /At$|Time$|Date$|(From|To)$/i.test(String(k || ''))
}
function displayCellValue(c, row) {
  const v = row[c.k]
  if (v === null || v === undefined || v === '') return '-'
  if (typeof v === 'number' && /price|amount|Price|Amount/i.test(c.k || '')) return Number(v).toFixed(2)
  if (typeof v === 'string' && isDateColKey(c.k) && /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(v)) return formatDateTime(v)
  return displayCell(c, v)
}

const sidebarCollapsed = ref(localStorage.getItem('dms:sidebar:collapsed') === '1')
const drawerSize = computed(() => 'calc(100vw - ' + (sidebarCollapsed.value ? '64px' : '230px') + ')')
if (typeof window !== 'undefined') {
  const syncSidebar = () => { sidebarCollapsed.value = localStorage.getItem('dms:sidebar:collapsed') === '1' }
  window.addEventListener('storage', syncSidebar)
  window.addEventListener('sidebar-toggle', syncSidebar)
}

function closeRouteDrawers() {
  formVisible.value = false
  detailVisible.value = false
  importVisible.value = false
}

watch(() => route.fullPath, closeRouteDrawers)
onBeforeUnmount(closeRouteDrawers)

// === D13: 拉取 pageKey 布局配置（platform_button_configs 合并：平台默认 + 租户覆盖） ===
const { layout: pageLayout, load: loadPageLayout, visibleToolbar, visibleRowButtons: layoutRowButtons } = usePageLayout(props.config && props.config.key)
watch(() => props.config && props.config.key, (k) => { invalidatePageLayoutCache(k); loadPageLayout(true) }, { immediate: false })

function configRowButtons() {
  return (props.config.rowActions || []).map((a, idx) => ({
    buttonKey: a.key || ('configAction' + idx),
    label: a.label,
    buttonType: a.type || 'default',
    sortOrder: a.sortOrder || (15 + idx),
    statusIn: a.when,
    versionStatusIn: a.versionStatusIn || a.when,
    rowButtonPosition: a.type === 'danger' ? 'danger' : 'common',
    confirmRequired: !!a.confirm,
    confirmText: a.confirm,
    method: a.method || 'POST',
    path: a.path,
    noRefresh: a.noRefresh
  }))
}
function effectiveRowButtons() {
  const cfg = props.config || {}
  const hiddenByConfig = new Set()
  if (cfg.hideStatusActions) cfg.hideStatusActions.forEach((k) => hiddenByConfig.add(k))
  if (cfg.key === 'orders') ['submit', 'approve', 'reject'].forEach((k) => hiddenByConfig.add(k))
  const configured = layoutRowButtons().filter((b) => {
    if (hiddenByConfig.has(b.buttonKey)) return false
    if (cfg.noEdit && b.buttonKey === 'edit') return false
    if (cfg.noDelete && b.buttonKey === 'delete') return false
    if (cfg.readonly && ['edit', 'delete'].includes(b.buttonKey)) return false
    if (cfg.hideRowActions && Array.isArray(cfg.hideRowActions) && cfg.hideRowActions.includes(b.buttonKey)) return false
    if (cfg.key === 'sales-outs' && ['confirm','cancel','delete','edit','submit','approve','reject'].includes(b.buttonKey)) return false
    if (['confirm', 'execute'].includes(b.buttonKey)) {
      const sources = [].concat(cfg.rowActions || [], cfg.actions || [])
      const lists = Array.isArray(cfg.statusActions) ? cfg.statusActions : Object.values(cfg.statusActions || {})
      lists.forEach((list) => { if (Array.isArray(list)) sources.push(...list) })
      const explicit = sources.some((a) => normalizeActionKey(a) === b.buttonKey)
      if (!explicit) return false
    }
    return true
  })
  const buttonMap = new Map(configured.map((b) => [b.buttonKey, b]))
  configRowButtons().forEach((b) => { buttonMap.set(b.buttonKey, b) })
  if (cfg.detailable && !buttonMap.has('view')) buttonMap.set('view', { buttonKey: 'view', label: '查看', buttonType: 'primary', sortOrder: 10 })
  if (!cfg.noEdit && (cfg.editPath || Array.isArray(cfg.editableWhen)) && !buttonMap.has('edit')) buttonMap.set('edit', { buttonKey: 'edit', label: '编辑', buttonType: 'primary', sortOrder: 20 })
  if (!cfg.noDelete && Array.isArray(cfg.deletableWhen) && cfg.deletableWhen.length && !buttonMap.has('delete')) buttonMap.set('delete', { buttonKey: 'delete', label: '删除', buttonType: 'danger', sortOrder: 100 })
  const merged = Array.from(buttonMap.values())
  return merged.sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100))
}
const visibleRowButtons = computed(() => effectiveRowButtons())
// === 列顺序：本地优先 + 自动补 createdAt/updatedAt ===
const colOrder = ref(loadColOrder() || [])
watch(colOrder, (v) => saveColOrder(v), { deep: true })
const columnSettingVisible = ref(false)
const columnSettingRef = ref(null)
let _colSortable = null
const baseCols = computed(() => (props.config && props.config.cols) || [])
function ensureTimeCols(cols) {
  const has = (k) => cols.some((c) => c.k === k)
  const out = cols.slice()
  if (!has('createdAt')) out.push({ k: 'createdAt', l: '创建时间', w: 160, sortable: true })
  if (!has('updatedAt')) out.push({ k: 'updatedAt', l: '更新时间', w: 160, sortable: true })
  return out
}
const displayCols = computed(() => {
  const merged = ensureTimeCols(baseCols.value)
  if (!colOrder.value || !colOrder.value.length) return merged
  const byKey = new Map(merged.map((c) => [c.k, c]))
  const known = new Set(merged.map((c) => c.k))
  const ordered = []
  colOrder.value.forEach((k) => {
    if (known.has(k)) { ordered.push(byKey.get(k)); byKey.delete(k) }
  })
  byKey.forEach((c) => ordered.push(c))
  return ordered
})
function openColumnSetting() {
  columnSettingVisible.value = true
  nextTick(() => {
    const el = columnSettingRef.value
    if (!el) return
    if (_colSortable) { _colSortable.destroy(); _colSortable = null }
    _colSortable = Sortable.create(el, {
      animation: 150,
      handle: '.col-handle',
      ghostClass: 'col-ghost',
      onEnd: () => {
        const next = Array.from(el.querySelectorAll('[data-col-key]')).map((n) => n.getAttribute('data-col-key'))
        colOrder.value = next
      }
    })
  })
}
function resetColumnOrder() {
  colOrder.value = []
  ElMessage.success('列顺序已重置')
}
onBeforeUnmount(() => { if (_colSortable) { _colSortable.destroy(); _colSortable = null } })
function isCancelableOrder(row) { return props.config.key === 'orders' && row && row.status === 'APPROVED' && Number(row.shippedQty || 0) === 0 }

const rows = ref([])
const loading = ref(false)
const keyword = ref('')
const colFilters = reactive({})
const colRangeFilters = reactive({})
const layoutFilters = reactive({})
const layoutRangeFilters = reactive({})
// 从本地恢复分页状态（按用户+config.key 隔离）
const _restoredState = loadListState()
const page = ref(Number(_restoredState && _restoredState.page) > 0 ? Number(_restoredState.page) : 1)
const size = ref([10, 20, 50, 100].includes(Number(_restoredState && _restoredState.size)) ? Number(_restoredState.size) : 20)
const total = ref(0)
const sortField = ref((_restoredState && _restoredState.sortField) || 'updatedAt')
const sortOrder = ref((_restoredState && _restoredState.sortOrder) || 'descending')
watch([page, size, sortField, sortOrder], () => { saveListState({ page: page.value, size: size.value, sortField: sortField.value, sortOrder: sortOrder.value }) }); saveListState({ page: page.value, size: size.value, sortField: sortField.value, sortOrder: sortOrder.value })

const formVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const submitting = ref(false)
const formData = reactive({})
const displayMap = reactive({})
const lineEditorContext = computed(() => {
  if (props.config.key === 'promotions') {
    const first = Array.isArray(formData.rules) && formData.rules[0] ? formData.rules[0] : {}
    return { promoType: formData.promoType, targetType: first.targetType || 'SKU', cycle: first.cycle || 'ONCE' }
  }
  return {}
})
watch(() => formData.taxRate, (v) => {
  if (props.config.key === 'product-prices' && Array.isArray(formData.componentPrices)) {
    formData.componentPrices.forEach(row => { if (!row.existingId) { row.taxRate = Number(v || 0); recalcComponentExcl(row) } })
  }
})
watch(() => formData.priceType, () => {
  if (props.config.key === 'product-prices') formData.componentPrices = []
})
watch(() => formData.partnerId, async () => {
  if (props.config.key === 'product-prices') {
    formData.componentPrices = []
    if (formData.priceType === 'SALE' && formData.productId) {
      // Only load BOM component prices if the selected product is actually a BOM bundle
      await loadBundleComponentPrices()
    }
  }
})
watch(() => formData.promoType, (v) => {
  if (props.config.key !== 'promotions' || !Array.isArray(formData.rules)) return
  formData.rules.forEach((r) => { if (r) { r.promoType = v; if (!r.targetType) r.targetType = 'SKU'; if (!r.cycle) r.cycle = 'ONCE' } })
})

function pickerExtraParams(f) {
  if (props.config.key === 'product-prices' && f.key === 'productId') {
    return formData.priceType === 'PURCHASE' ? { excludeBundle: true } : {}
  }
  return f.extraParams || {}
}
function recalcComponentExcl(row) {
  const rate = Number(row.taxRate ?? formData.taxRate ?? 0)
  const incl = Number(row.inclPrice || 0)
  row.taxRate = rate
  row.exclPrice = incl > 0 ? Math.round(incl / (1 + rate) * 10000) / 10000 : 0
}
function isBundleProduct(raw) {
  return !!(raw && (raw.is_bom || raw.isBom || raw.isBundle || raw.productType === 'BUNDLE' || raw.productType === 'BOM'))
}
async function loadBundleComponentPrices() {
  if (props.config.key !== 'product-prices') return
  if (formData.priceType !== 'SALE' || !formData.partnerId || !formData.productId) {
    formData.componentPrices = []
    return
  }
  try {
    const bundleRes = await httpGet(`/api/product-bundles/product/${formData.productId}/active`, {}, { skipErrorToast: true, skip404Redirect: true })
    const bundle = bundleRes?.data?.data ?? bundleRes?.data
    if (!bundle || !Array.isArray(bundle.lines) || !bundle.lines.length) {
      formData.componentPrices = []
      return
    }
    const priceRes = await listResource('/api/product-prices', {
      productId: bundle.lines.map(line => line.childProductId).join(','),
      partnerType: 'DEALER',
      partnerId: formData.partnerId,
      priceScope: 'SALE',
      priceContext: 'BOM_COMPONENT',
      includeComponents: true,
      bomParentProductId: formData.productId,
      size: 200
    }).catch(() => null)
    const priceList = priceRes?.data?.list || priceRes?.data?.records || []
    formData.componentPrices = bundle.lines.map(line => {
      const existing = priceList.find(price => Number(price.productId) === Number(line.childProductId) && Number(price.bomParentProductId || 0) === Number(formData.productId || 0) && price.status === 'active')
      return {
        productId: line.childProductId,
        productCode: line.childProductCode,
        productName: line.childProductName,
        inclPrice: existing ? Number(existing.salesPrice || existing.inclPrice || 0) : 0,
        taxRate: existing ? Number(existing.taxRate ?? formData.taxRate ?? 0.13) : Number(formData.taxRate || 0.13),
        exclPrice: existing ? Number(existing.salesPriceExclTax || existing.exclPrice || 0) : 0,
        existingId: existing?.id || null
      }
    })
    formData.componentPrices.forEach(row => recalcComponentExcl(row))
  } catch (e) {
    formData.componentPrices = []
  }
}
async function onFormPickerPick(f, picked) {
  const raw = picked?.raw || picked?.row || picked || {}
  if (f.key === 'productId' && props.config.key === 'product-prices') {
    displayMap.productId = picked?.label || ((raw.code || '') + ' ' + (raw.nameCn || raw.name || ''))
    formData.componentPrices = []
    if (formData.priceType === 'SALE') {
      if (formData.partnerId) {
        // Always attempt load; the API returns empty/404 for non-BOM products (handled silently)
        await loadBundleComponentPrices()
      } else if (isBundleProduct(raw)) {
        formData.componentPrices = [{ productCode: '', productName: '请先选择经销商', inclPrice: 0, taxRate: 0.13, exclPrice: 0, _placeholder: true }]
      }
    }
  }
}
function money4(n) {
  const v = Number(n)
  if (!isFinite(v) || v <= 0) return 0
  return Math.round((v / (1 + (Number(formData.taxRate) || 0))) * 10000) / 10000
}
watch(() => [formData.salesPrice, formData.taxRate], () => {
  if (props.config.key === 'product-prices') formData.salesPriceExclTax = money4(formData.salesPrice)
})
watch(() => [formData.purchasePrice, formData.taxRate], () => {
  if (props.config.key === 'product-prices') formData.purchasePriceExclTax = money4(formData.purchasePrice)
})
const editingId = ref(null)

const detailVisible = ref(false)
const detailData = ref({})
const detailLines = ref([])
const detailLogs = ref([])
const operationLoading = ref(false)

const filterPopoverVisible = ref(false)
const currentFilterCol = ref(null)
// v4.2.4: popover 内下拉统一解析 options，支持 select/dict/remote/resource
const currentFilterOptions = computed(() => {
  if (!currentFilterCol.value) return []
  return selectFilterOptions({ k: currentFilterCol.value.k, l: currentFilterCol.value.l, filter: currentFilterCol.value.filter }) || []
})
const filterTriggerRef = ref(null)
const filterPopoverRef = ref(null)
// v4.2.7: 弹层改为受控模式（去掉 @update:visible），hover/失焦不再触发关闭；
// 仅通过外部 mousedown（不在弹层与触发图标内）关闭，避免选完结束日期后弹层自动消失。
function onDocPointerDown(e) {
  if (!filterPopoverVisible.value) return
  const trig = filterTriggerRef.value
  if (trig && trig.contains(e.target)) return
  const popEl = document.querySelector('.crud-filter-popover')
  if (popEl && popEl.contains(e.target)) return
  filterPopoverVisible.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocPointerDown, true))

// v4.2.7: date/datetime 面板宽度大于默认 popover 宽度，按筛选列类型动态适配
const filterPopoverWidth = computed(() => {
  const f = currentFilterCol.value?.filter
  if (f?.type === 'datetime') return f.range ? 800 : 400
  if (f?.type === 'date') return f.range ? 640 : 340
  return 280
})

// v4.2.7: 表格总列宽超出容器出现横向滚动时，固定右列（操作列）会悬浮覆盖最后一列
// 表头的筛选漏斗图标，导致无法点击；此时自动取消操作列固定，恢复筛选可用；不溢出时保留固定。
const tableRef = ref(null)
const tableOverflowX = ref(false)
function measureTableOverflow() {
  const root = tableRef.value?.$el || tableRef.value
  if (!root || !root.classList) return
  // el-table 自身通过该 class 标记横向滚动（列总宽超出容器），以它为准
  tableOverflowX.value = root.classList.contains('el-table--scrollable-x')
}
function scheduleOverflowMeasure() {
  nextTick(() => {
    measureTableOverflow()
    setTimeout(measureTableOverflow, 200)
  })
}
onMounted(() => {
  scheduleOverflowMeasure()
  window.addEventListener('resize', scheduleOverflowMeasure)
})
onBeforeUnmount(() => window.removeEventListener('resize', scheduleOverflowMeasure))
watch(() => [rows.value.length, props.config?.key], () => scheduleOverflowMeasure())

const importVisible = ref(false)
const uploadRef = ref(null)
const importing = ref(false)
const tplDownloading = ref(false)
const importUrl = computed(() => props.config.api + '/batch-import')
const uploadHeaders = computed(() => ({ 'Authorization': 'Bearer ' + (getToken() || '') }))

const searchable = computed(() => props.config.searchable !== false)
const keywordPlaceholder = computed(() => {
  const keys = (props.config.keywordFields || (props.config.cols || [])
    .filter(c => c.filter && c.filter.type === 'text')
    .map(c => c.l))
    .slice(0, 4)
  return keys.length ? `搜索${keys.join(' / ')}（支持多值）` : '关键词搜索（支持多值）'
})
const canCreate = computed(() => !props.config.readonly && !props.config.noCreate)
const canEdit = computed(() => !props.config.readonly)
const remoteFilterOptions = reactive({})
const canDelete = computed(() => !props.config.readonly && !props.config.noDelete)
const canBatchDelete = computed(() => false)
const canImport = computed(() => !props.config.readonly && props.config.importable === true)
const canExport = computed(() => props.config.exportable === true)
const canDownloadTemplate = computed(() => canImport.value && props.config.templateable !== false)
const hasCodeColumn = computed(() => (props.config.cols || []).some((c) => c.k === 'code'))
const showDetailButton = computed(() => props.config.showDetailButton === true || !props.config.detailable || !hasCodeColumn.value)


const activeLayoutFilterKeys = computed(() => new Set((pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .map((f) => f.filterKey)))

const layoutFilterFields = computed(() => (pageLayout.filters || [])
  .filter((f) => f.visible !== false && f.status !== 'inactive')
  .slice()
  .sort((a, b) => (a.sortOrder || 100) - (b.sortOrder || 100)))

const hasLayoutFilters = computed(() => layoutFilterFields.value.length > 0)
const showLegacyKeyword = computed(() => !hasLayoutFilters.value)
const legacyFilterFields = computed(() => hasLayoutFilters.value
  ? []
  : (props.config.cols || []).filter((c) => c.filter))

const groupedFields = computed(() => {
  const fields = props.config.form || []
  const map = {}
  const order = []
  fields.forEach((f) => {
    const g = f.group || ''
    if (!map[g]) { map[g] = []; order.push(g) }
    map[g].push(f)
  })
  return order.map((g) => ({ name: g, items: map[g] }))
})

const detailKeys = computed(() => {
  const data = detailData.value || {}
  const keys = Object.keys(data).filter((k) => {
    if (DETAIL_HIDDEN_KEYS.has(k)) return false
    const v = data[k]
    if (typeof v === 'object' && v !== null) return false
    return true
  })
  const idNamePairs = [
    ['categoryId', 'categoryName'], ['dealerId', 'dealerName'],
    ['hospitalId', 'hospitalName'], ['warehouseId', 'warehouseName'],
    ['supplierId', 'supplierName'], ['regionId', 'regionName'],
    ['productId', 'productName'], ['productLineId', 'productLineName'],
    ['partnerId', 'partnerName'], ['orgId', 'orgName'], ['roleId', 'roleName']
  ]
  idNamePairs.forEach(([idKey, nameKey]) => {
    if (keys.includes(idKey) && keys.includes(nameKey) && data[nameKey]) {
      const idx = keys.indexOf(idKey)
      if (idx > -1) keys.splice(idx, 1)
    }
  })
  const priority = ['id', 'code', 'name', 'nameCn', 'dealerName', 'productCode', 'productName',
    'warehouseName', 'status', 'stockStatus', 'qty', 'inSource', 'batchNo', 'serialNo',
    'orderType', 'priceType', 'currency', 'inclPrice', 'exclPrice', 'validFrom', 'validTo',
    'createdAt', 'updatedAt', 'remark', 'description']
  keys.sort((a, b) => {
    const ia = priority.indexOf(a)
    const ib = priority.indexOf(b)
    if (ia === -1 && ib === -1) return 0
    if (ia === -1) return 1
    if (ib === -1) return -1
    return ia - ib
  })
  return keys
})
const detailLineKeys = computed(() => (detailLines.value.length ? Object.keys(detailLines.value[0]).filter((k) => k !== 'id' && typeof detailLines.value[0][k] !== 'object') : []))

watch(() => props.config, async () => {
  page.value = 1
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  Object.keys(layoutFilters).forEach((k) => delete layoutFilters[k])
  Object.keys(layoutRangeFilters).forEach((k) => delete layoutRangeFilters[k])
  if (props.config && props.config.key) {
    invalidatePageLayoutCache(props.config.key)
    await loadPageLayout(true)
  }
  await Promise.all(layoutFilterFields.value.map((f) => f.dictType ? loadDict(f.dictType).catch(() => {}) : null))
  ;(props.config.cols || []).forEach((c) => {
    if (c.filter && c.filter.options && c.filter.options.__dictType) loadDict(c.filter.options.__dictType).catch(() => {})
  })
  await ensureRemoteFilterOptions()
  fetchData()
}, { immediate: true })

const PICKER_NAME_MAP = {
  orgId: 'orgName', dealerId: 'dealerName', hospitalId: 'hospitalName',
  warehouseId: 'warehouseName', supplierId: 'supplierName', regionId: 'regionName',
  productId: 'productName', categoryId: 'categoryName', contractId: 'contractName'
}

function isFull(f) { return f && (f.type === 'textarea' || f.full === true) }
function isLinesField(f) { return f && f.type === 'lines' }
function onNumberFieldChange(f) {
  if (!f.calc) return
  const rule = f.calc
  const num = v => Number(v || 0)
  if (rule.op === 'divide') {
    // target = from[0] / (1 + from[1])
    const gross = num(formData[rule.from[0]])
    const rate = num(formData[rule.from[1]])
    if (gross > 0 && rate >= 0) formData[rule.target] = Math.round(gross / (1 + rate) * 10000) / 10000
  }
}
function formColSpan(f) { return isLinesField(f) || isFull(f) ? 24 : 12 }
function visibleFormFields(items) {
  return (items || []).filter(f => {
    if (!f) return false
    if (typeof f.showIf === 'function') {
      try { return f.showIf(formData) } catch (_) { return true }
    }
    if (Array.isArray(f.showWhen) && f.showWhen.length === 2) {
      return formData[f.showWhen[0]] === f.showWhen[1]
    }
    if (props.config.key === 'product-prices' && ['inclPrice', 'exclPrice'].includes(f.key) && formData.priceType === 'SALE' && formData.componentPrices?.length) {
      return false
    }
    return true
  })
}
const hasLines = computed(() => (props.config.form || []).some((f) => f.type === 'lines'))
function pickerResource(f) { return f.type === 'product-picker' ? 'products' : (f.picker && f.picker.resource) || f.picker }

// 字段级下拉选项：支持静态 f.options，或 f.optionsUrl 异步加载（带缓存）
const asyncOptionsCache = {}
function selectOptions(f) {
  if (Array.isArray(f.options)) return f.options
  if (f.optionsUrl) {
    if (!asyncOptionsCache[f.key]) {
      asyncOptionsCache[f.key] = ref([])
      loadAsyncOptions(f)
    }
    return asyncOptionsCache[f.key].value
  }
  return []
}
async function loadAsyncOptions(f) {
  try {
    const res = await httpGet(f.optionsUrl, f.optionsParams || {})
    const list = Array.isArray(res.data) ? res.data : (res.data && (res.data.list || res.data.records)) || []
    asyncOptionsCache[f.key].value = list.map((it) => ({
      value: it[f.optionValue || 'id'],
      label: it[f.optionLabel || 'name'] != null ? String(it[f.optionLabel || 'name']) : String(it[f.optionValue || 'id'])
    }))
  } catch (e) {
    asyncOptionsCache[f.key].value = []
  }
}
function stripEmoji(s) { return String(s || '').replace(/[\u{1F000}-\u{1FFFF}\u2600-\u27BF✅❌✓]/gu, '').trim() }


// === D13: 工具栏按钮由 layout 驱动 ===
// 必含按钮：search/reset；内置按钮（create/export/import/batchDelete）由 CrudView 自身渲染，
// 后端布局若也下发了同 key 按钮则需过滤，避免重复
const mustButtonKeys = ['search', 'reset']
const builtinToolbarKeys = computed(() => {
  const keys = ['search', 'reset']
  if (canCreate.value) keys.push('create')
  if (canExport.value) keys.push('export')
  if (canImport.value) keys.push('import')
  if (canBatchDelete.value) keys.push('batchDelete', 'batch-delete')
  return keys
})
const extraToolbarButtons = computed(() => visibleToolbar()
  .filter((b) => !mustButtonKeys.includes(b.buttonKey))
  .filter((b) => !builtinToolbarKeys.value.includes(b.buttonKey))
  // 业务模块显式标记 noCreate 时，隐藏后端布局下发的“新增/新建”按钮，避免打开空白表单
  .filter((b) => !(props.config.noCreate && b.buttonKey === 'create')))
const visibleExtraToolbarButtons = extraToolbarButtons

function onResetForm() {
  keyword.value = ''
  Object.keys(colFilters).forEach((k) => delete colFilters[k])
  Object.keys(layoutFilters).forEach((k) => delete layoutFilters[k])
  Object.keys(layoutRangeFilters).forEach((k) => delete layoutRangeFilters[k])
  page.value = 1
  fetchData()
}

// === D13: 行内按钮由 layout.rowButtons 驱动，支持折叠 ===
const maxFlatRowButtons = computed(() => 2)
function visibleFlatRowButtons(row) {
  return visibleRowButtons.value.filter((b) => rowActionVisible(b, row))
}
function overflowRowButtons(row) {
  const flat = visibleFlatRowButtons(row)
  if (flat.length <= maxFlatRowButtons.value) return []
  return flat.slice(maxFlatRowButtons.value)
}
// R1.5：行内已有 查看/编辑/删除 等操作，编号/SKU 字段不再渲染链接，保留纯文本。
// 业务列如需自定义跳转，在 c.link 中显式声明即可。
function isLinkCol(c) { return false }
function rowActionVisible(b, row) {
  if (props.config.rowButtonKeys && !props.config.rowButtonKeys.includes(b.buttonKey)) return false
  if (props.config.rowButtonPermissions && typeof props.config.rowButtonPermissions[b.buttonKey] === 'function' && !props.config.rowButtonPermissions[b.buttonKey](row)) return false
  if (b.versionStatusIn && Array.isArray(b.versionStatusIn) && b.versionStatusIn.length && row && row.versionStatus != null && !b.versionStatusIn.includes(row.versionStatus)) return false
  if (b.statusIn && Array.isArray(b.statusIn) && b.statusIn.length && !b.statusIn.includes(row && (row.versionStatus || row.status))) return false
  if (b.statusNotIn && Array.isArray(b.statusNotIn) && b.statusNotIn.length && b.statusNotIn.includes(row && row.status)) return false
  const cfg = props.config || {}
  if (b.buttonKey === 'edit' && Array.isArray(cfg.editableWhen) && (!row || !rowMatchesStates(row, cfg.editableWhen))) return false
  if (b.buttonKey === 'delete' && Array.isArray(cfg.deletableWhen) && (!row || !cfg.deletableWhen.includes(row.status))) return false
  if (b.buttonKey === 'cancel' && Array.isArray(cfg.cancelableWhen) && (!row || !cfg.cancelableWhen.includes(row.status))) return false
  if (b.buttonKey === 'cancel' && cfg.key === 'orders' && !isCancelableOrder(row)) return false
  if (b.buttonKey === "newVersion" || b.buttonKey === "activate" || b.buttonKey === "deactivate") return true
  const legacy = legacyActionForButton(b, row)
  if (legacy === false || legacy === null) return false
  if (legacy && Array.isArray(legacy.when) && !legacy.when.includes(row && row.status)) return false
  return true
}

function rowMatchesStates(row, states) {
  if (!row || !Array.isArray(states)) return false
  return states.includes(row.versionStatus) || states.includes(row.status)
}

function legacyActionForButton(b, row) {
  const cfg = props.config || {}
  if (b.buttonKey === 'edit') {
    if (cfg.editableWhen && Array.isArray(cfg.editableWhen) && (!row || !rowMatchesStates(row, cfg.editableWhen))) return null
    if (cfg.editPath) return { key: 'edit', isRoute: true, path: cfg.editPath }
    return { key: 'edit' }
  }
  if (b.buttonKey === 'delete') {
    if (cfg.deletableWhen && Array.isArray(cfg.deletableWhen) && (!row || !cfg.deletableWhen.includes(row.status))) return null
    return { key: 'delete' }
  }
  if (b.buttonKey === 'view') {
    if (cfg.viewPath) return { key: 'view', isRoute: true, path: cfg.viewPath, readonlyQuery: true }
    if (cfg.detailPath) return { key: 'view', isRoute: true, path: cfg.detailPath, readonlyQuery: !!cfg.readonlyQuery }
    if (cfg.statusActions) {
      const list = Array.isArray(cfg.statusActions) ? cfg.statusActions : (cfg.statusActions[row && row.status] || [])
      const open = list.find((a) => normalizeActionKey(a) === 'open')
      if (open) return open
    }
    if (Array.isArray(cfg.actions)) {
      const open = cfg.actions.find((a) => normalizeActionKey(a) === 'open')
      if (open) return open
    }
    return { key: 'view' }
  }
  if (cfg.statusActions) {
    const list = Array.isArray(cfg.statusActions) ? cfg.statusActions : (cfg.statusActions[row && row.status] || [])
    const exact = list.find((a) => normalizeActionKey(a) === b.buttonKey)
    if (exact) return exact
  }
  if (Array.isArray(cfg.actions)) {
    const exact = cfg.actions.find((a) => normalizeActionKey(a) === b.buttonKey)
    if (exact) return exact
  }
  const standardActions = {
    submit: { method: 'POST', path: '/submit', type: 'warning', confirm: '确认提交？' },
    approve: { method: 'POST', path: '/approve', type: 'success', confirm: '确认审批通过？' },
    reject: { method: 'POST', path: '/reject', type: 'danger', confirm: '确认驳回？' },
    cancel: { method: 'POST', path: '/cancel', type: 'warning', confirm: '确认取消？' },
    confirm: { method: 'POST', path: '/confirm', type: 'success', confirm: '确认执行？' },
    execute: { method: 'POST', path: '/execute', type: 'success', confirm: '确认执行？' }
  }
  return standardActions[b.buttonKey] ? { key: b.buttonKey, ...standardActions[b.buttonKey] } : null
}

function normalizeActionKey(a) {
  return a.key || a.buttonKey || actionKeyFromPath(a.path)
}

function actionKeyFromPath(path) {
  if (!path) return ''
  return String(path).replace(/^\//, '').replace(/[^a-zA-Z0-9]+(.)/g, (_, c) => c.toUpperCase())
}

function filterOptions(f) {
  if (Array.isArray(f.options) && f.options.length) return f.options
  if (f.dictType === 'dealer' && remoteFilterOptions.dealers) return remoteFilterOptions.dealers
  if (f.dictType === 'supplier' && remoteFilterOptions.suppliers) return remoteFilterOptions.suppliers
  if (f.dictType === 'region' && remoteFilterOptions.regions) return remoteFilterOptions.regions
  if (f.dictType === 'product_category' && remoteFilterOptions.categories) return remoteFilterOptions.categories
  if (f.dictType === 'warehouse' && remoteFilterOptions.warehouses) return remoteFilterOptions.warehouses
  const col = (props.config.cols || []).find((c) => c.k === f.filterKey)
  if (col && col.filter) {
    if (Array.isArray(col.filter.options) && col.filter.options.length) return col.filter.options
    if (col.filter.remote && remoteFilterOptions[col.filter.remote]) return remoteFilterOptions[col.filter.remote]
  }
  if (f.dictType && Array.isArray(DICT_FALLBACK[f.dictType]) && DICT_FALLBACK[f.dictType].length) return DICT_FALLBACK[f.dictType]
  if (f.dictType) {
    const dict = getDictOptions(f.dictType)
    if (Array.isArray(dict) && dict.length) return dict
  }
  if (f.dictType === 'dealer' && remoteFilterOptions.dealers) return remoteFilterOptions.dealers
  return col && col.filter ? col.filter.options || [] : []
}

const DICT_FALLBACK = {
  product_status: [{value:'active',label:'启用'},{value:'inactive',label:'停用'}],
  dealer_status: [{value:'active',label:'启用'},{value:'inactive',label:'停用'}],
  hospital_status: [{value:'active',label:'启用'},{value:'inactive',label:'停用'}],
  warehouse_status: [{value:'active',label:'启用'},{value:'inactive',label:'停用'}],
  supplier_status: [{value:'active',label:'启用'},{value:'inactive',label:'停用'}],
  authorization_status: [{value:'active',label:'生效中'},{value:'expired',label:'已过期'},{value:'revoked',label:'已撤销'}],
  sales_order_status: [{value:'DRAFT',label:'草稿'},{value:'PENDING_APPROVAL',label:'审批中'},{value:'APPROVED',label:'已审批'},{value:'SHIPPING',label:'发货中'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'},{value:'REJECTED',label:'已驳回'}],
  sales_return_status: [{value:'DRAFT',label:'草稿'},{value:'PENDING_APPROVAL',label:'审批中'},{value:'APPROVED',label:'已审批'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'},{value:'REJECTED',label:'已驳回'}],
  purchase_order_status: [{value:'DRAFT',label:'草稿'},{value:'PENDING_APPROVAL',label:'审批中'},{value:'APPROVED',label:'已审批'},{value:'RECEIVING',label:'收货中'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'}],
  purchase_return_status: [{value:'DRAFT',label:'草稿'},{value:'PENDING_APPROVAL',label:'审批中'},{value:'APPROVED',label:'已审批'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'}],
  sales_out_status: [{value:'DRAFT',label:'草稿'},{value:'SHIPPED',label:'已发货'},{value:'PARTIAL_SHIPPED',label:'部分发货'},{value:'CANCELLED',label:'已取消'}],
  receipt_status: [{value:'DRAFT',label:'草稿'},{value:'RECEIVED',label:'已收货'},{value:'PARTIAL_RECEIVED',label:'部分收货'}],
  stock_move_status: [{value:'DRAFT',label:'草稿'},{value:'DONE',label:'已完成'},{value:'CANCELLED',label:'已取消'}],
  inventory_adjust_status: [{value:'DRAFT',label:'草稿'},{value:'APPROVED',label:'已审批'},{value:'DONE',label:'已完成'}],
  surgery_report_status: [{value:'DRAFT',label:'草稿'},{value:'SUBMITTED',label:'已提交'},{value:'APPROVED',label:'已审批'}],
  promotion_status: [{value:'draft',label:'草稿'},{value:'active',label:'启用'},{value:'inactive',label:'停用'}],
  user_status: [{value:'active',label:'启用'},{value:'inactive',label:'停用'},{value:'locked',label:'锁定'}],
  tenant_type: [{value:'MANUFACTURER',label:'厂商'},{value:'DEALER',label:'经销商'},{value:'HOSPITAL',label:'医院'}],
  dealer_level: [{value:'T1',label:'一级'},{value:'T2',label:'二级'}],
  hospital_level: [{value:'T1',label:'一级'},{value:'T2',label:'二级'}],
  stock_status: [{value:'QUALIFIED',label:'合格'},{value:'PENDING',label:'待检'},{value:'DEFECTIVE',label:'不合格'},{value:'QUARANTINED',label:'冻结'}]
}
function selectFilterOptions(f) {
  // legacy filters may set options=getDictOptions(type) at module load; the reactive array starts empty
  if (Array.isArray(f?.filter?.options)) {
    if (f.filter.options.length) return f.filter.options
    if (f.filter.options.__dictType) { loadDict(f.filter.options.__dictType).catch(() => {}); return f.filter.options }
  }
  const dt = f?.filter?.dictType
  if (dt && Array.isArray(DICT_FALLBACK[dt]) && DICT_FALLBACK[dt].length) return DICT_FALLBACK[dt]
  if (dt) {
    const d = getDictOptions(dt)
    if (Array.isArray(d) && d.length) return d
    loadDict(dt).catch(() => {})
    return d
  }
  const key = f?.filter?.remote || f?.filter?.resource
  if (!key) return []
  if (!remoteFilterOptions[key]) ensureRemoteFilterOptions().catch(() => {})
  return remoteFilterOptions[key] || []
}

async function ensureRemoteFilterOptions() {
  const remotes = new Set()
  ;(props.config.cols || []).forEach((c) => {
    if (c.filter && c.filter.remote) remotes.add(c.filter.remote)
    if (c.filter && c.filter.resource) remotes.add(c.filter.resource)
  })
  ;(pageLayout.filters || []).forEach((f) => {
    if (f.dictType === 'dealer') remotes.add('dealers')
    if (f.dictType === 'supplier') remotes.add('suppliers')
    if (f.dictType === 'region') remotes.add('regions')
    if (f.dictType === 'product_category') remotes.add('categories')
    if (f.dictType === 'warehouse') remotes.add('warehouses')
  })
  const endpoints = {
    categories: { url: '/api/product-categories', label: 'name' },
    dealers: { url: '/api/lookups/dealers', label: 'name' },
    suppliers: { url: '/api/lookups/suppliers', label: 'name' },
    regions: { url: '/api/regions', label: 'name' },
    warehouses: { url: '/api/lookups/warehouses', label: 'name' },
    hospitals: { url: '/api/lookups/hospitals', label: 'name' },
    products: { url: '/api/lookups/products', label: 'name' },
    'product-lines': { url: '/api/lookups/product-lines', label: 'name' },
    contracts: { url: '/api/lookups/contracts', label: 'name' }
  }
  await Promise.all([...remotes].map(async (key) => {
    if (remoteFilterOptions[key]) return
    const ep = endpoints[key]
    if (!ep) return
    try {
      const res = await httpGet(ep.url, { size: 500 })
      const list = res?.data?.list || res?.data?.records || res?.data || []
      remoteFilterOptions[key] = list.map((x) => ({
        value: x.value != null ? x.value : x.id,
        label: x.label || x[ep.label] || x.name || x.code || String(x.value != null ? x.value : (x.id || ''))
      }))
    } catch (e) { remoteFilterOptions[key] = [] }
  }))
}
function onToolbarButtonClick(b) {
  // 内置：search/reset 由布局代码直接处理；其余按 buttonKey 分发
  if (b.buttonKey === 'import') return handleImport()
  if (b.buttonKey === 'export') return handleExport()
  if (b.buttonKey === 'create') return onCreate()
  if (b.buttonKey === 'search') return reload()
  if (b.buttonKey === 'reset') return onResetForm()
  if (b.buttonKey === 'batch_delete') {
    // 业务模块可在 config.batchDeleteHandler 提供
    if (typeof props.config.batchDeleteHandler === 'function') {
      return props.config.batchDeleteHandler(selectedRows.value)
    }
    ElMessage.warning('请配置 config.batchDeleteHandler')
    return
  }
  // auto: config.toolbarHandlers?.['buttonKey']?.(b)
  if (props.config.toolbarHandlers && typeof props.config.toolbarHandlers[b.buttonKey] === 'function') {
    return props.config.toolbarHandlers[b.buttonKey](b)
  }
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调，请在 config.toolbarHandlers 中提供')
}

async function onRowButtonClick(b, row) {
  const cfg = props.config || {}
  if (props.config.rowActionHandlers && typeof props.config.rowActionHandlers[b.buttonKey] === 'function') {
    return props.config.rowActionHandlers[b.buttonKey](row, b, { router, openForm, refresh: fetchData })
  }
  if (b.buttonKey === 'view') {
    const legacyView = legacyActionForButton(b, row)
    if (legacyView && legacyView.key !== 'view') return doAction(row, legacyView)
    return openRowView(row)
  }
  if (b.buttonKey === 'edit') {
    if (cfg.editPath) { router.push(cfg.editPath + '/' + row.id); return }
    if (Array.isArray(cfg.editableWhen) && !rowMatchesStates(row, cfg.editableWhen)) { ElMessage.warning('当前状态不可编辑'); return }
    return openForm(row)
  }
  if (b.buttonKey === 'delete') {
    if (Array.isArray(cfg.deletableWhen) && !cfg.deletableWhen.includes(row && (row.versionStatus || row.status))) {
      ElMessage.warning('当前状态不可删除'); return
    }
    return onDelete(row)
  }
  if (b.buttonKey === 'reset_pwd' || b.buttonKey === 'reset-password' || b.buttonKey === 'resetPassword') return onResetPassword(row)
  if (b.buttonKey === 'unlock') return onUnlock(row)
  const legacy = legacyActionForButton(b, row)
  if (legacy) return doAction(row, legacy)
  if (['activate', 'deactivate'].includes(b.buttonKey)) return doAction(row, { path: '/' + b.buttonKey, method: 'POST', confirm: '确认执行该操作？' })
  ElMessage.info('未配置按钮 ' + b.buttonKey + ' 的回调')
}

async function onResetPassword(row) {
  try {
    const { value } = await ElMessageBox.prompt('请为「' + (row.name || row.username) + '」设置新密码（至少 8 位）', '重置密码', {
      inputType: 'password',
      inputPlaceholder: '请输入新密码',
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      inputValidator: (v) => {
        if (!v || v.length < 8) return '密码至少 8 位'
        if (v.length > 64) return '密码不超过 64 位'
        return true
      }
    })
    await actionResource(props.config.api, row.id, '/reset-password', 'post', { newPassword: value })
    ElMessage.success('密码已重置')
    fetchData()
  } catch (e) { /* 用户取消 */ }
}

async function onUnlock(row) {
  try {
    await ElMessageBox.confirm('确认解锁账号「' + (row.name || row.username) + '」？', '解锁', { type: 'info' })
    await actionResource(props.config.api, row.id, '/unlock', 'post')
    ElMessage.success('账号已解锁')
    fetchData()
  } catch (e) { /* 用户取消 */ }
}

const selectedRows = ref([])

// === D13: 动态 operationWidth 适配折叠 ===
const operationWidth = computed(() => {
  const n = visibleRowButtons.value.length
  if (n <= 1) return 96
  if (n === 2) return 170
  return 110
})
async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, limit: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    Object.keys(layoutFilters).forEach((k) => {
      const v = layoutFilters[k]
      if (v !== '' && v != null) params[k] = v
    })
    Object.keys(layoutRangeFilters).forEach((k) => {
      const v = layoutRangeFilters[k]
      if (Array.isArray(v) && v.length === 2) {
        params[k + 'From'] = v[0]
        params[k + 'To'] = v[1]
      } else if (k.endsWith('From') || k.endsWith('To')) {
        if (v !== "" && v != null) params[k] = v
      }
    })
    Object.keys(colFilters).forEach((k) => { if (colFilters[k] !== '' && colFilters[k] != null) params[k] = colFilters[k] })
    // Range filter supports two shapes:
    //   A) colRangeFilters[base] = [from, to]        -- el-date-picker daterange direct v-model
    //   B) colRangeFilters[base+'From'] / base+'To'  -- two el-input-number with separate v-model
    const rangeFrom = {}
    const rangeTo = {}
    Object.keys(colRangeFilters).forEach((k) => {
      const v = colRangeFilters[k]
      if (k.endsWith('From') || k.endsWith('To')) {
        const base = k.endsWith('From') ? k.slice(0, -4) : k.slice(0, -2)
        if (k.endsWith('From')) rangeFrom[base] = v
        else rangeTo[base] = v
      } else if (Array.isArray(v) && v.length === 2) {
        rangeFrom[k] = v[0]
        rangeTo[k] = v[1]
      }
    })
    const rangeBases = new Set([...Object.keys(rangeFrom), ...Object.keys(rangeTo)])
    rangeBases.forEach((b) => {
      const f = rangeFrom[b]
      const t = rangeTo[b]
      const has = (x) => x !== "" && x != null && !(Array.isArray(x) && x.length === 0)
      if (has(f)) params[b + 'From'] = f
      if (has(t)) params[b + 'To'] = t
    })
    if (sortField.value) params.sort = sortField.value + ',' + (sortOrder.value === 'ascending' ? 'asc' : 'desc')
    if (props.config.extraParams) Object.assign(params, props.config.extraParams)
    const res = await listResource(props.config.api, params)
    const data = res.data
    if (Array.isArray(data)) { rows.value = data; total.value = data.length }
    else if (data && Array.isArray(data.list)) { rows.value = data.list; total.value = data.total ?? data.list.length }
    else if (data && Array.isArray(data.records)) { rows.value = data.records; total.value = data.total ?? data.records.length }
    else { rows.value = []; total.value = 0 }
  } finally {
    loading.value = false
  }
}

function reload() { page.value = 1; fetchData() }
function onSizeChange() { page.value = 1; fetchData() }
function onSortChange({ prop, order }) { sortField.value = prop; sortOrder.value = order; fetchData() }

function linkLabel(c, row) { return c.linkLabelKey && row[c.linkLabelKey] ? row[c.linkLabelKey] : row[c.k] }
function displayCell(c, value) {
  const label = dictLabel(c, value)
  return label === value ? formatAuto(value, c.k) : label
}
function goLink(link, row) { router.push('/m/' + link.menu) }



const canSubmitForm = computed(() => props.config.submitAction && editing.value && editingId.value && ['DRAFT','REJECTED'].includes(formData.status))
const canCancelForm = computed(() => Array.isArray(props.config.cancelableWhen) && editingId.value && props.config.cancelableWhen.includes(formData.status))
const canCancelOrder = computed(() => canCancelForm.value)

async function cancelCurrentRecord() {
  ElMessageBox.confirm('确认取消该销售订单？已提交订单在未发货前允许取消。', '提示', { type: 'warning' })
    .then(async () => {
      await actionResource(props.config.api, editingId.value, '/cancel', 'POST')
      ElMessage.success('操作成功')
      formVisible.value = false
      fetchData()
    }).catch(() => {})
}

function rowEditable(row) {
  const cfg = props.config
  if (!cfg) return true
  if (cfg.editableWhen && Array.isArray(cfg.editableWhen)) {
    return rowMatchesStates(row, cfg.editableWhen)
  }
  return true
}
function rowDeletable(row) {
  const cfg = props.config
  if (!cfg) return true
  if (cfg.deletableWhen && Array.isArray(cfg.deletableWhen)) {
    return cfg.deletableWhen.includes(row && row.status)
  }
  return true
}

function rowActions(row) {
  const sa = props.config.statusActions
  if (sa) {
    if (Array.isArray(sa)) {
      return sa.filter((a) => !a.when || a.when.includes(row.status))
    }
    return sa[row.status] || []
  }
  if (props.config.actions) return props.config.actions
  return []
}

function doAction(row, a) {
  if (a.isRoute) {
    const sep = a.path.indexOf('?') > 0 ? '&' : '?'
    router.push(a.path + '/' + row.id + (a.readonlyQuery ? sep + 'mode=view' : ''))
    return
  }
  ElMessageBox.confirm(a.confirm || ('确认执行「' + stripEmoji(a.label) + '」？'), '提示', { type: a.type === 'danger' ? 'warning' : 'info' })
    .then(async () => {
      await actionResource(props.config.api, row.id, a.path, a.method || 'post', a.body)
      ElMessage.success('操作成功')
      if (!a.noRefresh) {
        fetchData()
      }
    })
    .catch(() => {})
}

function onCreate() {
  if (props.config.createPath) { router.push(props.config.createPath); return }
  openForm(null)
}
function openForm(row) {
  editing.value = !!row
  editingId.value = row ? row.id : null
  Object.keys(formData).forEach((k) => delete formData[k])
  Object.keys(displayMap).forEach((k) => delete displayMap[k])
  ;(props.config.form || []).forEach((f) => {
    if (row) {
      formData[f.key] = row[f.key]
      if (f.picker || f.type === 'product-picker') {
        const nameKey = PICKER_NAME_MAP[f.key] || (f.key.replace(/Id$/, '') + 'Name')
        if (row[nameKey]) displayMap[f.key] = row[nameKey]
      }
    } else if (f.value !== undefined) formData[f.key] = f.value
    else if (f.type === 'lines') formData[f.key] = []
    else if (f.type === 'boolean') formData[f.key] = false
    else if (f.type === 'attachment') formData[f.key] = null
    else formData[f.key] = ''
  })
  if (row && props.config.detailable && props.config.api) {
    getDetail(props.config.api, row.id).then((res) => {
      const d = res.data || {}
      normalizeDetailForForm(d, props.config)
      ;(props.config.form || []).forEach((f) => {
        if (d[f.key] !== undefined && d[f.key] !== null) formData[f.key] = d[f.key]
        if ((f.picker || f.type === 'product-picker') && d[f.key] != null) {
          const nameKey = PICKER_NAME_MAP[f.key] || (f.key.replace(/Id$/, '') + 'Name')
          if (d[nameKey]) displayMap[f.key] = d[nameKey]
        }
      })
    }).catch(() => {})
  }
  reloadDicts()
  formVisible.value = true
}


function normalizeDetailForForm(d, config) {
  if (config.key === 'promotions' && Array.isArray(d.rules)) {
    d.rules = d.rules.map((r) => ({ ...r, ...(r.ruleDetail || {}) }))
  }
}

async function saveForm(silent) {
  const fields = props.config.form || []
  for (const f of fields) {
    if (f.type === 'lines') {
      if (f.required && (!Array.isArray(formData[f.key]) || formData[f.key].length === 0)) {
        ElMessage.warning('请填写' + f.label + '?')
        return false
      }
      const rows = Array.isArray(formData[f.key]) ? formData[f.key] : []
      const allCols = f.cols || []
      for (let i = 0; i < rows.length; i++) {
        const row = rows[i] || {}
        const ctx = { ...formData, ...row }
        for (const c of allCols) {
          if (!c.required) continue
          if (typeof c.showIf === 'function') {
            let visible = true
            try { visible = c.showIf(ctx) } catch (_) { visible = true }
            if (!visible) continue
          }
          if (Array.isArray(c.showWhen) && c.showWhen.length === 2) {
            if (ctx[c.showWhen[0]] !== c.showWhen[1]) continue
          }
          const v = row[c.k]
          if (v === '' || v == null || (typeof v === 'number' && isNaN(v))) {
            ElMessage.warning('第 ' + (i + 1) + ' 行' + c.l + '不能为空')
            return false
          }
        }
      }
    } else if (f.type === 'component-prices' && formData.priceType === 'SALE' && formData.componentPrices?.length) {
      const rows = Array.isArray(formData[f.key]) ? formData[f.key] : []
      if (!formData.productId) { ElMessage.warning('请先选择BOM母件SKU'); return false }
      const rowsToCreate = rows.filter(row => !row.existingId)
      if (!rowsToCreate.length) { ElMessage.warning('所有子件在此经销商下已有价格，价格创建后不可编辑，请先失效旧价格'); return false }
      for (let i=0;i<rowsToCreate.length;i++) {
        const incl = Number(rowsToCreate[i].inclPrice)
        if (!Number.isFinite(incl) || incl < 0) { ElMessage.warning('待新增子件含税销售价不能小于0'); return false }
      }
    } else if (f.key === 'inclPrice' && props.config.key === 'product-prices' && formData.priceType === 'SALE' && formData.componentPrices?.length) {
      // BOM母件不维护价格，价格维护在子件行。
    } else if (f.required && !f.readonly) {
      const v = formData[f.key]
      if (v === '' || v == null) {
        ElMessage.warning('请填写' + f.label + '?')
        return false
      }
      if (f.type === 'password' && typeof v === 'string' && v.length < 8) {
        ElMessage.warning(f.label + '长度不能少于 8 位')
        return false
      }
    } else if (f.type === 'password' && formData[f.key] && !f.readonly) {
      if (String(formData[f.key]).length < 8) {
        ElMessage.warning(f.label + '长度不能少于 8 位')
        return false
      }
    }
  }
  saving.value = true
  try {
    const payload = {}
    Object.keys(formData).forEach((k) => {
      let v = formData[k]
      if (v === '' || v == null) return
      if (k === 'attachment' && typeof v === 'object') {
        payload.attachmentFileId = v.fileId
        payload.attachmentName = v.originalName
        payload.attachmentUrl = v.url
        return
      }
      if (props.config.key === 'product-prices' && k === 'componentPrices' && Array.isArray(v)) {
        payload[k] = v
        return
      }
      if (props.config.key === 'promotions' && Array.isArray(v)) {
        payload.promoType = formData.promoType
        payload.rules = v.map((r) => {
          const numberOrNull = (value) => {
            if (value === '' || value === null || value === undefined) return null
            const n = Number(value)
            return Number.isFinite(n) ? n : null
          }
          const idOrNull = (value) => {
            if (value === '' || value === null || value === undefined) return null
            return value
          }
          const detail = {
            targetType: r.targetType,
            targetProductId: idOrNull(r.targetProductId),
            targetProductLineId: idOrNull(r.targetProductLineId),
            thresholdQty: numberOrNull(r.thresholdQty),
            cycle: r.cycle,
            everyN: numberOrNull(r.everyN),
            giftProductId: idOrNull(r.giftProductId),
            giftQty: numberOrNull(r.giftQty),
            reduceAmount: numberOrNull(r.reduceAmount)
          }
          return { id: r.id, seq: r.seq, ruleDetail: detail }
        }).filter((r) => {
          const d = r.ruleDetail
          const hasTarget = d.targetProductId || d.targetProductLineId
          if (formData.promoType === 'GIFT') return hasTarget && d.giftProductId
          if (formData.promoType === 'FULL_REDUCTION') return hasTarget && Number(d.reduceAmount) > 0
          return hasTarget
        })
        return
      }
      payload[k] = v
    })
    delete payload.attachment
    if (editing.value) {
      await updateResource(props.config.api, editingId.value, payload)
      if (!silent) ElMessage.success('提交成功')
    } else {
      const createApi = props.config.apiCreate || props.config.api
      const res = await createResource(createApi, payload)
      if (res?.data?.id) editingId.value = res.data.id
      editing.value = true
      if (!silent) ElMessage.success('提交成功')
    }
    if (!silent) formVisible.value = false
    fetchData()
    return true
  } catch (e) {
    return false
  } finally {
    saving.value = false
  }
}

async function onSubmitAndAction() {
  formData.status = 'DRAFT'
  const saved = await saveForm(true)
  if (!saved || !editingId.value) return
  try {
    submitting.value = true
    await actionResource(props.config.api, editingId.value, props.config.submitAction.path || '/submit', props.config.submitAction.method || 'POST')
    ElMessage.success('提交成功')
    formVisible.value = false
    fetchData()
  } finally { submitting.value = false }
}

function onDelete(row) {
  ElMessageBox.confirm('确认删除该记录？', '提示', { type: 'warning' })
    .then(async () => { await deleteResource(props.config.api, row.id); ElMessage.success('删除成功'); fetchData() })
    .catch(() => {})
}
function onBatchDelete() {
  if (!selectedRows.value.length) return
  ElMessageBox.confirm(`确认删除选中的 ${selectedRows.value.length} 条记录？`, '批量删除', { type: 'warning' })
    .then(async () => {
      await Promise.all(selectedRows.value.map(row => deleteResource(props.config.api, row.id)))
      ElMessage.success('批量删除完成')
      selectedRows.value = []
      fetchData()
    })
    .catch(() => {})
}

async function isEditableRow(row) {
  const cfg = props.config
  if (!cfg.editableWhen || !Array.isArray(cfg.editableWhen) || !row) return false
  return rowMatchesStates(row, cfg.editableWhen)
}
async function openRowView(row) {
  if (props.config.viewPath) { router.push(props.config.viewPath + '/' + row.id + '?mode=view'); return }
  if (props.config.detailPath) { router.push(props.config.detailPath + '/' + row.id + (props.config.readonlyQuery ? '?mode=view' : '')); return }
  openDetail(row)
}
async function openDetail(row) {
  if (props.config.viewPath) { router.push(props.config.viewPath + '/' + row.id + '?mode=view'); return }
  if (props.config.detailPath) { router.push(props.config.detailPath + '/' + row.id + (props.config.readonlyQuery ? '?mode=view' : '')); return }
  detailData.value = row
  detailLines.value = []
  detailLogs.value = []
  detailVisible.value = true
  if (props.config.detailable) {
    try {
      const res = await getDetail(props.config.api, row.id)
      const d = res.data || {}
      detailData.value = d
      detailLines.value = d.lines || d.items || d.details || []
      // 加载操作日志
      if (detailData.value.id) {
        operationLoading.value = true
        try {
          const resourceType = resolveBusinessType(props.config.key)
          const businessType = resolveBizType(props.config.key)
          const resLog = await getOperationLogs(resourceType, detailData.value.id, businessType)
          detailLogs.value = Array.isArray(resLog.data) ? resLog.data : []
          operationLoading.value = false
        } catch (e) {
          console.error('加载操作日志失败', e)
          detailLogs.value = []
          operationLoading.value = false
        }
      } else {
        detailLogs.value = d.auditLogs || d.logs || []
      }
    } catch (e) { /* 用列表行兜底 */ }
  }
}

const BUSINESS_TYPE_MAP = {
  products: 'product',
  categories: 'productCategory',
  dealers: 'dealer',
  hospitals: 'hospital',
  warehouses: 'warehouse',
  suppliers: 'supplier',
  orders: 'order',
  'purchase-orders': 'purchaseOrder',
  'sales-returns': 'salesReturn',
  'purchase-returns': 'purchaseReturn',
  authorizations: 'authorization',
  'sales-outs': 'salesOut',
  receipts: 'receipt',
  'stock-moves': 'stockMove',
  'inventory-adjustments': 'inventoryAdjustment',
  promotions: 'promotion',
  'product-bundles': 'product_bundle',
  'product-prices': 'product_price',
  'surgery-reports': 'surgeryReport'
}

function resolveBusinessType(key) {
  return BUSINESS_TYPE_MAP[key] || key
}

const BIZ_TYPE_MAP = {
  'product-bundles': 'productBundle',
  promotions: 'promotion',
  orders: 'salesOrder',
  'sales-returns': 'salesReturn',
  'purchase-orders': 'purchaseOrder',
  'purchase-returns': 'purchaseReturn'
}
function resolveBizType(key) {
  return BIZ_TYPE_MAP[key] || null
}

function formatChangeJson(json) {
  if (!json) return ''
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    if (!obj || typeof obj !== 'object') return ''
    return Object.entries(obj).map(([field, diff]) => {
      const o = diff && diff.old != null ? String(diff.old) : '-'
      const n = diff && diff.new != null ? String(diff.new) : '-'
      return `${labelOf(field) || field}: ${o} → ${n}`
    }).join('\n')
  } catch (e) {
    return typeof json === 'string' ? json : ''
  }
}

function openFilter(col, event) {
  currentFilterCol.value = col
  filterTriggerRef.value = event && event.currentTarget ? event.currentTarget : null
  ensureRemoteFilterOptions().catch(() => {})
  filterPopoverVisible.value = !filterPopoverVisible.value
}

function applyFilter() {
  filterPopoverVisible.value = false
  reload()
}

function clearCurrentFilter() {
  if (currentFilterCol.value) {
    const k = currentFilterCol.value.k
    colFilters[k] = ''
    colFilters[currentFilterCol.value.filter?.paramKey || k] = ''
    colRangeFilters[k] = null
    colRangeFilters[k + 'From'] = null
    colRangeFilters[k + 'To'] = null
  }
  filterPopoverVisible.value = false
  reload()
}

function handleImport() {
  importVisible.value = true
}

function getAuthHeader() {
  return { 'Authorization': 'Bearer ' + (getToken() || '') }
}

function buildExportQuery() {
  const sp = new URLSearchParams()
  if (keyword.value && String(keyword.value).trim()) sp.set('keyword', String(keyword.value).trim())
  Object.keys(colFilters || {}).forEach((k) => {
    const v = colFilters[k]
    if (v === '' || v == null) return
    if (Array.isArray(v) || typeof v === 'object') return
    sp.set(k, String(v))
  })
  const sortDir = sortOrder.value === 'ascending' ? 'asc' : 'desc'
  sp.set('sort', (sortField.value || 'updatedAt') + ',' + sortDir)
  return sp.toString()
}

function parseContentDisposition(cd) {
  if (!cd) return null
  const m1 = cd.match(/filename\*=UTF-8''([^;]+)/i)
  if (m1) {
    try { return decodeURIComponent(m1[1]) } catch (e) { return m1[1] }
  }
  const m2 = cd.match(/filename="?([^"]+)"?/i)
  if (m2) return m2[1]
  return null
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 100)
}

async function handleExport() {
  const query = buildExportQuery()
  const url = props.config.api + '/actions/export' + (query ? ('?' + query) : '')
  try {
    const res = await fetch(url, { method: 'GET', headers: getAuthHeader() })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let msg = '导出失败: HTTP ' + res.status
      try {
        const j = JSON.parse(text)
        if (j && j.message) msg = j.message
      } catch (e) {}
      throw new Error(msg)
    }
    const blob = await res.blob()
    if (!blob || blob.size === 0) {
      throw new Error('导出内容为空')
    }
    const serverName = parseContentDisposition(res.headers.get('content-disposition'))
    const filename = serverName || ((props.config.key || 'export') + '_' + new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19) + '.xlsx')
    downloadBlob(blob, filename)
    ElMessage.success('导出成功，共 ' + blob.size + ' 字节')
  } catch (e) {
    console.error('[Export] failed:', e)
    ElMessage.error(e.message || '导出失败')
  }
}

function beforeImport(file) {
  if (!file) return false
  const name = (file.name || '').toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    ElMessage.error('仅支持 .xlsx / .xls 文件')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

async function submitImport() {
  if (!uploadRef.value) return
  if (importing.value) return
  importing.value = true
  try {
    uploadRef.value.submit()
  } catch (e) {
    importing.value = false
    ElMessage.error(e.message || '提交导入失败')
  }
}

async function downloadTemplate() {
  if (tplDownloading.value) return
  tplDownloading.value = true
  try {
    const res = await fetch(props.config.api + '/actions/export/template', {
      method: 'GET',
      headers: getAuthHeader()
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let msg = '下载模板失败: HTTP ' + res.status
      try {
        const j = JSON.parse(text)
        if (j && j.message) msg = j.message
      } catch (e) {}
      throw new Error(msg)
    }
    const blob = await res.blob()
    if (!blob || blob.size === 0) throw new Error('模板内容为空')
    const serverName = parseContentDisposition(res.headers.get('content-disposition'))
    const filename = serverName || ((props.config.key || 'template') + '_template.xlsx')
    downloadBlob(blob, filename)
    ElMessage.success('模板已下载')
  } catch (e) {
    console.error('[Template] failed:', e)
    ElMessage.error(e.message || '下载模板失败')
  } finally {
    tplDownloading.value = false
  }
}

function onImportSuccess(res, file) {
  importing.value = false
  let msg = '导入成功'
  if (res && typeof res === 'object') {
    if (res.message) msg = res.message
    else if (res.data) {
      if (res.data.message) msg = res.data.message
      else if (res.data.success !== undefined) {
        msg = `导入完成：成功 ${res.data.success} 条，失败 ${res.data.failed || 0} 条`
      }
    }
  }
  ElMessage.success(msg)
  importVisible.value = false
  fetchData()
}

function onImportError(err, file) {
  importing.value = false
  let msg = '导入失败'
  try {
    const status = err && err.status
    const raw = err && err.message ? String(err.message) : ''
    if (raw) {
      try {
        const j = JSON.parse(raw)
        if (j && j.message) msg = j.message
      } catch (e) {
        if (raw && raw.length < 200) msg = raw
      }
    }
    if (msg === '导入失败') {
      if (status === 404) msg = '导入接口不存在'
      else if (status === 403) msg = '没有导入权限'
      else if (status === 413) msg = '文件过大'
      else if (status === 500) msg = '服务器内部错误'
    }
  } catch (e) {}
  ElMessage.error(msg)
}
</script>

<style scoped>
.crud-container {
  background: var(--dms-bg-container);
  border-radius: 4px;
  padding: 16px;
  box-shadow: 0 1px 3px 0 rgb(0 0 0 / .1), 0 1px 2px -1px rgb(0 0 0 / .1);
}
.panel-title {
  margin-bottom: 16px;
  margin-top: 0;
  border-bottom: 1px solid var(--dms-border-1);
  padding-bottom: 8px;
  font-size: 1rem;
  color: var(--dms-color-primary);
  font-weight: 500;
}
.page-toolbar { gap: 8px 10px; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.filter-icon { cursor: pointer; margin-left: 4px; font-size: 14px; }
.row-actions { display: flex; gap: 6px; align-items: center; flex-wrap: nowrap; }
.row-actions :deep(.el-button) { margin-left: 0; padding: 7px 8px; }

.crud-form-container { padding: 12px 16px 20px; max-width: none; flex: 1; min-height: 0; box-sizing: border-box; display: flex; flex-direction: column; overflow: auto; }
.crud-form-container.has-lines { max-width: 100%; padding: 12px 20px 20px; }
.crud-form-container :deep(.el-divider) { margin: 14px 0 10px; }
.crud-form-container :deep(.el-form-item) { margin-bottom: 12px; }
.crud-form-container :deep(.el-form-item__label) { font-size: 13px; color: var(--dms-text-2); }
.crud-form-container :deep(.el-input__wrapper),
.crud-form-container :deep(.el-select),
.crud-form-container :deep(.el-input-number),
.crud-form-container :deep(.el-date-editor),
.crud-form-container :deep(.el-textarea__inner) { width: 100%; }
.crud-form-container :deep(.el-select > .el-select__wrapper) { width: 100%; }
.crud-detail-container { padding: 12px 16px 24px; max-width: none; flex: 1; min-height: 0; box-sizing: border-box; overflow: auto; }
.crud-detail-container :deep(.el-descriptions__label) { width: 130px; min-width: 130px; color: var(--dms-text-2); background: var(--dms-fill-1, #fafafa); }
:deep(.el-table .cell) { word-break: break-word; }
.crud-container :deep(.el-table) { width: 100%; }
.crud-form-container :deep(.el-form-item__label),
.crud-detail-container :deep(.el-descriptions__label) { width: 120px; min-width: 120px; }
.crud-form-container :deep(.el-input-number .el-input__wrapper) { width: 100%; }
.crud-form-container :deep(.el-select__wrapper),
.crud-form-container :deep(.el-date-editor.el-input__wrapper),
.crud-form-container :deep(.el-input__wrapper) { width: 100%; }


</style>

<style>
/* CrudView drawer: constrained to content area below topbar, beside sidebar */
.crud-area-drawer.el-drawer {
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  height: 100% !important;
  width: 100% !important;
  margin: 0 !important;
  border-radius: 0;
  box-shadow: -4px 0 12px rgba(0,0,0,.08);
  transform: none !important;
}
.el-overlay:has(.crud-area-drawer) {
  position: absolute !important;
  top: 56px !important;
  left: var(--dms-sidebar-w, 230px) !important;
  right: 0 !important;
  bottom: 0 !important;
  height: auto !important;
}
.crud-area-drawer.el-drawer .el-drawer__body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
}
.crud-area-drawer.el-drawer .el-drawer__footer {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 12px 20px;
}

/* v4.2.4 列表工具栏 / 筛选 popover 样式 */
.crud-container .page-toolbar { flex-wrap: wrap; }
.crud-container .page-toolbar .el-input,
.crud-container .page-toolbar .el-select,
.crud-container .page-toolbar .el-date-editor,
.crud-container .page-toolbar .el-input-number { width: 200px; }
.crud-container .page-toolbar .el-button { padding: 6px 14px; }
.crud-container .page-toolbar .el-button + .el-button { margin-left: 4px; }
.crud-container .filter-icon { font-size: 16px; padding: 0 4px; }
 .crud-filter-popover { padding: 12px !important; min-width: 280px; overflow: visible; }
.crud-filter-popover .filter-pop-body { display: flex; flex-direction: column; gap: 10px; }
.crud-filter-popover .filter-pop-title { font-weight: 600; color: var(--dms-text-2); }
.crud-filter-popover .filter-range-row { display: flex; align-items: center; gap: 6px; }
.crud-filter-popover .filter-range-sep { color: var(--dms-text-2); }
.crud-filter-popover .filter-pop-actions { display: flex; justify-content: flex-end; gap: 4px; margin-top: 2px; }
</style>
