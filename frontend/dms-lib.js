/* =====================================================================
 * DMS 前端共享库 v2
 * 新增：
 *   - LABELS 字段中文字典
 *   - Picker 联动选择器（避免用户输入 ID）
 *   - 分组表单（fields 支持 group 属性）
 *   - 完整的 Toast/Modal/Confirm/Detail
 * ===================================================================== */

var DMS = (function(){
  var TOKEN = localStorage.getItem('dms_access_token');
  var USER = JSON.parse(localStorage.getItem('dms_user') || '{}');

  // ============ 中文字段字典（用于表格头 + 详情表） ============
  var LABELS = {
    // 通用
    id:'编号', code:'编码', name:'名称', nameCn:'中文名称', nameEn:'英文名称',
    status:'状态', createdAt:'创建时间', updatedAt:'更新时间', createdBy:'创建人',
    remark:'备注', description:'说明', version:'版本号',
    tenantId:'租户ID',

    // 用户
    username:'账号', userType:'用户类型', email:'邮箱', phone:'手机号',
    lastLoginAt:'最近登录', wechatBound:'微信绑定', mustChangePassword:'需改密',
    orgId:'组织', dealerId:'经销商', lockedUntil:'锁定至',

    // 产品
    spec:'规格型号', unit:'单位', currentPrice:'参考单价', price:'单价',
    taxRate:'税率', udiRequired:'UDI追溯', warnMonths:'临期预警(月)',
    safetyQty:'安全库存', minOrderQty:'最小订购量', categoryId:'分类',

    // 经销商
    level:'级别', legalPerson:'法人', uscNo:'统一社会信用代码', regAddress:'注册地址',
    regCapital:'注册资本', foundedAt:'成立日期', businessScope:'经营范围',
    gspStatus:'GSP资质', gspExpire:'GSP到期', regionId:'区域',
    contactName:'联系人', contactPhone:'联系电话', contactEmail:'联系邮箱',

    // 医院/仓库
    type:'类型', hospitalId:'医院', address:'地址',
    warehouseId:'仓库', fromWarehouseId:'源仓库', toWarehouseId:'目标仓库',

    // 订单
    orderType:'订单类型', shipAddressId:'收货地址',
    amountInclTax:'含税金额', discountAmount:'优惠金额', finalAmount:'最终金额',
    expectedDate:'期望到货', submittedAt:'提交时间', approvedAt:'审批时间',
    parentOrderId:'父订单', shipSnapshot:'收货快照',

    // 合同/授权
    applicationType:'申请类型', contractCategory:'合同分类', category:'分类',
    validFrom:'生效开始', validTo:'生效结束',
    applicationId:'申请编号', contractId:'合同', pdfUrl:'PDF地址',
    authType:'授权类型', productId:'产品', terminalId:'终端',

    // 库存
    qty:'数量', batchNo:'批次号', serialNo:'序列号', prodDate:'生产日期',
    expDate:'到期日期', inSource:'入库来源',

    // 促销
    promoType:'促销类型', priority:'优先级', exclusive:'排他',
    ruleDetail:'规则详情', dealerScope:'经销商范围', productScope:'产品范围',

    // 发票
    invoiceNo:'发票号', refOrderId:'关联订单', refSalesOutId:'关联销售出库',
    amount:'金额', taxAmount:'税额', issueDate:'开票日期', imageUrl:'发票图片',

    // 销售
    salesDate:'销售日期', arrivedAt:'到货时间',

    // 系统/日志
    action:'操作', entityType:'实体类型', entityId:'实体编号', ipAddress:'IP地址',
    atTime:'时间', userAgent:'客户端', success:'是否成功', failReason:'失败原因',
    receiverId:'接收人', channel:'通道', title:'标题', content:'内容',
    isRead:'已读', loginType:'登录方式',
    industry:'行业', typeCode:'字典类型', typeName:'类型名称', itemCode:'字典项',
    label:'标签', sortOrder:'排序', scope:'作用域', key:'键', value:'值',
    parentId:'父级', seq:'顺序', settingsCount:'参数数',

    // Picker 通用
    ref:'关联'
  };

  function labelOf(k) { return LABELS[k] || k; }

  // ============ 中文枚举字典 ============
  var ENUMS = {
    status: [
      {value:'active',label:'启用'},{value:'inactive',label:'停用'},
      {value:'locked',label:'锁定'},{value:'blocked',label:'冻结'},
      {value:'draft',label:'草稿'},{value:'paused',label:'暂停'},
      {value:'expired',label:'已过期'},{value:'suspended',label:'挂起'}
    ],
    userType: [{value:'vendor',label:'厂商'},{value:'dealer',label:'经销商'}],
    orderType: [
      {value:'NORMAL',label:'常规订单'},{value:'SHORTAGE',label:'紧急补货'},
      {value:'CUSTOM',label:'定制订单'},{value:'EMERGENCY',label:'应急订单'}
    ],
    orderStatus: [
      {value:'DRAFT',label:'草稿'},{value:'SUBMITTED',label:'已提交'},
      {value:'APPROVED',label:'已审批'},{value:'REJECTED',label:'已驳回'},
      {value:'CANCELLED',label:'已取消'},{value:'COMPLETED',label:'已完成'}
    ],
    level: [{value:'T1',label:'一级'},{value:'T2',label:'二级'}],
    applicationType: [
      {value:'NEW',label:'新签'},{value:'MODIFY',label:'变更'},
      {value:'RENEW',label:'续签'},{value:'TERMINATE',label:'终止'}
    ],
    contractCategory: [
      {value:'SALES',label:'销售合同'},{value:'AUTHORIZATION',label:'授权合同'},
      {value:'DISTRIBUTION',label:'经销合同'}
    ],
    authType: [
      {value:'ORDER',label:'订货授权'},{value:'SALES_TO_HOSPITAL',label:'向医院销售'},
      {value:'RMA',label:'退换货'},{value:'LOAN',label:'借用'}
    ],
    warehouseType: [
      {value:'main',label:'主仓库'},{value:'sub',label:'分仓库'},{value:'hospital',label:'医院寄售仓'}
    ],
    promoType: [
      {value:'MOQ',label:'起订量(MOQ)'},{value:'FULL_REDUCTION',label:'满减'}
    ],
    boolean: [{value:'true',label:'是'},{value:'false',label:'否'}],
    hospitalLevel: [
      {value:'三甲',label:'三级甲等'},{value:'三乙',label:'三级乙等'},
      {value:'二甲',label:'二级甲等'},{value:'二乙',label:'二级乙等'},
      {value:'一级',label:'一级'},{value:'未定',label:'未定级'}
    ],
    gspStatus: [{value:'active',label:'有效'},{value:'expired',label:'已过期'},{value:'none',label:'无'}]
  };

  // ============ 认证 & API ============
  function requireAuth() {
    if (!TOKEN) { window.location.href = '/'; return false; }
    return true;
  }
  function logout() {
    localStorage.clear();
    window.location.href = '/';
  }
  function apiCall(path, options) {
    options = options || {};
    options.headers = options.headers || {};
    options.headers['Authorization'] = 'Bearer ' + TOKEN;
    if (options.body && typeof options.body !== 'string') {
      options.body = JSON.stringify(options.body);
      options.headers['Content-Type'] = 'application/json';
    }
    return fetch(path, options).then(function(r){
      if (r.status === 401 || r.status === 403) {
        toast('登录已过期，请重新登录', 'error');
        setTimeout(logout, 1500);
        throw new Error('Unauthorized');
      }
      return r.json();
    });
  }

  // ============ Toast ============
  function toast(msg, type) {
    type = type || 'info';
    var el = document.createElement('div');
    el.className = 'dms-toast dms-toast-' + type;
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(function(){ el.classList.add('show'); }, 10);
    setTimeout(function(){
      el.classList.remove('show');
      setTimeout(function(){ el.remove(); }, 300);
    }, 2600);
  }

  // ============ 状态徽章 ============
  function statusBadge(s) {
    if (s == null || s === '') return '<span class="dms-badge dms-badge-gray">-</span>';
    var u = String(s).toUpperCase();
    var cls = 'dms-badge-gray';
    var text = String(s);
    // 中文映射
    var map = {
      'APPROVED':'已审批','ACTIVE':'启用','COMPLETED':'已完成','EFFECTIVE':'生效',
      'SUBMITTED':'已提交','PENDING':'待处理','DRAFT':'草稿',
      'REJECTED':'已驳回','CANCELLED':'已取消','FAILED':'失败','LOCKED':'锁定',
      'INACTIVE':'停用','SUSPENDED':'挂起','EXPIRED':'已过期','PAUSED':'暂停','BLOCKED':'冻结',
      'TRUE':'是','FALSE':'否'
    };
    if (map[u]) text = map[u];
    if (u==='APPROVED'||u==='ACTIVE'||u==='COMPLETED'||u==='EFFECTIVE'||u==='TRUE') cls='dms-badge-green';
    else if (u==='SUBMITTED'||u==='PENDING'||u==='DRAFT'||u==='PAUSED') cls='dms-badge-orange';
    else if (u==='REJECTED'||u==='CANCELLED'||u==='FAILED'||u==='LOCKED'||u==='BLOCKED') cls='dms-badge-red';
    else if (u==='INACTIVE'||u==='EXPIRED'||u==='FALSE') cls='dms-badge-gray';
    else cls='dms-badge-blue';
    return '<span class="dms-badge ' + cls + '">' + text + '</span>';
  }

  function fmt(v, key) {
    if (v == null) return '-';
    if (typeof v === 'object') return JSON.stringify(v).substring(0, 80);
    if (typeof v === 'boolean') return v ? '✅' : '❌';
    var s = String(v);
    // 时间格式化
    if (key && (key === 'atTime' || key === 'createdAt' || key === 'updatedAt' ||
                key === 'submittedAt' || key === 'approvedAt' || key === 'lastLoginAt')) {
      return s.substring(0, 19).replace('T', ' ');
    }
    // 金额
    if (key && typeof v === 'number' && (key.indexOf('mount')>0 || key.indexOf('Price')>0 || key.indexOf('price')>0)) {
      return '¥ ' + Number(v).toFixed(2);
    }
    if (s.length > 60) return s.substring(0, 60) + '…';
    return s;
  }

  // ============ Modal 通用弹窗 ============
  function baseModal(title, contentHtml, footerHtml, opts) {
    opts = opts || {};
    var mask = document.createElement('div');
    mask.className = 'dms-modal-mask';
    var box = document.createElement('div');
    box.className = 'dms-modal-box';
    if (opts.width) box.style.maxWidth = opts.width;
    box.innerHTML =
      '<div class="dms-modal-hdr"><span>' + title + '</span><button class="dms-modal-close" type="button">&times;</button></div>' +
      '<div class="dms-modal-body">' + contentHtml + '</div>' +
      '<div class="dms-modal-ftr">' + (footerHtml || '') + '</div>';
    mask.appendChild(box);
    document.body.appendChild(mask);
    setTimeout(function(){ mask.classList.add('show'); }, 10);
    function close() {
      mask.classList.remove('show');
      setTimeout(function(){ mask.remove(); }, 200);
    }
    box.querySelector('.dms-modal-close').onclick = close;
    mask.onclick = function(e){ if(e.target === mask) close(); };
    return { mask: mask, box: box, close: close };
  }

  // ============ 表单弹窗（分组 + Picker）============
  /**
   * DMS.form({ title, fields, onSubmit })
   * fields: [{key,label,type,value,required,options,group,placeholder,picker,readonly}]
   *   type: text/number/date/textarea/select/checkbox/picker/password
   *   picker: {resource:'dealers|products|hospitals|warehouses|categories|regions|contracts|orders'}
   */
  function form(opts) {
    var groups = groupFields(opts.fields || []);
    var body = '';
    groups.forEach(function(g){
      if (g.name) body += '<div class="dms-form-group-title">' + g.name + '</div>';
      body += '<div class="dms-form-grid">';
      g.items.forEach(function(f){
        body += renderField(f);
      });
      body += '</div>';
    });
    var footer =
      '<button type="button" class="dms-btn dms-btn-cancel">取消</button>' +
      '<button type="button" class="dms-btn dms-btn-primary dms-form-ok">' + (opts.okText || '保存') + '</button>';
    var m = baseModal(opts.title || '表单', body, footer, { width:'720px' });

    // 绑定取消
    m.box.querySelector('.dms-btn-cancel').onclick = m.close;

    // 绑定 Picker 输入框点击
    m.box.querySelectorAll('[data-picker]').forEach(function(input){
      input.onclick = function(){
        var res = input.getAttribute('data-picker');
        openPicker(res, function(picked){
          input.value = picked.label;
          input.setAttribute('data-value', picked.value);
        });
      };
      // 清除按钮
      var clr = input.parentElement.querySelector('.dms-picker-clear');
      if (clr) clr.onclick = function(e){
        e.stopPropagation();
        input.value = '';
        input.removeAttribute('data-value');
      };
    });

    // 明细子表：新增行
    m.box.querySelectorAll('[data-lines-add]').forEach(function(btn){
      btn.onclick = function(){
        var key = btn.getAttribute('data-lines-add');
        var wrap = m.box.querySelector('[data-lines-body="' + key + '"]');
        var idx = wrap.querySelectorAll('.dms-line-row').length;
        wrap.insertAdjacentHTML('beforeend', renderLineRow(key, idx));
        bindLineRow(wrap.lastElementChild);
      };
    });
    // 已有行的绑定
    m.box.querySelectorAll('.dms-line-row').forEach(bindLineRow);

    function bindLineRow(row){
      row.querySelectorAll('[data-picker]').forEach(function(input){
        input.onclick = function(){
          var res = input.getAttribute('data-picker');
          openPicker(res, function(picked){
            input.value = picked.label;
            input.setAttribute('data-value', picked.value);
            // 自动填单价
            if (res === 'products' && picked.row && picked.row.price != null) {
              var unitInput = row.querySelector('[data-line-key="unitPrice"]');
              if (unitInput && !unitInput.value) unitInput.value = picked.row.price;
            }
          });
        };
      });
      row.querySelector('.dms-line-del').onclick = function(){ row.remove(); };
    }

    // 绑定提交
    m.box.querySelector('.dms-form-ok').onclick = function(){
      var data = {};
      var invalid = false;
      m.box.querySelectorAll('.dms-modal-body [data-key]').forEach(function(el){
        // 跳过明细行内输入
        if (el.closest('.dms-line-row')) return;
        var k = el.getAttribute('data-key');
        var required = el.hasAttribute('data-required');
        var v;
        if (el.hasAttribute('data-picker')) {
          v = el.getAttribute('data-value');
          if (v) v = Number(v);
        } else if (el.type === 'checkbox') {
          v = el.checked;
        } else if (el.type === 'number' && el.value !== '') {
          v = Number(el.value);
        } else if (el.tagName === 'SELECT' && el.getAttribute('data-bool') === '1') {
          v = el.value === 'true';
        } else {
          v = el.value;
        }
        if (required && (v === '' || v == null)) {
          el.classList.add('dms-field-error');
          invalid = true;
        } else {
          el.classList.remove('dms-field-error');
        }
        if (v !== '' && v != null) data[k] = v;
      });

      // 收集明细
      m.box.querySelectorAll('[data-lines-body]').forEach(function(wrap){
        var key = wrap.getAttribute('data-lines-body');
        var arr = [];
        wrap.querySelectorAll('.dms-line-row').forEach(function(row, i){
          var line = { seq: i + 1 };
          row.querySelectorAll('[data-line-key]').forEach(function(el){
            var k2 = el.getAttribute('data-line-key');
            if (el.hasAttribute('data-picker')) {
              var v2 = el.getAttribute('data-value');
              if (v2) line[k2] = Number(v2);
            } else if (el.type === 'number' && el.value !== '') {
              line[k2] = Number(el.value);
            } else if (el.value !== '') {
              line[k2] = el.value;
            }
          });
          // 只保留有 productId 的行
          if (line.productId) arr.push(line);
        });
        data[key] = arr;
      });

      if (invalid) { toast('请填写必填字段', 'warn'); return; }

      var ret = opts.onSubmit ? opts.onSubmit(data) : null;
      if (ret && ret.then) {
        ret.then(function(ok){ if(ok !== false) m.close(); });
      } else if (ret !== false) {
        m.close();
      }
    };

    return m;
  }

  function groupFields(fields) {
    var map = {}; var order = [];
    fields.forEach(function(f){
      var g = f.group || '';
      if (!map[g]) { map[g] = []; order.push(g); }
      map[g].push(f);
    });
    return order.map(function(g){ return { name: g, items: map[g] }; });
  }

  function renderField(f) {
    if (f.type === 'lines') return renderLinesField(f);
    var lbl = f.label || labelOf(f.key);
    var required = f.required ? ' data-required="1"' : '';
    var star = f.required ? ' <span style="color:#f5222d;">*</span>' : '';
    var full = (f.type === 'textarea') ? ' dms-form-full' : '';
    var v = f.value == null ? '' : String(f.value).replace(/"/g,'&quot;');
    var body = '';

    if (f.type === 'picker' || f.picker) {
      var res = (f.picker && f.picker.resource) || f.type;
      var dataValue = (f.value != null && f.value !== '') ? (' data-value="' + f.value + '"') : '';
      var displayVal = (f.displayValue != null) ? f.displayValue : (f.value != null ? f.value : '');
      body = '<div class="dms-picker-wrap">' +
             '<input type="text" data-key="' + f.key + '" data-picker="' + res + '" readonly ' +
             'placeholder="点击选择 · ' + lbl + '" value="' + (String(displayVal).replace(/"/g,'&quot;')) + '"' + dataValue + required + '>' +
             '<span class="dms-picker-clear" title="清除">×</span>' +
             '</div>';
    } else if (f.type === 'textarea') {
      body = '<textarea data-key="' + f.key + '" placeholder="' + (f.placeholder||'') + '"' + required + '>' + v + '</textarea>';
    } else if (f.type === 'select' && f.options) {
      body = '<select data-key="' + f.key + '"' + required + '>';
      if (!f.required) body += '<option value="">-- 请选择 --</option>';
      f.options.forEach(function(o){
        var lo = (typeof o === 'string') ? o : (o.label || o.value);
        var vo = (typeof o === 'string') ? o : o.value;
        body += '<option value="' + vo + '"' + (String(vo)===v?' selected':'') + '>' + lo + '</option>';
      });
      body += '</select>';
    } else if (f.type === 'checkbox' || f.type === 'boolean') {
      body = '<select data-key="' + f.key + '" data-bool="1"' + required + '>' +
             '<option value="false"' + (v!=='true'?' selected':'') + '>否</option>' +
             '<option value="true"' + (v==='true'?' selected':'') + '>是</option>' +
             '</select>';
    } else {
      var t = f.type || 'text';
      body = '<input type="' + t + '" data-key="' + f.key + '" placeholder="' + (f.placeholder||'') + '" value="' + v + '"' + required + '>';
    }

    return '<div class="dms-form-item' + full + '">' +
           '<label>' + lbl + star + '</label>' + body + '</div>';
  }

  // ============ 明细子表 lines 字段 ============
  function renderLinesField(f) {
    var key = f.key;
    var cols = f.cols || [
      {k:'productId', l:'产品', type:'picker', picker:'products', required:true},
      {k:'qty',       l:'数量', type:'number', required:true},
      {k:'unitPrice', l:'单价', type:'number'},
      {k:'taxRate',   l:'税率', type:'number'}
    ];
    var initLines = Array.isArray(f.value) ? f.value : [];
    if (initLines.length === 0) initLines = [{}];  // 默认给一行

    var head = '<tr>';
    cols.forEach(function(c){ head += '<th>' + c.l + (c.required?' *':'') + '</th>'; });
    head += '<th style="width:60px;">操作</th></tr>';

    var body = '';
    initLines.forEach(function(line, i){ body += renderLineRow(key, i, line, cols); });

    return '<div class="dms-form-item dms-form-full">' +
      '<label>' + (f.label || '订单明细') + ' <span style="color:#f5222d;">*</span></label>' +
      '<div class="dms-lines-box">' +
      '<table class="dms-lines-table"><thead>' + head + '</thead>' +
      '<tbody data-lines-body="' + key + '">' + body + '</tbody></table>' +
      '<button type="button" class="dms-btn dms-btn-primary dms-btn-sm" data-lines-add="' + key + '" style="margin-top:8px;">➕ 添加明细行</button>' +
      '</div></div>';
  }

  function renderLineRow(key, idx, line, cols) {
    line = line || {};
    cols = cols || [
      {k:'productId', l:'产品', type:'picker', picker:'products'},
      {k:'qty',       l:'数量', type:'number'},
      {k:'unitPrice', l:'单价', type:'number'},
      {k:'taxRate',   l:'税率', type:'number'}
    ];
    var tds = '';
    cols.forEach(function(c){
      var v = line[c.k] == null ? '' : String(line[c.k]);
      if (c.type === 'picker' || c.picker) {
        var res = c.picker || 'products';
        var dv = line[c.k] ? (' data-value="' + line[c.k] + '"') : '';
        tds += '<td><input type="text" data-line-key="' + c.k + '" data-picker="' + res + '" readonly placeholder="点击选择" value="' + v + '"' + dv + '></td>';
      } else {
        tds += '<td><input type="' + (c.type||'text') + '" data-line-key="' + c.k + '" value="' + v + '" placeholder="' + c.l + '"></td>';
      }
    });
    return '<tr class="dms-line-row" data-idx="' + idx + '">' + tds +
      '<td><button type="button" class="dms-btn dms-btn-danger dms-btn-sm dms-line-del">删除</button></td></tr>';
  }

  // ============ Picker 弹窗 ============
  // resource: 'dealers|products|hospitals|warehouses|categories|regions|contracts|orders|org-units'
  var PICKER_META = {
    dealers:    { title:'选择经销商', cols:[{k:'code',l:'编码'},{k:'name',l:'名称'},{k:'level',l:'级别'},{k:'status',l:'状态'}] },
    products:   { title:'选择产品',   cols:[{k:'code',l:'编码'},{k:'name',l:'名称'},{k:'spec',l:'规格'},{k:'unit',l:'单位'},{k:'price',l:'单价'}] },
    hospitals:  { title:'选择医院/终端', cols:[{k:'code',l:'编码'},{k:'name',l:'名称'},{k:'level',l:'等级'}] },
    warehouses: { title:'选择仓库',   cols:[{k:'code',l:'编码'},{k:'name',l:'名称'},{k:'type',l:'类型'}] },
    categories: { title:'选择分类',   cols:[{k:'code',l:'编码'},{k:'name',l:'名称'}] },
    regions:    { title:'选择区域',   cols:[{k:'code',l:'编码'},{k:'name',l:'名称'},{k:'level',l:'级别'}] },
    contracts:  { title:'选择合同',   cols:[{k:'code',l:'编号'},{k:'category',l:'分类'},{k:'status',l:'状态'}] },
    orders:     { title:'选择订单',   cols:[{k:'code',l:'订单号'},{k:'type',l:'类型'},{k:'amount',l:'金额'},{k:'status',l:'状态'}] },
    'org-units':{ title:'选择组织',   cols:[{k:'code',l:'编码'},{k:'name',l:'名称'},{k:'type',l:'类型'}] }
  };

  function openPicker(resource, onSelect) {
    var meta = PICKER_META[resource] || { title:'选择', cols:[{k:'code',l:'编码'},{k:'name',l:'名称'}] };
    var bodyHtml =
      '<div class="dms-picker-search">' +
        '<input type="text" placeholder="🔍 输入编码或名称搜索..." class="dms-picker-kw">' +
        '<button type="button" class="dms-btn dms-btn-primary dms-picker-btn">查询</button>' +
      '</div>' +
      '<div class="dms-picker-list"><div class="dms-loading">加载中...</div></div>';
    var m = baseModal(meta.title, bodyHtml, '<button type="button" class="dms-btn dms-btn-cancel">取消</button>', { width:'640px' });
    m.box.querySelector('.dms-btn-cancel').onclick = m.close;

    var kwInput = m.box.querySelector('.dms-picker-kw');
    var listBox = m.box.querySelector('.dms-picker-list');

    function load(){
      var kw = kwInput.value.trim();
      listBox.innerHTML = '<div class="dms-loading">加载中...</div>';
      var q = kw ? ('?keyword=' + encodeURIComponent(kw) + '&limit=50') : '?limit=50';
      apiCall('/api/lookups/' + resource + q).then(function(d){
        if (d.code !== 0) { listBox.innerHTML = '<div class="dms-error-hint">' + d.message + '</div>'; return; }
        var list = d.data || [];
        if (!list.length) { listBox.innerHTML = '<div class="dms-empty">📭 未找到匹配结果</div>'; return; }
        var h = '<table class="dms-picker-table"><thead><tr>';
        meta.cols.forEach(function(c){ h += '<th>' + c.l + '</th>'; });
        h += '</tr></thead><tbody>';
        list.forEach(function(row, i){
          h += '<tr data-idx="' + i + '">';
          meta.cols.forEach(function(c){
            var v = row[c.k];
            if (c.k === 'status') h += '<td>' + statusBadge(v) + '</td>';
            else h += '<td>' + fmt(v, c.k) + '</td>';
          });
          h += '</tr>';
        });
        h += '</tbody></table>';
        listBox.innerHTML = h;
        listBox.querySelectorAll('tbody tr').forEach(function(tr){
          tr.onclick = function(){
            var idx = Number(tr.getAttribute('data-idx'));
            var row = list[idx];
            onSelect && onSelect({ value: row.value || row.id, label: row.label || (row.code + ' · ' + row.name), row: row });
            m.close();
          };
        });
      });
    }
    kwInput.oninput = function(){
      if (kwInput._t) clearTimeout(kwInput._t);
      kwInput._t = setTimeout(load, 350);
    };
    m.box.querySelector('.dms-picker-btn').onclick = load;
    kwInput.focus();
    load();
  }

  // ============ 确认框 ============
  function confirmDlg(msg, onOk) {
    var m = baseModal('确认',
      '<div style="padding:20px 4px;font-size:14px;">' + msg + '</div>',
      '<button type="button" class="dms-btn dms-btn-cancel">取消</button>' +
      '<button type="button" class="dms-btn dms-btn-primary dms-confirm-ok">确定</button>',
      { width:'400px' });
    m.box.querySelector('.dms-btn-cancel').onclick = m.close;
    m.box.querySelector('.dms-confirm-ok').onclick = function(){ m.close(); if(onOk) onOk(); };
  }

  // ============ 详情预览 ============
  function detail(title, data) {
    var rows = '';
    Object.keys(data || {}).forEach(function(k){
      var v = data[k];
      var display;
      if (v == null) display = '-';
      else if (typeof v === 'object') display = '<code>' + JSON.stringify(v) + '</code>';
      else if (k === 'status') display = statusBadge(v);
      else display = fmt(v, k);
      rows += '<tr><td style="width:180px;color:#999;">' + labelOf(k) + '</td><td>' + display + '</td></tr>';
    });
    baseModal(title,
      '<table class="dms-detail-table">' + rows + '</table>',
      '<button type="button" class="dms-btn dms-btn-cancel dms-detail-close">关闭</button>',
      { width:'720px' })
      .box.querySelector('.dms-detail-close').onclick = function(){
        this.closest('.dms-modal-mask').classList.remove('show');
        setTimeout((function(m){return function(){m.remove();};})(this.closest('.dms-modal-mask')), 200);
      };
  }

  return {
    TOKEN: TOKEN,
    USER: USER,
    LABELS: LABELS,
    ENUMS: ENUMS,
    labelOf: labelOf,
    requireAuth: requireAuth,
    logout: logout,
    api: apiCall,
    toast: toast,
    modal: form,   // 保留旧接口名
    form: form,
    confirm: confirmDlg,
    detail: detail,
    picker: openPicker,
    statusBadge: statusBadge,
    fmt: fmt
  };
})();
