/* DMS PC 原型 · 共享脚本：SVG 图标库 + 侧边菜单 + 顶栏 */
(function () {
  var I = {
    goods:'M3 7l9-4 9 4-9 4-9-4zM3 7v10l9 4 9-4V7M12 11v10',
    files:'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5',
    conn:'M8 7h8M8 17h8M5 4.6A2.4 2.4 0 1 0 5 9.4a2.4 2.4 0 0 0 0-4.8zM19 14.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8zM19 4.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8zM5 14.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8z',
    office:'M3 21h18M5 21V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v16M14 21V9h4a1 1 0 0 1 1 1v11M8 7h2M8 11h2M8 15h2',
    user:'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-4 4-6 8-6s8 2 8 6',
    firstaid:'M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6zM12 8v6M9 11h6',
    house:'M3 11l9-7 9 7M5 10v10h14V10M10 20v-6h4v6',
    location:'M12 21s7-6.5 7-12a7 7 0 1 0-14 0c0 5.5 7 12 7 12zM12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
    shop:'M4 9l1-4h14l1 4M4 9h16v3a2 2 0 0 1-4 0 2 2 0 0 1-4 0 2 2 0 0 1-4 0 2 2 0 0 1-4 0V9zM5 14v6h14v-6',
    money:'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM9 9h5a1.5 1.5 0 0 1 0 3H9.5a1.5 1.5 0 0 0 0 3H15M12 6.5v11',
    doc:'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h6',
    key:'M8 19a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM11 12l9-9M16 7l3 3M14 9l2 2',
    sell:'M3 7h18l-2 12H5zM9 21a1.5 1.5 0 1 0 .1 0M17 21a1.5 1.5 0 1 0 .1 0M8 11h8',
    refreshL:'M4 10a8 8 0 0 1 13-5l3 3M20 14a8 8 0 0 1-13 5l-3-3M20 4v4h-4M4 20v-4h4',
    cart:'M9 20a1.6 1.6 0 1 0 .1 0M18 20a1.6 1.6 0 1 0 .1 0M2 3h3l2.5 12h12L21 7H6',
    box:'M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5',
    van:'M2 7h11v9H2zM13 10h5l3 3v3h-8zM7 18a1.8 1.8 0 1 0 .1 0M17 18a1.8 1.8 0 1 0 .1 0',
    takeaway:'M3 8l9-5 9 5-9 5zM3 8v8l9 5 9-5V8M12 13v8',
    switch:'M8 7h12M16 17H4M11 4L8 7l3 3M13 20l3-3-3-3M19 4l-3 3 3 3M5 20l3-3-3-3',
    alarm:'M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM12 9v4l3 2M5 3L3 5M19 3l2 2',
    hist:'M4 20V10M10 20V4M16 20v-8M22 20H2',
    search:'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-4-4',
    present:'M3 8h18v13H3zM3 12h18M12 8v13M12 8s-4 0-4-3 4-2 4 3zM12 8s4 0 4-3-4-2-4 3z'

    ,discount:'M20 12L12 20l-8-8V4h8zM8.6 8.6a1.4 1.4 0 1 0 0-2.8 1.4 1.4 0 0 0 0 2.8zM6 14l8-8'
    ,medal:'M12 14a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM9 13.5L8 21l4-2 4 2-1-7.5'
    ,ticket:'M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2 2 2 0 0 0 0 4 2 2 0 0 1-2 2H5a2 2 0 0 1-2-2 2 2 0 0 0 0-4zM14 6v12'
    ,dataline:'M4 20V4M4 20h16M8 16l4-5 3 3 4-6'
    ,dataana:'M4 20V4M4 20h16M7 17v-5h3v5zM12 17V8h3v9zM17 17V5h3v12z'
    ,trophy:'M7 4h10v5a5 5 0 0 1-10 0zM7 5H4v2a3 3 0 0 0 3 3M17 5h3v2a3 3 0 0 1-3 3M9 16h6M12 14v2M9 20h6l.5-2h-7z'
    ,bell:'M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6zM10 20a2 2 0 0 0 4 0'
    ,coin:'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v10M9.5 9.5c0-1.2 1.1-2 2.5-2s2.5.8 2.5 2-1 1.8-2.5 2-2.5.8-2.5 2 1.1 2 2.5 2 2.5-.8 2.5-2'
    ,trend:'M3 17l6-6 4 4 8-8M15 7h6v6'
    ,setup:'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2'
    ,monitor:'M3 4h18v13H3zM8 21h8M12 17v4'
    ,avatar:'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM5 21c0-3.5 3-5.5 7-5.5s7 2 7 5.5'
    ,setting:'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.5-2-3.4-2.3 1a7.6 7.6 0 0 0-2.6-1.5L14 2h-4l-.5 2.6a7.6 7.6 0 0 0-2.6 1.5l-2.3-1-2 3.4 2 1.5a7.6 7.6 0 0 0 0 3l-2 1.5 2 3.4 2.3-1a7.6 7.6 0 0 0 2.6 1.5L10 22h4l.5-2.6a7.6 7.6 0 0 0 2.6-1.5l2.3 1 2-3.4z'
    ,notebook:'M5 4h14v16H5zM9 4v16M8 9h2M8 13h2M13 9h4M13 13h4'
    ,plus:'M12 5v14M5 12h14'
    ,download:'M12 3v12M7 10l5 5 5-5M5 21h14'
    ,upload:'M12 21V9M7 14l5-5 5 5M5 3h14'
    ,eye:'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'
    ,edit:'M4 20h4l11-11-4-4L4 16zM13.5 6.5l4 4'
    ,trash:'M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14'
    ,more:'M5 13.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2zM12 13.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2zM19 13.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z'
    ,chevronR:'M9 6l6 6-6 6'
    ,chevronD:'M6 9l6 6 6-6'
    ,check:'M4 12l5 5L20 6'
    ,close:'M6 6l12 12M18 6L6 18'
    ,info:'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 11v5M12 7.5v.5'
    ,warn:'M12 3l10 18H2zM12 10v4M12 17.5v.5'
    ,clock:'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2'
    ,arrowR:'M4 12h16M13 6l6 6-6 6'
    ,arrowUp:'M12 19V5M6 11l6-6 6 6'
    ,arrowDn:'M12 5v14M6 13l6 6 6-6'
    ,filter:'M3 5h18l-7 8v6l-4 2v-8z'
    ,refresh:'M20 11a8 8 0 1 0-2 6M20 5v6h-6'
    ,printer:'M6 9V3h12v6M6 18H4v-7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v7h-2M6 14h12v7H6z'
    ,save:'M5 3h12l3 3v15H5zM8 3v6h7V3M8 21v-8h8v8'
    ,wallet:'M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM16 12h5'
    ,pie:'M12 3a9 9 0 1 0 9 9h-9zM12 3v9h9A9 9 0 0 0 12 3z'
  };
  window.icon = function (name, cls) {
    return '<svg class="ic ' + (cls || '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="' + (I[name] || I.doc) + '"/></svg>';
  };

  var MENU = [
    { g: '工作台', items: [
      { k: 'home', icon: 'monitor', t: '业务工作台', href: 'home.html' },
      { k: 'dashboard', icon: 'dataline', t: '数据驾驶舱', href: 'dashboard.html' }
    ]},
    { g: '基础数据', items: [
      { k: 'products', icon: 'goods', t: '产品管理' },
      { k: 'dealers', icon: 'office', t: '经销商管理' },
      { k: 'hospitals', icon: 'firstaid', t: '医院/终端' },
      { k: 'warehouses', icon: 'house', t: '仓库管理' },
      { k: 'prices', icon: 'money', t: '产品价格' }
    ]},
    { g: '订单业务', items: [
      { k: 'orders', icon: 'sell', t: '销售订单', href: 'orders.html', badge: 12 },
      { k: 'returns', icon: 'refreshL', t: '销退订单' },
      { k: 'purchase', icon: 'cart', t: '采购订单' },
      { k: 'outs', icon: 'van', t: '销售出库' }
    ]},
    { g: '库存业务', items: [
      { k: 'inventory', icon: 'box', t: '库存查询' },
      { k: 'receipts', icon: 'takeaway', t: '收货入库' },
      { k: 'expiry', icon: 'alarm', t: '效期预警', badge: 3 },
      { k: 'stocktake', icon: 'hist', t: '库存盘点' }
    ]},
    { g: '手术与营销', items: [
      { k: 'surgery', icon: 'firstaid', t: '手术植入报台' },
      { k: 'promo', icon: 'present', t: '促销规则' },
      { k: 'discount', icon: 'discount', t: '全局折扣' },
      { k: 'voucher', icon: 'ticket', t: '代金券管理' }
    ]},
    { g: '审批中心', items: [
      { k: 'todo', icon: 'bell', t: '我的审批', href: 'home.html', badge: 5 },
      { k: 'templates', icon: 'setup', t: '审批流配置' },
      { k: 'admin', icon: 'monitor', t: '审批监控' }
    ]},
    { g: '报表与系统', items: [
      { k: 'reports', icon: 'dataana', t: '报表中心' },
      { k: 'users', icon: 'user', t: '账号管理' },
      { k: 'roles', icon: 'avatar', t: '角色权限' },
      { k: 'logs', icon: 'notebook', t: '日志中心' }
    ]}
  ];

  function renderSider(active) {
    var h = '<div class="brand"><div class="brand-logo"><img src="assets/logo-mark.png" alt="MySolMed"></div>' +
      '<div><div class="brand-name">MySolMed DMS</div><div class="brand-sub">经销商管理系统</div></div></div>' +
      '<div class="menu">';
    MENU.forEach(function (grp) {
      h += '<div class="menu-group"><div class="menu-group-title">' + grp.g + '</div>';
      grp.items.forEach(function (it) {
        var cls = it.k === active ? 'menu-item active' : 'menu-item';
        var href = it.href ? ' href="' + it.href + '"' : '';
        var tag = it.href ? 'a' : 'div';
        h += '<' + tag + ' class="' + cls + '"' + (it.href ? href : '') + ' style="' + (it.href ? 'text-decoration:none' : '') + '">' +
          window.icon(it.icon) + '<span>' + it.t + '</span>' +
          (it.badge ? '<span class="menu-badge">' + it.badge + '</span>' : '') +
          '</' + tag + '>';
      });
      h += '</div>';
    });
    return h + '</div>';
  }

  function renderTopbar(crumb) {
    return '<div class="crumb">' + crumb + '</div>' +
      '<div class="topbar-right">' +
        '<div class="tenant-tag">' + window.icon('office') + '默认租户 · 华东大区</div>' +
        '<div class="top-icon">' + window.icon('search') + '</div>' +
        '<div class="top-icon">' + window.icon('bell') + '<span class="dot"></span></div>' +
        '<div class="top-icon">' + window.icon('setting') + '</div>' +
        '<div class="user-chip"><div class="avatar">管</div>' +
        '<div><div class="uname">系统管理员</div><div class="urole">sys_admin</div></div></div>' +
      '</div>';
  }

  var TABS = {
    home:{t:'业务工作台',icon:'monitor',href:'home.html'},
    dashboard:{t:'数据驾驶舱',icon:'dataline',href:'dashboard.html'},
    orders:{t:'销售订单',icon:'sell',href:'orders.html'},
    'order-detail':{t:'订单详情',icon:'doc',href:'order-detail.html'}
  };
  function tabIdFromFile(){ var f=(location.pathname.split('/').pop()||'home.html'); if(f==='')f='home.html'; return f.replace('.html',''); }
  function getOpenTabs(){ var ids=null; try{ ids=JSON.parse(localStorage.getItem('dms_proto_tabs')||'null'); }catch(e){ ids=null; } if(!Array.isArray(ids)) ids=['home']; if(ids.indexOf('home')<0) ids.unshift('home'); return ids.filter(function(id){return !!TABS[id];}); }
  function saveOpenTabs(ids){ try{ localStorage.setItem('dms_proto_tabs', JSON.stringify(ids)); }catch(e){} }
  function renderTags(activeId){
    var ids=getOpenTabs(); if(ids.indexOf(activeId)<0){ ids.push(activeId); saveOpenTabs(ids); }
    var h='<div class="tags-menu" title="折叠/展开菜单">'+window.icon('switch')+'</div>';
    ids.forEach(function(id){ var t=TABS[id]; var on=(id===activeId);
      h+='<a class="tag-item'+(on?' active':'')+'" href="'+t.href+'">'+(on?'':'<span class="tag-dot"></span>')+window.icon(t.icon)+'<span>'+t.t+'</span>'+(id==='home'?'':'<span class="tag-close" data-close="'+id+'" title="关闭页签">'+window.icon('close')+'</span>')+'</a>';
    });
    h+='<div class="tags-actions"><div class="tags-icon-btn" title="刷新当前页" data-refresh="1">'+window.icon('refresh')+'</div><div class="tags-icon-btn" title="关闭其他页签" data-closeothers="1">'+window.icon('close')+'</div></div>';
    return h;
  }
  function bindTagBar(activeId){
    var bar=document.getElementById('tagsBar'); if(!bar) return;
    bar.addEventListener('click',function(ev){
      var c=ev.target.closest('[data-close],[data-closeothers],[data-refresh]'); if(!c) return;
      ev.preventDefault();
      if(c.getAttribute('data-refresh')){ location.reload(); return; }
      var ids=getOpenTabs();
      if(c.getAttribute('data-closeothers')){ ids=ids.filter(function(id){return id==='home'||id===activeId;}); saveOpenTabs(ids); bar.innerHTML=renderTags(activeId); return; }
      var id=c.getAttribute('data-close'); ids=ids.filter(function(x){return x!==id;}); saveOpenTabs(ids);
      if(id===activeId){ location.href=TABS[ids[ids.length-1]].href; } else { bar.innerHTML=renderTags(activeId); }
    });
  }
  window.shell = function (opt) {
    var root = document.getElementById('app');
    root.className = 'app';
    root.innerHTML =
      '<aside class="sider">' + renderSider(opt.active) + '</aside>' +
      '<div class="main">' +
        '<header class="topbar">' + renderTopbar(opt.crumb) + '</header>' +
        '<nav class="tags-bar" id="tagsBar"></nav>' +
        '<main class="content" id="content"></main>' +
      '</div>';
    var tabId = tabIdFromFile();
    document.getElementById('tagsBar').innerHTML = renderTags(tabId);
    bindTagBar(tabId);
    document.getElementById('content').innerHTML = opt.body;
    if (opt.after) opt.after();
  };
})();
