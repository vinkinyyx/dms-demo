/* DMS 移动端共享 JS */
var MDMS = (function(){
  var TOKEN = localStorage.getItem('dms_access_token');
  var USER = JSON.parse(localStorage.getItem('dms_user') || '{}');

  function requireAuth() {
    if (!TOKEN) { window.location.href = '/mobile/login.html'; return false; }
    return true;
  }
  function api(path, options) {
    options = options || {};
    options.headers = options.headers || {};
    options.headers['Authorization'] = 'Bearer ' + TOKEN;
    if (options.body && typeof options.body !== 'string') {
      options.body = JSON.stringify(options.body);
      options.headers['Content-Type'] = 'application/json';
    }
    return fetch(path, options).then(function(r){
      if (r.status === 401 || r.status === 403) {
        localStorage.clear();
        window.location.href = '/mobile/login.html';
        throw new Error('Unauthorized');
      }
      return r.json();
    });
  }
  function toast(msg, type) {
    type = type || 'info';
    var bg = { info:'#2C4B8E', success:'#52C41A', error:'#F5222D', warn:'#FA8C16' }[type];
    var el = document.createElement('div');
    el.style.cssText = 'position:fixed;top:60px;left:50%;transform:translateX(-50%);padding:10px 20px;border-radius:6px;color:#fff;font-size:13px;z-index:9999;background:'+bg+';box-shadow:0 4px 12px rgba(0,0,0,.2);opacity:0;transition:.3s;';
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(function(){ el.style.opacity='1'; }, 10);
    setTimeout(function(){ el.style.opacity='0'; setTimeout(function(){el.remove();},300); }, 2500);
  }
  function statusBadge(s) {
    if (!s) return '<span class="m-badge-gray">-</span>';
    var u = String(s).toUpperCase();
    var cls = 'm-badge-gray', text = String(s);
    var map = {
      'APPROVED':'已审批','ACTIVE':'启用','COMPLETED':'已完成',
      'SUBMITTED':'已提交','PENDING':'待处理','DRAFT':'草稿',
      'REJECTED':'已驳回','CANCELLED':'已取消','LOCKED':'锁定',
      'INACTIVE':'停用'
    };
    if (map[u]) text = map[u];
    if (u==='APPROVED'||u==='ACTIVE'||u==='COMPLETED') cls='m-badge-green';
    else if (u==='SUBMITTED'||u==='PENDING'||u==='DRAFT') cls='m-badge-orange';
    else if (u==='REJECTED'||u==='CANCELLED'||u==='FAILED'||u==='LOCKED') cls='m-badge-red';
    else if (u==='INACTIVE') cls='m-badge-gray';
    else cls='m-badge-blue';
    return '<span class="'+cls+'">'+text+'</span>';
  }
  return { TOKEN:TOKEN, USER:USER, requireAuth:requireAuth, api:api, toast:toast, statusBadge:statusBadge };
})();
