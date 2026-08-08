from pathlib import Path
p = Path('frontend-vue/src/components/CrudView.vue')
s = p.read_text(encoding='utf-8')
start = s.index('<template>')
end = s.index('</template>') + len('</template>')
new_template = '''<template>
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
          style="width: 160px"
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
          style="width: 160px"
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

      <el-input v-if="showLegacyKeyword" v-model="keyword" placeholder="关键词搜索" clearable style="width: 220px" @keyup.enter="reload">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template v-for="f in legacyFilterFields" :key="f.k">
        <el-select v-if="f.filter.type === 'select'" v-model="colFilters[f.k]" :placeholder="f.l" clearable
          style="width: 150px" @change="reload">
          <el-option v-for="o in f.filter.options" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker v-else-if="f.filter.type === 'date'" v-model="colFilters[f.k]" type="date" value-format="YYYY-MM-DD"
          :placeholder="f.l" clearable style="width: 150px" @change="reload" />
        <el-input v-else-if="f.filter.type === 'text'" v-model="colFilters[f.k]" :placeholder="f.l" clearable
          style="width: 180px" @keyup.enter="reload" />
        <el-input-number v-else-if="f.filter.type === 'number'" v-model="colFilters[f.k]" :placeholder="f.l" controls-position="right"
          :min="0" style="width: 130px" @change="reload" />
      </template>

      <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="onResetForm"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <div class="spacer" />
      <slot name="extra-actions" />
      <template v-for="b in extraToolbarButtons" :key="b.buttonKey">
        <el-button
          :type="b.buttonType || 'default'"
          v-has="b.permissionCode"
          @click="onToolbarButtonClick(b)"
        >{{ b.label }}</el-button>
      </template>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small" @sort-change="onSortChange" :default-sort="{ prop: 'updatedAt', order: 'descending' }">
      <el-table-column v-for="c in config.cols" :key="c.k" :prop="c.k" :label="c.l" :width="c.w"
        :sortable="c.sortable === false ? false : 'custom'" show-overflow-tooltip>
        <template #header>
          <span>{{ c.l }}</span>
          <el-icon v-if="c.filter" class="filter-icon" @click.stop="openFilter(c, $event)">
            <Filter :color="colFilters[c.k] != null && colFilters[c.k] !== '' ? '#409EFF' : '#C0C4CC'" />
          </el-icon>
        </template>
        <template #default="{ row }">
          <el-tag v-if="c.isStatus || c.k === 'status'" :type="statusTagType(row[c.k])" size="small">{{ statusText(row[c.k]) }}</el-tag>
          <el-link v-else-if="c.link && row[c.link.valueKey] != null" type="primary"
            @click="goLink(c.link, row)">{{ linkLabel(c, row) }}</el-link>
          <el-link v-else-if="c.k === 'code' && config.detailable" type="primary" @click="openDetail(row)">{{ row[c.k] }}</el-link>
          <span v-else>{{ dictLabel(c, row[c.k]) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" :width="operationWidth">
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
            <el-dropdown v-if="overflowRowButtons(row).length" size="small" trigger="click">
              <el-button size="small">更多<i class="el-icon--right"><el-icon><ArrowDown /></el-icon></i></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <template v-for="b in overflowRowButtons(row)" :key="b.buttonKey">
                    <el-dropdown-item
                      v-has="b.permissionCode"
                      :divided="b.rowButtonPosition === 'danger'"
                      @click="onRowButtonClick(b, row)"
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
'''
s = s[:start] + new_template + s[end:]
p.write_text(s, encoding='utf-8', newline='\n')
