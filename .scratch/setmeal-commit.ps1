$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath 'D:\devprojects\java-projects\comment'
if ((git branch --show-current) -ne 'feat/setmeal') { throw '当前分支不是 feat/setmeal，停止提交' }
$setmealPaths = @(
    '.gitignore'
    'backend/application/src/main/java/com/dianping/shopdetail/model/result/ShopSetmealResult.java'
    'backend/application/src/main/java/com/dianping/shopdetail/service/ShopSetmealApplicationService.java'
    'backend/controller/src/main/java/com/dianping/controller/ShopSetmealController.java'
    'backend/controller/src/main/java/com/dianping/exception/GlobalExceptionHandler.java'
    'backend/domain/src/main/java/com/dianping/shopdetail/exception/ShopSetmealException.java'
    'backend/domain/src/main/java/com/dianping/shopdetail/model/ShopSetmeal.java'
    'backend/domain/src/main/java/com/dianping/shopdetail/model/ShopSetmealItem.java'
    'backend/domain/src/main/java/com/dianping/shopdetail/repository/ShopSetmealRepository.java'
    'backend/facade/src/main/java/com/dianping/shopdetail/impl/ShopSetmealFacadeImpl.java'
    'backend/facade/src/main/java/com/dianping/shopdetail/ShopSetmealFacade.java'
    'backend/infrastructure/src/main/java/com/dianping/shopdetail/persistence/dataobject/ShopSetmeal.java'
    'backend/infrastructure/src/main/java/com/dianping/shopdetail/persistence/dataobject/ShopSetmealItem.java'
    'backend/infrastructure/src/main/java/com/dianping/shopdetail/persistence/mapper/ShopSetmealMapper.java'
    'backend/infrastructure/src/main/java/com/dianping/shopdetail/repository/ShopSetmealRepositoryImpl.java'
    'backend/infrastructure/src/main/resources/db/demo/20261018_shop_setmeal_demo.sql'
    'backend/infrastructure/src/main/resources/db/migrations/20261018_shop_setmeal.sql'
    'backend/start/src/test/java/com/dianping/setmeal/ShopSetmealIntegrationTest.java'
    'CONTEXT.md'
    'doc/module/CONTEXT.md'
    'doc/module/setmeal-implementation.md'
    'docs/adr/0015-店铺套餐独立建表并通过DDD提供展示查询.md'
    'frontend/src/api/shopSetmeal.ts'
    'frontend/src/components/shop/ShopSetmealSection.spec.ts'
    'frontend/src/components/shop/ShopSetmealSection.vue'
    'frontend/src/types/api.ts'
    'frontend/src/views/shop/ShopDetailView.spec.ts'
    'frontend/src/views/shop/ShopDetailView.vue'
)
git add -- @setmealPaths
if ($LASTEXITCODE -ne 0) { throw '暂存本次文件失败，停止提交' }
git commit --only -m 'feat(setmeal): 补齐店铺套餐查询与展示' -- @setmealPaths
if ($LASTEXITCODE -ne 0) { throw '提交失败，请检查输出' }
