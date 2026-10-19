"""按用户确认的职责迁移应用层类型，并同步所有源码引用。"""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
backend = root / 'backend'
application = backend / 'application/src/main/java'
original = application / 'com/dianping/shopdetail'
mapping = {}
moves = {}
for source in sorted(original.rglob('*.java')):
    content = source.read_text(encoding='utf-8')
    package = re.search(r'^package ([\w.]+);', content, re.M)[1]
    name = source.stem
    if name == 'ShopPageResult':
        new_package = 'com.dianping.shop.shared.result'
    elif name == 'ShopDetailPageRequest':
        new_package = 'com.dianping.shop.shared.request'
    else:
        business = ('recommend' if name.startswith(('RecommendDish', 'PublishRecommendDish'))
                    else 'review' if name.startswith(('ShopReview', 'PublishShopReview'))
                    else 'setmeal' if name.startswith('ShopSetmeal') else 'shopdetail')
        suffix = package.removeprefix('com.dianping.shopdetail')
        new_package = 'com.dianping.shop.' + business + suffix
    new_name = 'ShopListPageRequest' if name == 'ShopDetailPageRequest' else name
    old_fqn, new_fqn = package+'.'+name, new_package+'.'+new_name
    mapping[old_fqn] = new_fqn
    moves[source] = application / Path(new_package.replace('.', '/')) / (new_name+'.java')

for source in (backend/'application/src/test/java/com/dianping/shopdetail').glob('*.java'):
    moves[source] = backend/'application/src/test/java/com/dianping/shop/shopdetail'/source.name

for source, target in moves.items():
    assert source.resolve().is_relative_to(backend.resolve())
    assert target.resolve().is_relative_to(backend.resolve())
    assert not target.exists(), target

sources = [p for p in backend.rglob('*.java') if '/src/' in p.as_posix() and '/target/' not in p.as_posix()]
backup = {str(p.relative_to(root)):p.read_bytes().decode('utf-8') for p in sources}
(root/'.scratch/repackage-before.json').write_text(json.dumps(backup,ensure_ascii=False),encoding='utf-8')
(root/'.scratch/repackage-map.json').write_text(json.dumps(mapping,ensure_ascii=False,indent=2),encoding='utf-8')
affected = []
old_packages = {fqn.rsplit('.',1)[0] for fqn in mapping}
for source in sources:
    old = backup[str(source.relative_to(root))]
    content = old.replace('\r\n','\n')
    if source in moves:
        if '/src/main/' in source.as_posix():
            new_package = mapping[re.search(r'^package ([\w.]+);',content,re.M)[1]+'.'+source.stem].rsplit('.',1)[0]
        else:
            new_package = 'com.dianping.shop.shopdetail'
        content = re.sub(r'^package [\w.]+;', 'package '+new_package+';', content, count=1,flags=re.M)
    # 先展开即将拆散的旧通配导入，原领域模型与仓储的通配导入不受影响。
    for package in old_packages:
        wildcard = 'import '+package+'.*;'
        if wildcard in content:
            content = content.replace(wildcard, '')
    for old_fqn,new_fqn in sorted(mapping.items(), key=lambda item:-len(item[0])):
        content = re.sub(re.escape(old_fqn)+r'\b',new_fqn,content)
    content = re.sub(r'\bShopDetailPageRequest\b','ShopListPageRequest',content)
    package = re.search(r'^package ([\w.]+);',content,re.M)[1]
    additions = []
    body = re.sub(r'^import .*?;\s*','',content,flags=re.M)
    for fqn in mapping.values():
        declaring_package,name = fqn.rsplit('.',1)
        if declaring_package == package or not re.search(r'\b'+name+r'\b',body): continue
        if 'import '+fqn+';' in content: continue
        if 'import '+declaring_package+'.*;' in content: continue
        # 全限定引用不强行再添加无用导入。
        if not re.search(r'(?<![\w.])'+name+r'\b',body): continue
        additions.append(fqn)
    if 'ShopDetailDomainService' in body and package == 'com.dianping.shop.shopdetail.service':
        if 'import com.dianping.shopdetail.service.ShopDetailDomainService;' not in content:
            additions.append('com.dianping.shopdetail.service.ShopDetailDomainService')
    # service 的旧通配导入也可能提供未迁移的领域服务。
    if 'import com.dianping.shopdetail.service.*;' in old and 'ShopDetailDomainService' in body:
        if 'import com.dianping.shopdetail.service.ShopDetailDomainService;' not in content:
            additions.append('com.dianping.shopdetail.service.ShopDetailDomainService')
    if additions:
        content = re.sub(r'^(package [\w.]+;)', lambda m:m[1]+'\n\n'+'\n'.join('import '+fqn+';' for fqn in sorted(set(additions))),content,count=1,flags=re.M)
    if source.name == 'RecommendDishIntegrationTest.java':
        content = content.replace('shopdetail[.].*RecommendDish(FacadeImpl|ApplicationService|RepositoryImpl)',
                                  '(shopdetail[.].*RecommendDish(FacadeImpl|RepositoryImpl)|shop[.]recommend[.]service[.]RecommendDishApplicationService)')
    if source.name == 'ShopSetmealIntegrationTest.java':
        content = content.replace(r'shopdetail\\..*ShopSetmeal(FacadeImpl|ApplicationService|RepositoryImpl)',
                                  r'(shopdetail\\..*ShopSetmeal(FacadeImpl|RepositoryImpl)|shop\\.setmeal\\.service\\.ShopSetmealApplicationService)')
    if source in moves:
        target = moves[source]
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)
    else:
        target = source
    if content != old.replace('\r\n','\n') or source in moves:
        target.write_bytes(content.replace('\n','\r\n').encode('utf-8'))
        affected.append(str(target.relative_to(root)))

print('迁移文件：',len(moves),'；同步源码引用：',len(affected))
print('\n'.join(affected))
