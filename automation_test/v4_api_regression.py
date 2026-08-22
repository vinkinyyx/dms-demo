import json, urllib.request, urllib.error
from datetime import datetime
import random, string
BASE='http://43.128.145.141/api'

def request(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json')
    if token: req.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            raw=r.read().decode('utf-8')
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw=e.read().decode('utf-8', errors='replace')
        try: payload=json.loads(raw)
        except Exception: payload={'raw':raw}
        return e.code, payload

def must(st_res, cond, msg):
    if not cond: raise AssertionError(msg + ': ' + json.dumps(st_res[1], ensure_ascii=False)[:1200])

def data_list(res):
    must(res, res[0]==200 and isinstance(res[1].get('data'),dict), 'list failed')
    return res[1]['data'].get('list',[])

def main():
    _, login=request('POST','/auth/login',body={'tenantCode':'default','username':'sys_admin','password':'Dms@123456'})
    token=login['data']['accessToken']
    results=[]
    def check(name, fn):
        try:
            out=fn(); results.append({'name':name,'ok':True,'data':out}); print('PASS',name)
        except Exception as e:
            import traceback; traceback.print_exc()
            results.append({'name':name,'ok':False,'error':str(e)}); print('FAIL',name,str(e))
    def static_checks():
        products=data_list(request('GET','/products?page=1&size=1',token))
        today=datetime.now().strftime('%Y-%m-%d')
        dealer=(data_list(request('GET','/dealers?page=1&size=1',token)))[0]
        os_all=data_list(request('GET','/sales-orders?page=1&size=1',token))
        os_status=data_list(request('GET','/sales-orders?page=1&size=5&status=DRAFT',token))
        os_dealer=data_list(request('GET',f"/sales-orders?page=1&size=5&dealerId={dealer['id']}",token))
        os_date=data_list(request('GET',f'/sales-orders?page=1&size=5&createdFrom={today}&createdTo={today}',token))
        must((_,{}), products and os_all, 'base list empty')
        return {'orders':len(os_all),'draft':len(os_status),'dealer':len(os_dealer),'date':len(os_date)}
    check('列表筛选接口有数据', static_checks)

    def immutable_rules():
        price=(data_list(request('GET','/product-prices?page=1&size=50',token)))[0]
        st_put,_=request('PUT',f"/product-prices/{price['id']}",token,{'salesPrice':0.01})
        st_del,_=request('DELETE',f"/product-prices/{price['id']}",token)
        must((st_put,_), st_put>=400, 'price should be immutable')
        must((st_del,_), st_del>=400, 'price deletable')
        st_act,_=request('POST',f"/product-prices/{price['id']}/activate",token)
        must((st_act,_), st_act==200, 'activate failed')
        promo=(data_list(request('GET','/promotions?page=1&size=1',token)))[0]
        st_pdel,_=request('DELETE',f"/promotions/{promo['id']}",token)
        must((st_pdel,_), st_pdel>=400, 'promo deletable')
        return {'priceId':price['id'],'promoId':promo['id']}
    check('价格不可编辑删除/促销不可删除', immutable_rules)

    def bom_order_return_flow():
        suffix=datetime.now().strftime('%Y%m%d%H%M%S')+''.join(random.choices(string.ascii_uppercase,k=4))
        dealer=(data_list(request('GET','/dealers?page=1&size=1',token)))[0]
        category=(data_list(request('GET','/product-categories?page=1&size=1',token)))[0]
        def create_product(kind):
            body={'code':f'V4REG{suffix}{kind}','nameCn':f'V4回归{kind}{suffix}','nameEn':f'V4REG{suffix}{kind}','categoryId':category['id'],'spec':'REG','unit':'个','status':'active'}
            st,res=request('POST','/products',token,body); must((st,res), st==200 and res.get('data',{}).get('id'), 'create product failed')
            return res['data']['id']
        parent=create_product('P'); c1=create_product('A'); c2=create_product('B')
        bundle_body={'productId':parent,'code':f'BV4{suffix}','name':f'V4回归BOM{suffix}','description':'api regression','pricingType':'COMPONENT','allowSplit':False,'bomVersion':'1','versionStatus':'active','versionLocked':True,'validFrom':'2026-01-01T00:00:00+08:00','validTo':'2026-12-31T23:59:59+08:00','status':'active','lines':[{'childProductId':c1,'lineType':'FIXED','quantity':1,'isRequired':True,'sortOrder':1},{'childProductId':c2,'lineType':'FIXED','quantity':2,'isRequired':True,'sortOrder':2}]}
        st_bundle,bundle_res=request('POST','/product-bundles',token,bundle_body); must((st_bundle,bundle_res), st_bundle==200 and bundle_res.get('data',{}).get('id'), 'create BOM failed')
        st_price,price_res=request('POST','/product-prices',token,{'productId':parent,'partnerType':'GLOBAL','salesPrice':999,'taxRate':0.13})
        must((st_price,price_res), st_price>=400, 'BOM parent price should be rejected')
        for pid,price in [(c1,100.00),(c2,50.00)]:
            st_p,p_res=request('POST','/product-prices',token,{'productId':pid,'partnerType':'DEALER','partnerId':dealer['id'],'salesPrice':price,'taxRate':0.13,'currency':'CNY','validFrom':'2026-01-01 00:00:00','validTo':'2026-12-31 23:59:59','status':'active'})
            must((st_p,p_res), st_p==200 and p_res.get('data',{}).get('id'), 'create dealer price failed')
        bid=bundle_res['data']['id']
        st_bad,_=request('PUT',f'/product-bundles/{bid}',token,{'code':'CHANGED','productId':999999,'bomVersion':'999'}); must((st_bad,_), st_bad>=400, 'BOM header editable')
        st_nv,res_nv=request('POST',f'/product-bundles/{bid}/new-version',token); must((st_nv,res_nv), st_nv==200, 'new version failed')
        new_id=res_nv['data']['id']; must((st_nv,res_nv), str(res_nv['data'].get('bomVersion'))!='1', 'new version not generated')
        # 新建版本期间：老版本保持active以保证在售，新版本为draft可改子件、不能改头
        st_old,res_old=request('GET',f'/product-bundles/{bid}',token); must((st_old,res_old), res_old['data']['versionStatus']=='active', 'old version should stay active while draft exists')
        st_newdraft,_=request('GET',f'/product-bundles/{new_id}',token); must((st_newdraft,_), _['data']['versionStatus']=='draft', 'new version should be draft')
        st_upd,res_upd=request('PUT',f'/product-bundles/{new_id}',token,{'description':'child changed','lines':[{'childProductId':c1,'lineType':'FIXED','quantity':1,'isRequired':True,'sortOrder':1},{'childProductId':c2,'lineType':'FIXED','quantity':3,'isRequired':True,'sortOrder':2}]})
        must((st_upd,res_upd), st_upd==200, 'new version child update failed')
        # 草稿发布后：新版本active，老版本转history
        st_act,_=request('POST',f'/product-bundles/{new_id}/activate',token); must((st_act,_), st_act==200, 'activate draft failed')
        st_old2,res_old2=request('GET',f'/product-bundles/{bid}',token); must((st_old2,res_old2), res_old2['data']['versionStatus']=='history', 'old version not history after activate')
        order_body={'dealerId':dealer['id'],'orderType':'NORMAL','expectedDate':'2026-08-20','headerDiscountType':'AMOUNT','headerDiscountValue':100,'remark':'v4 api regression\n多行备注','lines':[{'productId':parent,'qty':2,'lineDiscountType':'AMOUNT','lineDiscountValue':100}]}
        st,res=request('POST','/sales-orders',token,order_body); must((st,res), st==200 and res.get('data',{}).get('id'), 'create order failed')
        oid=res['data']['id']
        st,detail=request('GET',f'/sales-orders/{oid}',token); must((st,detail), st==200, 'order detail failed')
        order_lines=detail['data'].get('lines') or []
        # v4: BOM母件不单独落行，子件行通过 bomParentProductId 关联
        children=[x for x in order_lines if x.get('bomParentProductId')]
        must((_,{}), len(children)==2, 'BOM child lines not expected: '+str(len(children)))
        std=sum(float(x.get('standardAmount') or x.get('subtotal') or 0) for x in children)
        final=sum(float(x.get('finalAmount') or 0) for x in children)
        line_disc=sum(float(x.get('lineDiscountAmount') or 0) for x in children)
        header_alloc=sum(float(x.get('headerDiscountAmount') or 0) for x in children)
        must((_,{}), abs(std-500.0)<0.02, f'standard wrong {std}')
        must((_,{}), abs(line_disc-100.0)<0.02, f'line discount wrong {line_disc}')
        must((_,{}), abs(header_alloc-100.0)<0.02, f'header discount wrong {header_alloc}')
        must((_,{}), abs(final-300.0)<0.05, f'final wrong {final}')
        st,res=request('PUT',f'/sales-orders/{oid}',token,order_body); must((st,res), st==200, 'second save failed')
        st,res=request('POST',f'/sales-orders/{oid}/submit',token); must((st,res), st==200, 'submit failed')
        st,after=request('GET',f'/sales-orders/{oid}',token); must((st,after), after['data']['status'] in ('PENDING_APPROVAL','SUBMITTED','APPROVED'), 'submit status wrong')
        st,res=request('POST',f'/sales-orders/{oid}/simulate-ship',token)
        # 若审批未自动通过，则模拟审批通过后再出库
        if st != 200:
            request('POST',f'/sales-orders/{oid}/approve',token)
            st,res=request('POST',f'/sales-orders/{oid}/simulate-ship',token)
        must((st,res), st==200, 'simulate ship failed')
        out_id=res['data'].get('salesOutId') or res['data'].get('id')
        must((st,res), out_id, 'missing sales out id')
        st,outs=request('GET','/sales-returns/shipped-outs?orderId='+str(oid),token); must((st,outs), st==200 and outs.get('data'), 'return candidates failed')
        st,ol=request('GET',f'/sales-returns/shipped-outs/{out_id}/lines',token); must((st,ol), st==200 and ol.get('data',{}).get('lines'), 'out lines failed')
        pick=None
        for x in ol['data']['lines']:
            qty=float(x.get('shippedQty') or x.get('qty') or 0); avail=float(x.get('availableQty') or x.get('returnableQty') or 0)
            if qty==int(qty) and avail>=1 and float(x.get('unitPrice') or 0)>0: pick=x; break
        must((_,{}), pick, 'no integer returnable line')
        ret_body={'dealerId':dealer['id'],'warehouseId':pick.get('warehouseId') or 1,'refSalesOutId':out_id,'refOrderId':oid,'returnReason':'v4 api regression','expectedDate':'2026-08-20','lines':[{'id':pick.get('id'),'sourceOutLineId':pick.get('id'),'productId':pick.get('productId'),'productCode':pick.get('productCode'),'productName':pick.get('productName'),'qty':1,'unitPrice':pick.get('unitPrice'),'taxRate':pick.get('taxRate'),'batchNo':pick.get('batchNo'),'serialNo':pick.get('serialNo')}]}
        st,rret=request('POST','/sales-returns',token,ret_body); must((st,rret), st==200 and rret.get('data',{}).get('id'), 'create return failed')
        rid=rret['data']['id']
        st,rdetail=request('GET',f'/sales-returns/{rid}',token); must((st,rdetail), st==200 and abs(float(rdetail['data']['lines'][0]['unitPrice'])-float(pick['unitPrice']))<0.01, 'return price not inherited')
        st,rsub=request('POST',f'/sales-returns/{rid}/submit',token); must((st,rsub), st==200, 'return submit failed')
        return {'bundleId':bid,'newVersionId':new_id,'orderId':oid,'salesOutId':out_id,'returnId':rid,'standard':std,'final':final}
    check('BOM组件价/新版本/订单折扣平摊/出库/销退全链路', bom_order_return_flow)

    report={'time':datetime.now().isoformat(),'results':results}
    path='automation_test/v4-api-results/regression-report.json'
    open(path,'w',encoding='utf-8').write(json.dumps(report,ensure_ascii=False,indent=2))
    if any(not x['ok'] for x in results): raise SystemExit(1)

if __name__=='__main__': main()







