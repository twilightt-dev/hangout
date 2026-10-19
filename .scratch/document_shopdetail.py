"""按已检查的声明清单补充中文 Javadoc，逐文件保留所有可执行代码。"""
import json
import re
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parent
before = json.loads((ROOT / 'javadoc-before.json').read_text(encoding='utf-8'))
rows = [json.loads(line) for line in (ROOT / 'javadoc-inventory.jsonl').read_text(encoding='utf-8').splitlines()]

# 同名字段按所属类型进一步解释；标签描述不以“参数”“结果”等空泛词代替业务含义。
names = {
    'id':'记录标识', 'shopId':'店铺标识', 'userId':'服务端确认的用户标识',
    'viewerId':'当前浏览者标识；游客传入 {@code null}', 'dishId':'菜品标识', 'dishIds':'待检查的菜品标识集合',
    'requestId':'客户端生成的 UUID 提交标识，用于幂等校验', 'request':'已经定义字段约束的业务请求',
    'current':'从 1 开始的页码', 'size':'每页数量', 'offset':'从 0 开始的记录偏移量', 'total':'全部匹配记录数',
    'pages':'总页数', 'records':'当前页有序记录', 'name':'名称', 'shopName':'店铺名称', 'typeId':'店铺类型标识',
    'images':'图片路径集合的存储文本', 'area':'所属商圈', 'address':'详细地址', 'x':'经度', 'y':'纬度',
    'avgPrice':'店铺人均价格', 'openHours':'营业时间', 'code':'标签代码', 'userCount':'去重后的提及用户数',
    'coverImage':'已上传的封面图片路径', 'price':'套餐支付金额，单位为分', 'originalPrice':'套餐原价，单位为分',
    'people':'适用人数', 'description':'说明文本', 'rules':'使用规则', 'quantity':'菜品数量，最多两位小数', 'unit':'数量单位',
    'items':'套餐包含的固定菜品明细', 'title':'标题', 'subTitle':'副标题', 'payValue':'支付金额，单位为分',
    'actualValue':'抵扣金额，单位为分', 'type':'优惠券类型', 'stock':'秒杀券剩余库存', 'beginTime':'秒杀开始时间',
    'endTime':'秒杀结束时间', 'availability':'可购买、未开始或售罄的展示状态',
    'overallScore':'独立总体评分，按实际分数的十倍保存', 'tasteScore':'口味评分，按实际分数的十倍保存',
    'environmentScore':'环境评分，按实际分数的十倍保存', 'serviceScore':'服务评分，按实际分数的十倍保存',
    'content':'正文内容', 'tags':'用户选择的预设标签', 'createTime':'创建时间', 'updateTime':'更新时间',
    'userName':'作者当前昵称', 'userIcon':'作者当前头像', 'isLike':'当前用户是否点赞；读取故障时为 {@code null}',
    'liked':'点赞数量', 'comments':'评价或评论数量', 'recommendCount':'不同用户的推荐人数',
    'recommended':'当前浏览者是否已经推荐该菜品', 'recommendedIds':'当前浏览者已推荐的菜品标识',
    'totalReviews':'全店评价数量', 'averageScore':'五分制均分；无评价时为 {@code null}',
    'distance':'距当前店铺的球面直线距离，单位为米', 'recommendationReason':'周边推荐说明',
    'since':'近期统计窗口的起始时间', 'limit':'最多读取的语料条数', 'text':'用于匹配口碑标签的文本',
    'now':'本次读取或创建使用的时间基准', 'center':'作为周边推荐中心的店铺资料',
    'shop':'店铺基础资料', 'vouchers':'优惠券读取结果', 'setmeals':'套餐读取结果',
    'recommendations':'推荐菜读取结果', 'reviews':'评价读取结果', 'reviewSummary':'全店评价统计',
    'blogs':'关联当前店铺的探店笔记', 'decisionTags':'评价与博客合并得到的近期口碑标签',
    'reviewTags':'仅由近期评价得到的高频标签', 'relatedShops':'同类型周边店铺',
    'relatedUnavailableReason':'缺少坐标或类型时无法推荐周边店铺的原因',
    'statistics':'全店评价统计响应', 'recommendationStateAvailable':'当前用户的推荐状态是否读取成功',
    'detail':'已经组合好的商详业务数据', 'aggregate':'待补齐衍生数据的商详组合对象',
    'executionResults':'任务上下文记录的各分项状态', 'states':'按分项名称组织的应用执行状态',
    'key':'上下文属性名或区域名称', 'data':'业务数据', 'empty':'判断业务数据是否为空的规则或判断结果',
    'mapper':'数据映射器', 'query':'执行分项读取的函数', 'message':'对外展示的固定提示',
    'status':'业务或节点状态', 'sum':'按十倍保存的总体评分总和', 'scoreSum':'按十倍保存的总体评分总和',
    'score':'按实际分数的十倍保存的评分', 'submitted':'本次提交并完成规范化的内容', 'existing':'已持久化的同提交标识记录',
    'other':'用于比较的另一个业务对象', 'dish':'推荐菜业务数据', 'review':'评价业务数据',
    'setmeal':'套餐业务数据', 'r':'待转换的数据库或领域记录', 's':'待转换的业务数据', 'v':'优惠券业务数据',
    'b':'探店笔记业务数据', 't':'标签业务数据', 'row':'数据库记录', 'rows':'有序数据库记录集合',
    'ids':'待批量查询的记录标识', 'nameKey':'用于精确去重的 UTF-8 菜名二进制键', 'value':'待读取或转换的值',
    'label':'校验错误中使用的业务对象名称', 'manager':'Spring 事务管理器', 'readOnly':'是否创建只读事务',
    'repository':'业务仓储', 'details':'店铺基础资料、探店语料和周边查询仓储', 'meals':'套餐仓储',
    'dishes':'推荐菜及用户推荐关系仓储', 'domain':'商详领域服务', 'facade':'当前业务门面',
    'queries':'独立查看更多查询用例', 'service':'应用用例服务', 'engine':'共享任务链引擎；允许未装配',
    'jdbc':'关系数据库查询模板', 'redis':'博客点赞集合使用的 Redis 模板',
    'properties':'任务链线程池与超时配置', 'queryTimeout':'并行查询阶段总预算，单位为毫秒',
    'postTimeout':'后处理阶段共享预算，单位为毫秒', 'postBudget':'测试使用的后处理预算，单位为毫秒',
    'queryBudget':'测试使用的查询预算，单位为毫秒', 'budget':'本阶段允许消耗的总纳秒数',
    'shutdownTimeout':'两个线程池共享的关闭等待预算', 'defaultPhaseTimeout':'默认单阶段执行预算',
    'cpuThreads':'CPU 密集任务线程数量', 'ioThreads':'IO 密集任务线程数量', 'queueCapacity':'每个线程池的有界等待队列容量',
    'closed':'关闭操作是否已启动的原子标记', 'threads':'固定工作线程数量', 'capacity':'有界队列容量',
    'prefix':'工作线程名称前缀', 'executor':'需要等待结束的线程池', 'remaining':'剩余等待时间，单位为纳秒',
    'duration':'需要校验的正时间间隔', 'input':'链路输入；节点共享引用并按只读约定访问',
    'output':'业务输出；允许显式写入 {@code null}', 'outputSet':'快照是否曾显式设置输出',
    'outputWritten':'当前节点或当前阶段是否显式写入输出', 'localOutput':'尚未提交的节点输出',
    'attributes':'只读属性映射；属性值遵守业务只读约定', 'removals':'本次写集要求删除的属性名',
    'delta':'需要应用到旧快照上的节点写集', 'snapshot':'节点可见或准备提交的数据快照',
    'parent':'创建当前节点视图的父上下文', 'nodeView':'是否为节点视图', 'owner':'同一次链路执行共享的身份标记',
    'node':'任务节点或本次执行创建的节点上下文', 'holder':'待注册的处理器及节点元数据',
    'nodeId':'任务组内唯一的节点标识', 'nodeName':'节点展示名称；空白时回退为节点标识',
    'group':'任务组标识', 'phase':'节点所属的处理阶段', 'mode':'阶段内串行或并行执行方式',
    'timeout':'整个阶段共享的超时预算', 'order':'阶段内排序值；数值越小越先执行',
    'taskType':'任务类型，决定投递到 CPU 池还是 IO 池', 'required':'是否为失败后阻断链路的必需节点',
    'handler':'实际执行节点业务的处理器', 'handlers':'Spring 发现的全部任务处理器', 'metadata':'节点注解元数据',
    'registry':'按任务组与阶段组织的节点注册表', 'executors':'CPU 与 IO 线程池管理对象',
    'cpuExecutor':'CPU 密集任务执行器', 'ioExecutor':'IO 密集任务执行器', 'dispatcher':'任务节点投递器',
    'nodes':'本阶段按顺序排列的任务节点', 'config':'当前阶段配置', 'phaseConfigs':'按执行顺序排列且阶段不重复的配置',
    'execution':'引擎持有的本次链路协调权限', 'context':'当前调用独立使用的任务上下文',
    'index':'节点在当前阶段列表中的下标', 'completed':'接收节点完成通知的回调',
    'writes':'尚未提交的属性写入或节点写集', 'failure':'节点失败原因；成功时为 {@code null}',
    'cause':'失败原因', 'error':'导致失败的异常；没有异常时为 {@code null}', 'success':'整条链路是否成功',
    'reports':'按阶段或节点顺序保存的执行报告', 'completedAt':'节点完成时的单调时钟纳秒值',
    'outcome':'待接收的节点完成通知', 'root':'本次执行绑定的根上下文', 'source':'测试使用的数据源',
    'initialized':'已经完成表结构初始化的查询模板', 'user':'服务端认证得到的用户；游客为 {@code null}',
    'x1':'起点经度', 'y1':'起点纬度', 'x2':'终点经度', 'y2':'终点纬度',
}

fields = {
    **names, 'claimed':'根上下文是否已领取执行权限，用于禁止复用', 'sealed':'上下文是否封存；封存后拒绝写入',
    'issued':'节点视图是否已经交付给处理器，防止重复分配', 'frozen':'注册表是否已排序并进入只读状态',
    'groups':'按任务组组织的节点集合', 'phaseResults':'链路结束后公布的阶段诊断报告',
    'completionQueue':'工作线程向协调线程发送完成通知的队列', 'futures':'已投递任务的句柄，用于尽力取消',
    'touched':'并行阶段成功节点已写入或删除的属性名，用于检测冲突', 'finished':'已接收完成通知的节点数量',
    'fatal':'当前首先观察到的阻断性错误', 'expired':'阶段总预算是否已经耗尽', 'started':'阶段开始时的单调时钟纳秒值',
    'base':'阶段起始快照或本次查询的店铺基本资料', 'working':'累积已接受写集后的待提交快照',
    'phaseEngine':'负责单阶段节点执行与结果合并的引擎', 'defaultPhases':'五个固定阶段的默认串行配置',
    'phases':'商详查询与后处理阶段的执行配置', 'read':'独立只读事务模板', 'write':'业务写入事务模板',
    'BASE_COLUMNS':'基础店铺资料使用的数据库列集合', 'BASE':'基础资料的数据库行映射器',
    'PHRASES':'预设口碑标签对应的正向文本短语词典', 'SENTENCE':'用于分割口碑语料句子的规则',
    'UNCERTAIN':'用于排除否定和不确定表述的词语规则', 'UPLOADED_IMAGE':'允许引用的已上传业务图片路径规则',
    'SECTION_NODES':'应用分项名称与提供该结果的任务节点之间的对应关系', 'GROUP':'商详任务组标识',
    'SIZE':'评价独立分页的固定每页数量', 'VOUCHER_PREVIEW_SIZE':'首屏优惠券数量上限',
    'SETMEAL_PREVIEW_SIZE':'首屏套餐数量上限', 'DISH_PREVIEW_SIZE':'首屏推荐菜数量上限',
    'REVIEW_PREVIEW_SIZE':'首屏评价数量上限', 'BLOG_PREVIEW_SIZE':'首屏探店笔记数量上限',
    'setmealId':'所属套餐标识', 'sort':'展示排序值',
    'CPU_BOUND':'CPU 密集任务，使用 CPU 线程池', 'IO_BOUND':'IO 密集任务，使用 IO 线程池',
    'SERIAL':'阶段内逐个执行，后续节点可读取已接受的前序结果', 'PARALLEL':'阶段内并行执行，各节点读取同一初始快照',
    'PRE_VALIDATION':'业务执行前的校验阶段', 'PRE_PROCESSING':'正式处理前的准备阶段',
    'DATA_PROCESSING':'业务事实读取与处理阶段', 'DATA_INSERTION':'需要持久化写入的业务阶段',
    'POST_PROCESSING':'组装与衍生信息生成阶段', 'SUCCESS':'节点成功完成', 'FAILED':'节点执行失败',
    'TIMED_OUT':'节点在阶段预算内未完成', 'ABANDONED':'节点已投递但因链路提前结束而不再接收结果',
    'NOT_EXECUTED':'节点尚未执行，链路已经停止推进',
    'TASTY':'味道好', 'NICE_ENVIRONMENT':'环境好', 'FRIENDLY_SERVICE':'服务热情', 'GOOD_VALUE':'价格实惠',
    'CLEAN':'干净卫生', 'CONVENIENT_LOCATION':'交通方便', 'FAST_SERVICE':'出餐快', 'EASY_PARKING':'停车方便，仅代表用户口碑',
    'mvc':'用于发送 HTTP 请求的测试客户端', 'filter':'测试中的认证过滤器', 'runner':'Spring 测试上下文启动器',
    'cpu':'测试使用的 CPU 执行器', 'io':'测试使用的 IO 执行器',
}

methods = {
    'getShopDetail':'聚合店铺首次展示数据；同步路径直接传播仓储异常，应用路径按装配情况选择任务链。',
    'base':'校验店铺标识并查询基本资料；店铺不存在时按业务错误返回。',
    'vouchers':'读取优惠券，沿用上架、有效期和可购买优先的展示规则。',
    'setmeals':'读取当前店铺的上架套餐及固定菜品明细。',
    'recommendations':'读取按推荐人数、创建时间及标识排列的店内推荐菜。',
    'recommendedIds':'批量读取当前用户已推荐的菜品标识。',
    'reviews':'读取店铺评价及当前作者资料。', 'statistics':'读取全部评价的数量和总体评分均分。',
    'blogs':'读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。',
    'reviewEvidence':'读取时间窗口内的近期评价标签语料，不受首屏预览数量限制。',
    'blogEvidence':'读取时间窗口内的近期探店博客语料，不受首屏预览数量限制。',
    'related':'以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。',
    'relatedUnavailableReason':'判断店铺是否具备周边推荐所需的有效坐标和类型。',
    'enrichShopDetail':'根据近期评价、探店博客和店铺位置补齐标签与周边推荐。',
    'mergeDecisionTags':'按作者去重合并评价标签与明确正向的博客表述，筛选至少两名作者提及的前五项。',
    'hasCoordinates':'判断店铺经纬度是否非空、有限且处于地理坐标范围内。',
    'distanceMeters':'使用球面距离公式计算两个经纬度位置之间的直线距离。',
    'getInput':'读取链路输入；返回的业务对象按只读约定使用。',
    'getAttribute':'读取当前快照与本地写集合成后的属性。',
    'getAttributes':'生成当前视图的只读属性容器；不深复制其中的业务对象。',
    'setAttribute':'将属性写入当前视图的本地写集；不会直接修改其他节点的可见快照。',
    'removeAttribute':'记录属性删除，并清除该属性尚未提交的本地写入。',
    'setOutput':'显式写入当前视图的输出槽位，包括写入空值。',
    'getOutput':'读取当前视图可见的输出，优先使用本地显式写入值。',
    'hasOutput':'判断输出是否曾被显式设置，区分未设置与显式设置为空。',
    'isSuccess':'读取引擎封存的整条链路执行状态。', 'getError':'读取使链路终止的首个阻断性异常。',
    'getPhaseResults':'读取引擎封存的阶段执行报告。', 'getNodeResults':'按阶段顺序展开全部节点诊断报告。',
    'fail':'标记当前节点自身失败；必需或可选节点的影响由引擎统一判断。',
    'ensureWritable':'检查上下文是否允许写入，封存后的视图与已启动的根上下文均拒绝外部修改。',
    'beginExecution':'领取根上下文的一次性协调权限，并将调用方预置数据转成初始快照。',
    'createNodeContext':'基于引擎提供的快照创建同类型节点视图，业务属性继续通过父类写集传递。',
    'snapshot':'读取主上下文当前已经提交的数据快照。',
    'nodeContext':'创建并检查属于本次执行的全新节点上下文，拒绝跨请求引用或重复分配。',
    'finishNode':'封存节点视图并提取写集；提取结果是否接受由阶段引擎决定。',
    'commit':'将本阶段已经接受的快照提交到根上下文。',
    'finish':'写入最终链路状态及只读报告集合，并封存根上下文。',
    'apply':'应用节点写集生成新快照，先删除属性再合并写入。',
    'register':'校验并注册节点；同组标识必须唯一，冻结后不再允许注册。',
    'freeze':'按排序值和节点标识排序，并将注册表切换为只读状态。',
    'isFrozen':'检查节点注册表是否已经冻结。', 'nodes':'按任务组和阶段取得排序后的节点列表。',
    'executeChain':'按默认五阶段串行配置执行任务组，保留输入上下文的实际子类型。',
    'executePhaseChain':'按调用方给定的阶段顺序执行任务组，并将失败及诊断信息封存到上下文。',
    'dispatch':'按任务类型投递节点；拒绝执行和上下文创建失败也转为节点完成通知。',
    'run':'执行一次阶段运行，收集节点通知，决定是否提交结果并生成完整报告。',
    'remaining':'计算共享阶段总预算的剩余纳秒数。',
    'awaitCompletion':'在剩余阶段预算内等待并接收一个完成通知。',
    'drainAvailable':'立即接收已经到达的完成通知，不额外等待。',
    'accept':'接收预算内的节点结果，处理必需失败及并行写入冲突。',
    'expire':'结束阶段等待，保留截止前完成的结果并标记未完成节点超时。',
    'report':'将节点元数据、执行状态与异常组合为诊断报告。',
    'close':'共享一次关闭预算等待两个线程池退出，超时后尽力取消，并恢复调用线程的中断标记。',
    'await':'仅在剩余预算为正时等待指定线程池终止。',
    'cpuExecutor':'取得供 CPU 密集节点使用的有界线程池。', 'ioExecutor':'取得供 IO 密集节点使用的有界线程池。',
    'taskChainExecutors':'创建由 Spring 管理并在容器关闭时释放的双线程池。',
    'taskNodeRegistry':'收集 Spring 任务处理器，读取目标类注解并注册排序后冻结。',
    'taskChainEngine':'装配共享任务链引擎与默认阶段预算。',
    'shopDetailDomainService':'通过仓储接口装配纯 Java 领域服务，领域层不依赖 Spring。',
    'validate':'校验线程数、队列容量和时间预算，非法配置在启动时拒绝。',
    'validateDuration':'校验正时间间隔及其向纳秒换算是否溢出。',
    'getShopId':'取得本次商详查询的店铺标识。', 'getViewerId':'取得显式传入的浏览者身份，不读取工作线程的登录状态。',
    'getSince':'取得本次请求统一使用的近期口碑统计起点。', 'getBase':'读取基础查询节点提交的店铺资料。',
    'setBase':'把基础资料写入上下文增量，参与引擎结果合并。',
    'put':'记录一个分项的应用结果，失败状态不写入领域业务模型。',
    'data':'读取分项结果中的业务数据；节点未提交该分项时返回空值。',
    'sectionResults':'在引擎结束后汇集分项状态，依据最终节点报告补齐失败与超时。',
    'ensureSuccess':'校验必需任务执行状态与输出，转换为商详用例错误。',
    'failure':'统一两种执行路径的错误边界，保留已知业务错误并隐藏底层异常细节。',
    'attempt':'执行分项查询并记录成功或空结果；运行时异常记录日志后转换为局部失败。',
    'success':'创建成功或成功但无数据的区域响应。', 'unavailable':'创建没有伪造业务数据的不可用区域响应。',
    'available':'判断区域数据是否可以展示，包含成功、空数据及部分来源成功。',
    'section':'合并业务数据与应用状态，再转换为前端分项响应。',
    'fromTotals':'从全部评价的数量与评分总和计算五分制均分，保留一位小数。',
    'offset':'计算从零开始的分页偏移量。', 'pages':'根据全部记录数和每页数量计算总页数。',
    'sameSubmission':'比较规范化后的业务提交内容，用于判断同提交标识能否安全重放。',
    'toDish':'将发布请求转换为经过领域校验的推荐菜，创建时间取当前服务端时间。',
    'toReview':'将发布请求转换为经过领域校验的评价，保留四项独立评分。',
    'score':'将五分制请求评分精确转换为十倍保存的整数。',
    'validateScore':'校验十倍整数评分是否处于一至五分且满足半分步长。',
    'invalid':'抛出当前业务对应的非法请求异常。',
    'storedScore':'从总体评分总和计算内部整数均分，不使用历史均分反推。',
    'transaction':'创建当前用例使用的独立事务模板，配置读取或写入需要的隔离级别。',
    'replay':'检查幂等记录的提交内容一致性并返回已有结果。',
    'nameConflict':'创建同店同名菜品冲突的业务错误，引导用户推荐已有菜品。',
    'requireUser':'校验写操作所需的可信登录身份。', 'requirePositive':'校验业务标识为正数。',
    'shopExists':'检查店铺记录是否存在。', 'lockShop':'锁定店铺行，串行化该店铺的业务写入。',
    'findSubmission':'按作者和提交标识查询已持久化内容，用于幂等处理。',
    'findByName':'按当前店铺与规范菜名查询已有推荐菜。',
    'lockDish':'按店铺和菜品标识查询并加行锁，用于推荐关系与人数一致更新。',
    'findById':'按店铺和菜品标识查询推荐菜，避免跨店引用。',
    'hasRecommendation':'判断用户是否已推荐指定菜品。', 'insertRecommendation':'插入用户与菜品的去重推荐关系。',
    'incrementRecommendationCount':'将菜品的不同用户推荐人数增加一。',
    'countByShopId':'统计指定店铺的全部业务记录。', 'sumOverallScoreByShopId':'汇总店铺全部评价的十倍总体评分。',
    'updateStatistics':'更新店铺评分与评价数；须与评价写入处于同一事务。',
    'pageWithTotal':'在同一读取快照内取得分页明细和总数。',
    'summarize':'在同一读取快照内汇总全店评价数量及均分。',
    'findShop':'读取店铺自身基础资料，不使用旧缓存中的评价统计。',
    'findPublishedByShopId':'查询当前店铺的上架套餐并批量补齐固定菜品明细。',
    'findPublished':'按展示顺序读取当前店铺的上架套餐主记录。',
    'countPublished':'统计当前店铺上架套餐总数。', 'pagePublished':'按展示顺序分页读取上架套餐。',
    'findItems':'批量读取指定套餐的菜品明细，避免逐套餐查询。',
    'toDomain':'将数据库记录映射为领域业务数据。', 'toRow':'将领域业务数据映射为待写入数据库的记录。',
    'toTags':'将已规范化的预设标签序列化为数据库 JSON 文本。',
    'parseTags':'将数据库保存的预设标签 JSON 文本还原为标签列表。',
    'time':'读取可空的数据库时间列并转换为本地日期时间。',
    'contains':'检查标签代码是否属于系统预设词典。', 'displayName':'取得预设标签的中文展示名称。',
    'status':'取得供外层统一异常处理使用的状态码。',
    'nodeId':'取得任务组内唯一的节点标识。', 'nodeName':'取得节点展示名称；省略时由注册过程回退为标识。',
    'description':'取得节点用途说明，不参与执行决策。', 'group':'取得节点所属任务组。',
    'phase':'取得节点所属阶段。', 'order':'取得阶段内排序值。', 'taskType':'取得线程池路由使用的任务类型。',
    'required':'判断该节点是否为失败后阻断链路的必需节点。',
}

classes = {
    'TaskNodeRegistry':'任务节点注册表，启动时按任务组校验唯一性并排序冻结，运行时按阶段只读查询。',
    'TaskDispatcher':'节点投递器，隔离节点上下文并按 CPU 或 IO 类型路由线程池，通过完成通知交回写集。',
    'TaskNode':'声明任务节点的所属组、阶段、排序、线程类型及必需性，供启动注册使用。',
    'TaskNodeHandler':'业务节点的执行契约，业务结果经上下文写集提交。',
    'PhaseConfig':'一个阶段的执行方式与共享超时预算，构造时拒绝无效配置。',
    'TaskChainProperties':'任务链的线程池容量及时间预算配置，绑定 taskchain 前缀。',
    'TaskChainConfiguration':'共享任务链的 Spring 装配入口，创建线程池、冻结注册表并提供引擎。',
    'TaskContext.Execution':'仅由引擎持有的协调权限，负责创建节点视图、接受写集及封存报告。',
    'PhaseExecutionEngine.StageRun':'单次阶段执行状态，集中维护共享预算、完成队列、合并冲突与节点报告。',
    'ShopDetailResult.BaseInfo':'商详基本信息区的店铺自身资料响应。',
    'ShopDetailResult.VoucherItem':'优惠券展示响应；展示状态不替代交易接口的最终资格校验。',
    'ShopDetailResult.ReviewStatistics':'全部评价的数量及均分响应，与近期标签语料口径分开。',
    'ShopDetailResult.BlogItem':'探店笔记预览与当前浏览者点赞状态响应。',
    'ShopDetailResult.TagItem':'用户口碑标签及去重提及人数响应。',
    'ShopDetailResult.RelatedItem':'以当前店铺为中心的同类周边店铺响应。',
    'RecommendDishPageResult':'推荐菜独立分页响应，记录集合通过防御性复制保持只读。',
    'ShopReviewPageResult':'评价分页与全店评分摘要响应，明细集合保持只读。',
    'ShopReviewItem':'评价展示响应，将内部十倍整数评分转换为五分制小数并补齐作者资料。',
    'ShopReviewTagOption':'可供用户选择的系统预设评价标签。',
    'ReviewTag':'系统预设评价标签及其中文名称，口碑统计共用同一词典。',
    'ExecutionMode':'阶段内部节点的执行方式。', 'ProcessPhase':'任务链支持的固定处理阶段，调用方只配置所需阶段。',
    'TaskType':'节点任务类型，用于 CPU 与 IO 线程池路由。', 'NodeExecutionStatus':'节点执行诊断状态，不等同于页面业务数据是否为空。',
}

test_summaries = {
    'missingEngineUsesSynchronousDomainAggregation':'验证没有引擎时同步聚合仍能返回相同的成功业务结果及个人状态。',
    'synchronousAggregationPropagatesRepositoryFailureWhileTaskChainDegrades':'验证同步直接传播仓储异常，而任务链保留其他成功区域。',
    'synchronousRequiredFailureUsesSameErrorBoundaryAsTaskChain':'验证同步与任务链的必需查询故障使用同一对外异常边界。',
    'returnsSixSectionsAndDistinguishesEmptyFromFailure':'验证六区响应区分查询失败与成功空数据，且不暴露底层错误。',
    'failedTagEvidenceDoesNotHideSuccessfulListsAndStatistics':'验证单一语料故障不隐藏成功列表和统计，并标记口碑来源不完整。',
    'wholeEnrichmentTimeoutKeepsAssembledOutputAndRejectsLateWrites':'验证增强超时后保留先前输出，并拒绝迟到节点写入。',
    'optionalQueryTimeoutIsPreservedInCompletedOutput':'验证可选查询超时在最终区域响应中保留为超时状态。',
    'missingShopIsFatalAndInvalidInputNeverStartsQuery':'验证缺失店铺使链路失败，非法标识在开始查询前被拒绝。',
    'missingCoordinatesHaveAnExplicitStateAndDoNotQueryNearby':'验证缺坐标时返回明确不可用状态并跳过周边查询。',
    'questionsDoNotContributePositiveEvidence':'验证疑问句不会被当成明确正向口碑证据。',
    'countsDistinctAuthorsAcrossReviewsAndBlogsAndRejectsNegativeSentences':'验证跨来源作者去重、标签排序以及否定语料排除。',
    'guestGetsAggregatedEmptySectionsAndFreshStatisticsInsteadOfOldShopFields':'验证游客可读取聚合空区域，评分使用新评价统计。',
    'relatedUsesShopCenterTypeRadiusAndStableDistanceOrder':'验证周边使用店铺中心、同类型、五公里范围及稳定排序。',
    'missingCoordinatesAreDifferentFromEmptyResults':'验证缺少位置与成功但没有附近店铺是不同状态。',
    'voucherPaginationFiltersExpiredAndOrdersPurchasableThenPriceAndId':'验证优惠券分页过滤失效记录，并按可购买性、价格和标识排序。',
    'blogPreviewIsShopScopedOrderedAndUsesCurrentAuthor':'验证探店笔记按店铺过滤和稳定排序，展示作者当前资料。',
    'rejectsInvalidPagesAndDistinguishesMissingShop':'验证非法分页返回参数错误，缺失店铺返回不存在。',
    'trustedPrincipalIsPassedToAsyncQueriesAndGuestHasNoPersonalState':'验证可信身份显式传给工作线程，游客不携带他人推荐或点赞状态。',
    'tagEvidenceUsesRecentHistoryBeyondPreviewAndStatisticsUseAllReviews':'验证近期标签覆盖首屏外语料，全店均分仍包含窗口外评价。',
    'shopContextWorksWithTheSharedEngineAndPreservesTheRequestedShop':'验证商详子上下文兼容共享引擎并保留请求店铺身份。',
    'preservesIndependentScoresAndCanonicalizesSubmissionContent':'验证四项独立评分及提交内容的规范化规则。',
    'idsAreUniqueWithinGroupAndOrderingIsStableAfterFreeze':'验证同组节点标识唯一，冻结后顺序由排序值和标识稳定确定。',
    'emptyRegistryFreezesButUnknownGroupCannotExecute':'验证空注册表可以冻结，但未知组无法执行。',
    'nodeMetadataAndPhaseBudgetsRejectInvalidValues':'验证节点元数据和阶段预算在构造时拒绝非法值。',
    'serialNodesPassAcceptedDataToFollowingNodes':'验证串行节点可以读取前序成功节点提交的数据。',
    'parallelNodesReadTheSameSnapshotAndCommitOnlyTheirOwnWrites':'验证并行节点读取相同起始快照，只合并各自增量。',
    'parallelConflictsRejectTheWholePhaseEvenForOptionalNodes':'验证并行写入冲突拒绝整个阶段，节点可选性不放宽冲突约束。',
    'optionalFailureDiscardsItsWritesAndContinues':'验证可选节点失败丢弃本地修改且允许后续阶段继续。',
    'requiredFailureReturnsBeforeBlockedPeerAndLateWritesCannotChangeResult':'验证必需失败尽快返回，迟到节点无法改写结果。',
    'stageDeadlineDistinguishesOptionalAndRequiredUnfinishedNodes':'验证阶段到期根据未完成节点的必需性决定能否继续。',
    'serialNodesShareOneBudgetInsteadOfReceivingFreshTimeouts':'验证串行节点共享阶段预算，不逐节点重置超时。',
    'rejectionIsDiagnosedAndRespectsNodeImportance':'验证线程池拒绝任务形成节点诊断，且遵守必需和可选规则。',
    'simultaneousRequestsNeverMixTheirContextData':'验证并发请求之间不会混用上下文业务数据。',
    'configuredPhaseOrderControlsExecutionAndSerialUpdatesCanReplaceResults':'验证自定义阶段顺序及串行覆盖结果的合法语义。',
    'requiredFailureRollsBackItsWholePhaseAndReportsUnexecutedNodes':'验证必需失败拒绝本阶段写入，并报告后续未执行节点。',
    'callerInterruptionEndsTheStageAndPreservesInterruptStatus':'验证调用线程中断会终止阶段并保留中断标记。',
    'explicitNodeFailureDiscardsLocalWritesWithoutPoisoningOptionalChain':'验证显式节点失败丢弃局部写入，可选失败不污染主错误。',
    'invalidInvocationsFailBeforeNodesRunOrContextIsClaimed':'验证非法调用在节点执行与上下文领取前被拒绝。',
    'queueWaitingConsumesTheStageBudgetAndCancelledWorkNeverStarts':'验证队列等待计入阶段预算，未启动且已取消的任务不再运行。',
    'failedParallelWritesDoNotConflictWithAcceptedSuccessfulWrites':'验证失败并行节点的丢弃写集不与成功结果产生冲突。',
    'oneContextCannotRunConcurrentlyOrBeExecutedAgain':'验证同一上下文不能并发启动或重复执行。',
    'concreteCollectionInputsOutputsAndAttributeValuesKeepTheirDeclaredTypes':'验证输入输出和属性集合保留实际业务类型。',
    'visibleAttributesAndReportsAreReadOnlyContainerSnapshots':'验证属性及报告容器为只读快照。',
    'serialNodesReceiveBusinessSubtypeAndPassBusinessPropertiesToTheRoot':'验证节点保留业务上下文子类型且串行属性能合并回根。',
    'parallelBusinessContextsReadTheSameSnapshotAndMergeDistinctProperties':'验证业务子上下文并行读取一致快照并合并不同属性。',
    'parallelBusinessPropertyConflictRejectsTheWholeStage':'验证业务属性并行冲突拒绝整个阶段提交。',
    'failedBusinessNodeCannotLeakItsPropertiesIntoTheNextNode':'验证失败业务节点的属性不会泄漏给后续节点。',
    'nodeFactoryCannotReturnAnotherExecutionOrReuseANode':'验证节点工厂不能跨执行返回上下文或复用节点视图。',
    'invalidSubtypeFactoryIsReportedAsNodeFailure':'验证无效业务子类型工厂转为节点失败报告。',
    'bothPoolsAreBoundedNamedAndRejectWithoutRunningOnCaller':'验证双线程池有界、具备名称且拒绝任务不在调用线程执行。',
    'poolsShareOneShutdownBudgetAndAttemptCancellationAfterIt':'验证两个线程池共享关闭预算，超时后尝试取消。',
    'workerThreadsDoNotImplicitlyInheritCallerIdentity':'验证工作线程不会隐式继承请求线程身份。',
    'invalidPoolAndBudgetConfigurationFailsStartup':'验证非法线程池和预算配置导致启动失败。',
    'defaultsMatchApprovedDevelopmentValues':'验证默认线程池与预算符合确认的开发配置。',
    'discoversAnnotatedBeansFreezesRegistryAndRunsDefaultChain':'验证注解节点被发现、注册表冻结及默认链路可执行。',
    'discoversTargetAnnotationButInvokesActualJdkProxyBean':'验证从目标类型发现注解，但实际调用容器中的代理处理器。',
    'duplicateNodeIdsFailDuringStartup':'验证同组节点重复在启动期间直接失败。',
    'emptyNodeSetAllowsStartupButUnknownGroupFailsAtInvocation':'验证空节点集合不影响启动，但调用未知组时失败。',
    'missingDisplayNameFallsBackToNodeIdInRegistrationAndFailureReport':'验证省略展示名时注册信息和失败报告都回退为节点标识。',
    'omittedAnnotationNameUsesNodeIdAndEmptyDescription':'验证注解省略展示元数据时的名称和描述默认行为。',
}

def param_description(name, row):
    owner = row['owner']
    if name in ('overallScore', 'tasteScore', 'environmentScore', 'serviceScore') and owner in ('PublishShopReviewRequest', 'ShopReviewItem'):
        return {'overallScore':'用户独立填写的总体评分','tasteScore':'口味评分','environmentScore':'环境评分','serviceScore':'服务评分'}[name] + '，使用五分制小数'
    if name == 'attributes' and owner.endswith('WriteSet'):
        return '本节点待提交的属性写入映射；构造时复制容器'
    if name == 'tags' and '/dataobject/' in row['file'].replace('\\', '/'):
        return '预设评价标签的 JSON 存储文本'
    if name == 'value' and owner == 'PublishShopReviewRequest': return '用户传入的五分制小数评分'
    if name == 'context' and owner == 'ShopDetailIntegrationTest': return '测试使用的 Spring Web 应用上下文'
    if row['kind'] == 'CONSTRUCTOR' and name in ('vouchers', 'reviews'):
        return '优惠券仓储' if name == 'vouchers' else '评价仓储'
    if name == 'mapper' and 'Mapper' not in owner and 'RepositoryImpl' in owner:
        return '当前业务的 MyBatis 数据访问接口'
    if name in ('reviews', 'blogs') and row['name'] == 'mergeDecisionTags':
        return '近期评价标签证据' if name == 'reviews' else '近期探店博客文本证据'
    extra = {'canContinue':'本阶段结束后是否允许继续后续阶段', 'conflict':'并行写集是否发生冲突',
             'count':'记录数量或测试计数值', 'displayName':'中文展示名称', 'field':'待校验的字段名',
             'icon':'用户当前头像路径', 'latch':'协调测试线程执行时机的计数门闩',
             'nodeResults':'本阶段各节点的有序诊断报告', 'page':'需要补齐状态的推荐菜分页数据',
             'property':'测试业务上下文使用的属性名称', 'result':'需要转换或写入上下文的分项结果'}
    return names.get(name, fields.get(name, extra.get(name, '本次调用使用的业务值')))

def summary(row):
    name, owner, kind = row['name'], row['owner'], row['kind']
    if kind == 'FIELD':
        if row['type'].endswith('Repository'): return {'ShopVoucherRepository':'优惠券仓储','ShopReviewRepository':'店铺评价仓储','RecommendDishRepository':'推荐菜与用户推荐关系仓储','ShopSetmealRepository':'店铺套餐仓储','ShopDetailRepository':'店铺基础资料、标签语料和周边查询仓储'}[row['type']] + '。'
        if name == 'displayName': return '系统预设标签的中文展示名称。'
        if name == 'context' and owner == 'ShopDetailIntegrationTest': return '集成测试使用的 Spring Web 应用上下文。'
        if name == 'tags' and '/dataobject/' in row['file'].replace('\\', '/'): return '预设评价标签的 JSON 存储文本。'
        if name == 'status' and owner == 'ShopSetmeal': return '套餐上架状态，参与公开查询的过滤。'
        return fields.get(name, '当前对象维护的 ' + name + ' 状态') + '。'
    if kind in ('CLASS','RECORD','INTERFACE','ENUM','ANNOTATION_TYPE'):
        if '/dataobject/' in row['file'].replace('\\','/'):
            return {'RecommendDish':'推荐菜主表','ShopReview':'店铺评价表','ShopSetmeal':'套餐主表','ShopSetmealItem':'套餐固定菜品明细表'}[name] + '的持久化映射，字段形态对应数据库存储。'
        if name.startswith('Query') and name.endswith('Node'):
            return {'QueryRecommendationNode':'并行查询推荐菜预览及当前用户的去重推荐状态。','QueryShopBaseNode':'查询必须存在的店铺基本资料，失败时阻断商详链路。','QueryShopReviewNode':'并行查询首屏评价与全店评分统计，并分别记录读取状态。'} .get(name, '查询商详分项数据并通过节点上下文提交读取结果。')
        if owner in classes: return classes[owner]
        if '/test/' in row['file'].replace('\\','/'):
            if name == 'Config': return '商详集成测试配置，使用独立 H2 表结构与测试依赖。'
            if name.endswith('Test'): return '通过公开入口验证' + {'ShopDetailIntegrationTest':'商详 HTTP、任务链与读取契约', 'ShopDetailApplicationServiceTest':'商详应用编排、局部降级及超时', 'TaskChainEngineTest':'任务链执行顺序、隔离和失败边界'}.get(name, '当前模块的业务约束和执行边界') + '。'
            return '测试使用的' + ('业务上下文子类型。' if 'Context' in name else '任务处理器或容器装配对象。')
        if name.endswith('RepositoryImpl'): return '实现' + name.removesuffix('RepositoryImpl') + '仓储，负责数据库查询及持久化模型转换。'
        if name.endswith('Mapper'): return '当前业务表的 MyBatis 数据访问契约；写入锁须在调用方事务内使用。'
        if name.endswith('FacadeImpl'): return '业务门面实现，转调应用用例，不维护额外业务规则。'
        if name.endswith('Facade'): return '面向控制器的业务门面，隐藏应用层编排并返回业务响应。'
        if name.endswith('Controller'): return '业务 HTTP 入口，解析参数并显式传递服务端认证身份。'
        if name.endswith('Exception'): return '当前业务的可识别异常，由外层统一转换为错误响应。'
        return '当前业务的' + ('数据传输模型。' if kind == 'RECORD' else '协作对象。')
    if kind == 'CONSTRUCTOR':
        if row['type'] == '' and owner.endswith(('PhaseConfig','TaskNodeHolder','ShopReview','RecommendDish','ShopSetmeal','ShopSetmealItem')):
            return '创建并校验' + owner.split('.')[-1] + '，拒绝不满足业务约束的数据。'
        if owner.endswith(('Snapshot','WriteSet')): return '复制容器建立只读视图，业务对象本身仍按只读约定访问。'
        if owner.endswith(('PageResult','ShopPage')): return '复制记录集合建立只读分页结果。'
        if owner == 'TaskContext' and len(row['params']) == 1: return '创建本次请求专用的根上下文，初始快照为空。'
        if owner == 'TaskContext': return '初始化输入、可见快照及节点身份，供根上下文和节点视图共用。'
        if owner == 'TaskContext.Execution': return '将协调权限绑定到唯一根上下文，供引擎管理本次执行。'
        return '创建 ' + owner.split('.')[-1] + '，初始化所需依赖及实例状态。'
    if name in test_summaries: return test_summaries[name]
    if name == 'getShopDetail':
        if owner == 'ShopDetailApplicationService': return '选择任务链或同步聚合路径，检查必需结果并转换六区响应及对外错误。'
        if len(row['params']) == 1: return '以游客身份同步聚合店铺详情，复用包含浏览者参数的领域查询入口。'
        return '串行查询首屏所需事实数据，再补齐口碑标签与周边店铺；仓储异常交由调用方处理。'
    if name == 'execute':
        if owner == 'PhaseExecutionEngine': return '执行一个阶段；节点共享阶段总预算，成功结果在阶段收尾时统一决定是否提交。'
        if owner == 'TaskNodeHandler': return '执行节点业务，读取可见上下文并通过属性或输出写集提交结果。'
        return '执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。'
    if name == 'create':
        if owner == 'TaskChainExecutors': return '创建固定大小的有界线程池，禁止继承调用线程上下文，并使用直接拒绝策略。'
        return '为新的评价创建经过规范化及业务校验的领域对象。'
    if name == 'from':
        if owner == 'ShopDetailResult': return '把领域组合与应用执行状态转换为六区响应，不查询仓储或推导业务规则。'
        return '将领域业务数据转换为当前响应模型，不暴露持久化对象。'
    if name == 'map':
        if owner == 'SectionResult': return '保留区域状态及提示，只转换非空业务数据。'
        return '批量补齐套餐菜品明细并映射为领域模型。'
    if name == 'insert': return '插入经过领域校验的业务记录，并返回生成标识。'
    if name == 'detail': return '读取商详首次展示的六区组合及当前浏览者个人状态。'
    if name == 'publish': return '发布' + ('推荐菜并原子维护作者首次推荐关系与人数。' if 'RecommendDish' in owner else '店铺评价，幂等记录、评价写入与店铺统计在同一事务完成。')
    if name == 'recommend': return '为当前用户增加一次去重推荐，重复请求不重复增加推荐人数。'
    if name == 'page': return '分页读取当前店铺的业务记录，返回有序明细及总数。'
    if name == 'list': return '读取店铺上架套餐及明细，沿用公开查询参数约束。'
    if name == 'tags': return '读取供用户选择的固定评价标签词典。'
    if name in methods: return methods[name]
    if name.startswith('get'): return '读取测试业务上下文中通过写集维护的属性。'
    if name.startswith('set'): return '通过上下文写集更新测试业务属性。'
    helpers = {'setup':'创建测试所需的独立执行器、仓储替身和应用入口。', 'empty':'创建测试用的成功空分页数据。',
               'service':'按指定阶段预算装配测试应用入口与真实任务引擎。', 'parallel':'创建测试用的并行阶段配置。',
               'awaitIgnoringInterrupts':'模拟无法立即中断的外部任务，用于验证迟到写入隔离。',
               'node':'创建带指定元数据的测试任务节点。', 'shopNode':'创建商详业务上下文测试节点。',
               'proxiedNode':'创建用于验证代理注解发现的测试处理器。', 'shop':'向隔离测试数据库写入店铺夹具。',
               'dataSource':'创建独立 H2 内存数据源，避免测试共享真实业务数据。',
               'jdbcTemplate':'初始化隔离测试表结构并创建 JDBC 查询模板。',
               'sqlSessionTemplate':'创建连接隔离数据库的 MyBatis 会话模板。',
               'transactionManager':'为隔离测试数据源创建事务管理器。'}
    return helpers.get(name, '验证当前业务入口的约束与返回行为。')

def return_description(row):
    name, typ = row['name'], row['type']
    precise = {
        'toDish':'尚未持久化的合法推荐菜领域对象', 'toReview':'尚未持久化的合法评价领域对象',
        'score':'精确放大十倍后的整数评分', 'storedScore':'供店铺表存储的十倍整数总体均分',
        'transaction':'已配置只读标记与隔离级别的事务模板',
        'recommend':'包含最新推荐人数及当前用户推荐状态的菜品响应',
        'nameConflict':'提示同店同名菜品已存在的业务冲突异常',
        'failure':'保留已知业务状态或转换为 503、504 的商详异常',
        'tags':'系统预设评价标签的代码与中文名称列表',
        'nodeId':'任务组内唯一的节点标识', 'nodeName':'节点展示名称；空白值由注册过程回退为标识',
        'description':'节点用途说明', 'group':'节点所属任务组标识', 'phase':'节点所属处理阶段',
        'order':'排序值；数值越小越先执行', 'taskType':'决定工作线程池的 CPU 或 IO 任务类型',
        'getShopId':'本次查询的店铺标识', 'getSince':'本次请求统一使用的近期口碑统计起点',
        'getName':'当前视图可见的测试名称属性', 'getCount':'当前视图可见的测试计数属性',
        'getOutput':'当前视图的业务输出；未设置或显式设空时均可能为 null，二者通过 hasOutput 区分',
        'getPhaseResults':'按阶段执行顺序保存的只读报告列表',
        'getNodeResults':'按阶段顺序展开的只读节点报告列表',
        'apply':'合并删除、属性写入与输出更新后的新快照，原快照保持不变',
        'report':'包含节点标识、必需性、执行状态及失败原因的诊断报告',
        'displayName':'标签对应的中文展示名称', 'create':'完成字段规范化与业务校验的新评价',
        'base':'当前店铺的基本资料',
        'reviewEvidence':'近期评价的作者与标签证据列表', 'blogEvidence':'近期探店笔记的作者与文本证据列表',
        'summarize':'全店评价数量和五分制总体均分；无评价时均分为空',
        'findPublishedByShopId':'有序上架套餐列表，每项包含固定菜品明细',
        'findPublished':'有序上架套餐主记录列表', 'findItems':'按套餐和展示顺序排列的固定菜品明细',
        'findSubmission':'相同用户及提交标识对应的持久化记录；不存在时为空',
        'findByName':'店内同规范菜名的记录；不存在时为空',
        'lockDish':'已加行锁的指定店内菜品；不存在时为空',
        'findById':'指定店内的菜品记录；不存在时为空',
        'insertRecommendation':'实际插入的用户推荐关系行数', 'incrementRecommendationCount':'实际更新的菜品行数',
        'updateStatistics':'实际更新的店铺行数',
        'time':'本地日期时间；数据库列为空时返回 null',
        'toDomain':'由持久化字段恢复的领域业务对象', 'toRow':'可交由 MyBatis 写入的持久化记录',
        'toTags':'可存入数据库的标签 JSON 文本', 'parseTags':'从存储文本还原的预设标签列表',
        'taskChainExecutors':'由 Spring 管理生命周期的双线程池对象',
        'taskNodeRegistry':'完成节点校验、排序并冻结的注册表',
        'taskChainEngine':'具备默认五阶段串行配置的任务链引擎',
        'cpuExecutor':'CPU 密集任务专用的有界线程池', 'ioExecutor':'IO 密集任务专用的有界线程池',
        'shopDetailDomainService':'通过仓储接口装配的商详领域服务',
        'node':'具有指定元数据及执行逻辑的测试节点', 'shopNode':'测试用商详处理器',
        'proxiedNode':'用于验证代理发现与调用的任务处理器',
        'parallel':'测试使用的并行阶段配置列表', 'empty':'无记录且总数为零的成功分页结果',
        'service':'已按测试预算装配的商详应用服务',
        'dataSource':'独立的 H2 内存数据源', 'jdbcTemplate':'连接隔离测试表结构的 JDBC 模板',
        'sqlSessionTemplate':'连接隔离数据库的 MyBatis 会话模板',
        'transactionManager':'隔离测试数据源使用的事务管理器',
    }
    if name == 'getShopDetail': return '包含六个展示区域及各分项状态的商详响应' if row['owner']=='ShopDetailApplicationService' else '包含查询事实及衍生数据的商详组合结果'
    if name == 'create' and row['owner']=='TaskChainExecutors': return '使用有界队列、命名工作线程及拒绝策略的固定线程池'
    if name == 'replay': return '相同提交标识对应的既有评价标识' if row['owner']=='ShopReviewApplicationService' else '相同提交标识对应的既有菜品响应'
    if name in ('vouchers','setmeals','recommendations','reviews','blogs'):
        return {'vouchers':'优惠券','setmeals':'上架套餐及固定菜品明细','recommendations':'推荐菜','reviews':'店铺评价及当前作者资料','blogs':'探店笔记及当前浏览者点赞状态'}[name] + '的分页结果，包含总数与当前页记录'
    if name == 'statistics': return '全店评价数量和五分制总体均分'
    if name == 'detail': return '包含六区数据与分项状态的商详响应'
    if name == 'list': return '上架套餐分页响应' if 'PageResult' in typ else '包含固定菜品明细的上架套餐列表'
    if name in precise and typ != 'boolean': return precise[name]
    if typ == 'boolean':
        return {'available':'是否存在可以展示的分项结果', 'hasCoordinates':'是否具备有效经纬度',
                'sameSubmission':'两次提交的规范化业务内容是否相同', 'isSuccess':'链路是否成功',
                'hasOutput':'输出是否曾被显式设置', 'isFrozen':'注册表是否已冻结',
                'shopExists':'店铺是否存在', 'hasRecommendation':'当前用户是否已经推荐该菜品',
                'lockShop':'店铺是否存在且已成功取得行锁', 'contains':'代码是否属于预设标签'}.get(name, '该业务判断是否成立')
    if name == 'relatedUnavailableReason': return '无法推荐的业务原因；具备查询条件时为 {@code null}'
    if name == 'distanceMeters': return '两点直线距离，单位为米'
    if name == 'related': return '按距离排序的分页结果；缺少坐标或类型时领域入口返回 {@code null}' if row['owner']=='ShopDetailDomainService' else '带可用状态的周边分页结果'
    if name in ('page','pageWithTotal','pagePublished','pages','offset'):
        return '分页偏移量' if name=='offset' else '总页数' if name=='pages' else '当前页结果；无匹配数据时为空记录集合'
    if name == 'lockShop': return '锁定的店铺标识；店铺不存在时为 {@code null}'
    if name == 'dispatch': return '支持尽力取消的任务句柄；结果通过完成通知回调交付'
    if name in ('from','map','section','success','unavailable','attempt'): return '转换或执行后的业务响应，保留对应分项状态'
    if name == 'remaining': return '剩余纳秒数；非正数表示阶段预算耗尽'
    if name in ('getShopDetail','enrichShopDetail'): return '包含查询事实及衍生数据的商详组合结果'
    if name == 'mergeDecisionTags': return '按提及用户数和词典顺序排列的最多五个口碑标签'
    if name in ('createNodeContext','nodeContext'): return '与根上下文具有相同业务类型的全新节点视图'
    if name == 'beginExecution': return '只交给引擎使用的一次性执行协调权限'
    if name == 'getError': return '首个阻断异常；没有异常时为 {@code null}'
    if name == 'findShop': return '店铺基本资料；不存在时返回空 Optional'
    if typ.startswith('Optional'): return '匹配的业务记录；不存在时返回空 Optional'
    if name in ('recommendedIds',): return '当前用户在给定菜品集合中已推荐的标识'
    if name == 'sectionResults': return '按区域名称组织的只读结果映射'
    if name == 'snapshot': return '主上下文当前已提交的只读容器快照'
    if name == 'finishNode': return '封存后的节点增量及显式失败原因'
    if name == 'getAttributes': return '叠加当前写集后的只读属性映射'
    if name == 'getInput': return '本次链路的原始输入引用'
    if name in ('getAttribute','data','getBase','getViewerId'): return '当前视图可见的业务值；尚未提供时为 {@code null}'
    if name == 'fromTotals': return '评价数量与五分制均分；无评价时均分为 {@code null}'
    if name == 'insert': return '新记录标识' if 'Long' in typ else '受影响的数据库行数'
    if name == 'publish': return '新建或幂等重放的发布结果'
    if name == 'nodes': return '指定任务组和阶段的只读有序节点列表'
    if name == 'executeChain' or name == 'executePhaseChain': return '已封存执行状态与报告的原始业务上下文'
    if name == 'execute' or name == 'run': return '当前阶段能否继续及各节点诊断报告'
    if name in ('countByShopId','countPublished','sumOverallScoreByShopId'): return '匹配记录数或十倍总体评分总和'
    if name == 'status': return '外层异常处理使用的状态码'
    if name == 'close': return ''
    return '按本方法约定得到的' + ('只读集合结果' if any(t in typ for t in ('List','Set','Map')) else '业务结果')

def existing_body(doc):
    if not doc: return ''
    lines = [line.rstrip() for line in doc.strip().splitlines()]
    # 保留原有描述及有效标签；只有空参数标签会由下方的规范标签取代。
    return '\n'.join(lines).strip()

def declared_errors(row):
    """只为可从实现确认的关键调用契约补充异常标签。"""
    owner, name = row['owner'], row['name']
    if '/test/' in row['file'].replace('\\','/') and row['kind']=='METHOD':
        original = before[row['file']]
        start = len(original.encode('utf-16-le')[:row['start']*2].decode('utf-16-le'))
        end = len(original.encode('utf-16-le')[:row['end']*2].decode('utf-16-le'))
        signature = original[start:end]
        thrown = re.search(r'\)\s+throws\s+([\w.,\s]+?)\s*\{', signature)
        if thrown:
            return [(exception.strip(), '测试请求、并发等待或隔离环境初始化失败') for exception in thrown[1].split(',')]
    if owner=='TaskNodeHandler' and name=='execute': return [('Exception','节点业务执行失败，由引擎记录并决定是否阻断链路')]
    if owner=='PhaseExecutionEngine.StageRun' and name=='awaitCompletion': return [('InterruptedException','协调线程等待节点完成时被中断')]
    if owner=='TaskChainExecutors' and name=='await': return [('InterruptedException','关闭等待期间当前线程被中断')]
    if owner=='ShopDetailRepositoryImpl' and name=='time': return [('SQLException','读取数据库时间列失败')]
    if owner == 'ShopDetailDomainService' and name == 'base':
        return [('ShopDetailException', '店铺标识非法时为 400，店铺不存在时为 404')]
    if owner == 'ShopDetailApplicationService' and name == 'getShopDetail':
        return [('ShopDetailException', '输入非法、店铺不存在，或必需查询失败、超时')]
    if owner == 'TaskContext' and name in ('setAttribute','removeAttribute','setOutput','fail','beginExecution','ensureWritable'):
        return [('IllegalStateException', {'fail':'根上下文试图标记节点失败，或当前节点已经封存', 'beginExecution':'上下文为节点视图，或根上下文已经开始过执行'}.get(name,'当前视图已封存，或根上下文已领取执行权限'))]
    if owner == 'TaskContext.Execution' and name in ('nodeContext','commit','finish'):
        return [('IllegalStateException', '根上下文已结束，或节点工厂未遵守同类型全新视图约定' if name=='nodeContext' else '根上下文已经结束')]
    if owner == 'TaskContext.Execution' and name == 'finishNode':
        return [('IllegalArgumentException', '传入的视图不是本次执行所属的节点上下文')]
    if owner == 'TaskNodeRegistry' and name == 'register':
        return [('IllegalStateException', '注册表已经冻结'), ('IllegalArgumentException', '同组节点标识重复')]
    if owner == 'TaskChainEngine' and name in ('executeChain','executePhaseChain'):
        return [('IllegalArgumentException', '任务组、阶段配置或节点选择不合法'), ('IllegalStateException','注册表尚未冻结，或上下文已被使用')]
    if row['kind']=='CONSTRUCTOR' and owner=='PhaseConfig':
        return [('IllegalArgumentException','阶段预算不是正数或超出纳秒计时范围'), ('NullPointerException','阶段、执行方式或预算为空')]
    if row['kind']=='CONSTRUCTOR' and owner in ('ShopSetmeal','ShopSetmealItem'):
        return [('IllegalStateException','套餐或菜品数据不满足领域约束')]
    return []

byfile = defaultdict(list)
for row in rows: byfile[row['file']].append(row)
stats = defaultdict(int)
for filename, declarations in byfile.items():
    source = before[filename]
    edits = []
    for row in declarations:
        # javac 使用 UTF-16 字符偏移，Python 按码点索引。
        start = len(source.encode('utf-16-le')[:row['start']*2].decode('utf-16-le'))
        body = existing_body(row['doc'])
        if not body: body = summary(row)
        # 纠正旧教学注释中会被 Javadoc 解析成 HTML 标签的泛型和方法引用文本。
        body = body.replace('Outcome<O>', '{@code Outcome<O>}') if '{@code Outcome<O>}' not in body else body
        body = body.replace('completionQueue:add', '{@code completionQueue::add}')
        body = body.replace('节点完成的失败标记', '节点完成时的单调时钟纳秒值')
        if row['owner'] == 'TaskChainEngine' and row['kind'] == 'CLASS':
            body = ('任务链入口，按调用方配置的顺序推进阶段，并封存链路结果及执行报告。\n'
                    '<ul>\n'
                    '  <li>{@link #executeChain(String, TaskContext)}：按默认五阶段串行执行。</li>\n'
                    '  <li>{@link #executePhaseChain(String, TaskContext, List)}：自定义阶段顺序与阶段内执行方式。</li>\n'
                    '</ul>')
        content = body.splitlines()
        # 删除空的 @param/@return 占位标签。
        content = [line for line in content if not re.fullmatch(r'\s*@(?:param\s+\S+|return)\s*', line)]
        body = '\n'.join(content).strip()
        tags = []
        for generic in row['generics']:
            if not re.search(r'@param\s+<' + re.escape(generic) + r'>\s+\S', body):
                tags.append('@param <'+generic+'> '+{'I':'链路输入类型','O':'链路输出类型','C':'实际业务上下文类型','T':'当前业务数据类型','R':'转换后的业务数据类型'}.get(generic,'业务数据类型'))
        for param in row['params']:
            if not re.search(r'@param\s+' + re.escape(param) + r'\s+\S', body):
                tags.append('@param '+param+' '+param_description(param,row))
        if row['kind'] == 'METHOD' and row['type'] != 'void' and not re.search(r'@return\s+\S',body):
            tags.append('@return '+return_description(row))
        for exception, meaning in declared_errors(row):
            if not re.search(r'@(?:throws|exception)\s+'+re.escape(exception)+r'\s', body):
                tags.append('@throws '+exception+' '+meaning)
        if tags: body += '\n\n' + '\n'.join(tags)
        # 关联 Javadoc 应位于最前面的声明注解之前。
        replace_start = start
        if row['doc'] is not None:
            prefix = source[:start].rstrip()
            if not prefix.endswith('*/'):
                raise RuntimeError('无法定位原文档：'+filename+' '+row['owner']+'.'+row['name'])
            replace_start = prefix.rfind('/**')
            if replace_start < 0: raise RuntimeError('缺失文档起点')
        line_start = source.rfind('\n',0,replace_start)+1
        prefix_on_line = source[line_start:replace_start]
        indent = re.match(r'[ \t]*',prefix_on_line).group()
        inline = bool(prefix_on_line.strip())
        if inline and not indent:
            indent = '    '
        doc = '/**\n' + '\n'.join(indent+' *'+(' '+line if line.strip() else '') for line in body.splitlines()) + '\n'+indent+' */\n'+indent
        if inline: doc = '\n'+indent+doc
        # 起点已有行内缩进，交给原文保留，不重复添加。
        edits.append((replace_start,start,doc))
        stats[row['kind']] += 1
    for start,end,doc in sorted(edits,reverse=True): source=source[:start]+doc+source[end:]
    # 统一所编辑文件的换行为 LF，不触及任何代码 token。
    source = source.replace('\r\n','\n')
    if filename.endswith('TaskChainProperties.java'):
        source = source.replace('//阶段执行的超时时间\n', '//两个线程池关闭时共享的等待预算\n')
    source = '\n'.join(line.rstrip() for line in source.split('\n'))
    Path(filename).write_bytes(source.replace('\n','\r\n').encode('utf-8'))
print('已补齐声明：',dict(stats))
print('文件数量：',len(byfile))
