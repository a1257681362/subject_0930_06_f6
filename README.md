# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

## F1 风电叶片巡检台账

在骨架上新增叶片运维台账（schema 版本 `f1-1`），已跑通：建档 → 巡检 → 缺陷登记 → 维修 → 验收。电量结算后续再做。

### 数据表

| 表 | 说明 |
| --- | --- |
| `t_turbine` | 风机档案：场址、机型、并网时间、退役时间 |
| `t_blade` | 叶片档案：序列号、方位（A/B/C）、材料、装上/拆下时刻 |
| `t_blade_replacement` | 换件记录：旧件 → 新件前后关系 |
| `t_inspection_order` | 巡检工单：无人机影像、巡检时刻、状态机 |
| `t_defect` | 缺陷登记：缺陷等级、维修结果、验收结论 |

### 业务规则

- **编号互斥**：风机编号与叶片编号各自唯一，且两类编号之间也不得重复（`AssetNoGuard` 跨表校验 + 数据库唯一约束）；叶片序列号唯一。
- **归属口径（同一口径）**：风机服役窗口 `[grid_connected_at, decommissioned_at)` 与叶片归属窗口 `[installed_at, removed_at)` 统一为**左闭右开**。叶片未指定装上时刻时默认取所属风机并网时间。任意时刻同一风机同一方位至多一片在役叶片。
- **换件**：旧叶片下架（`removed_at` = 换件时刻）与新叶片上架（`installed_at` = 换件时刻）同事务完成，`t_blade_replacement` 保留旧件与新件的前后关系。
- **工单状态机**：`PENDING → INSPECTED → DEFECT_REGISTERED → REPAIRED → ACCEPTED`；无缺陷时 `INSPECTED → CLOSED`。缺陷状态机：`OPEN → REPAIRED → ACCEPTED`，验收驳回退回 `OPEN` 重新维修；工单下全部缺陷验收通过后工单才闭环。

### 接口一览

```
POST   /api/turbines                          建档（可同时录入首批叶片）
PUT    /api/turbines/{id}                     档案调整（如把并网时间调到临界时间）
GET    /api/turbines/{id}/blades              当前在役叶片
GET    /api/turbines/{id}/blades/at?time=     某时刻归属叶片（口径验证）
GET    /api/turbines/{id}/blades/history      叶片全量档案 + 换件前后关系
POST   /api/turbines/{id}/blades              补录叶片
POST   /api/turbines/{id}/replacements        换件
POST   /api/inspection-orders                 创建巡检工单
POST   /api/inspection-orders/{id}/inspect    提交无人机影像与巡检时刻
POST   /api/inspection-orders/{id}/defects    缺陷登记
POST   /api/inspection-orders/{id}/close      无缺陷闭环
POST   /api/defects/{id}/repair               维修结果回填
POST   /api/defects/{id}/accept               验收（pass=true 闭环 / false 驳回返工）
```

联调期上述业务接口在 Shiro 中放行（`anon`），登录体系接入后收紧。

### 验证

- `mvn test`：`BladeLedgerFlowTest`（全流程、编号互斥、换件前后关系、状态机）+ `BoundaryAttributionTest`（把风机档案调到临界时间后，叶片档案按同一左闭右开口径归属）。
- 测试使用 H2 内存库（`src/test/resources/application.yml`），不污染 `data/` 本地文件库。

## F2 无人机叶片巡检航线

在 F1 基础上新增无人机叶片巡检航线（schema 版本 `f2-1`）：航线按 风机+叶片方位+拍摄距离 配置，内容版本化发布；发布前校验临时禁飞区、风速条件、电量余量；临时改线走两级审批并留版本差异。

### 数据表

| 表 | 说明 |
| --- | --- |
| `t_flight_route` | 航线：风机+叶片方位+拍摄距离唯一组合，指向当前发布版本 |
| `t_flight_route_version` | 航线版本：航点串、预计航程、耗电与风速限值、校验留痕；状态 `DRAFT/PUBLISHED/RETIRED` |
| `t_no_fly_zone` | 禁飞区：临时/永久，多边形+限飞高度+生效窗口 `[effective_from, effective_to)`；只取消不删除 |
| `t_flight_task` | 飞行任务：创建时绑定当前发布版本；状态 `PLANNED/TAKEN_OFF/COMPLETED/CANCELLED` |
| `t_route_change_request` | 临时改线申请：基准版本、拟改航点、版本差异 JSON；状态 `PENDING/APPROVED/REJECTED` |
| `t_route_change_approval` | 改线审批记录：每级一条（change_id + step_no 唯一） |
| `t_audit_event` | 审计事件：航线规划与禁飞区关键动作留痕 |
| `t_inspection_order` | 新增 `flight_task_id`、`route_version_id`：历史影像始终引用拍摄时的原航线版本 |

### 业务规则

- **航线配置**：一条航线 = 风机 + 叶片方位（A/B/C）+ 拍摄距离 的唯一组合；航线内容版本化，版本号航线内递增。
- **发布前校验**（`RouteCheckService`）：① 逐航点核对当前生效的临时/永久禁飞区（射线法 + 限飞高度），冲突给出**航点坐标、命中区域和原因**；② 当前风速不得超过航线限值；③ 当前电量 − 预计耗电 不得低于安全余量 20%。校验不过发布被拦，冲突明细随响应返回。
- **版本作用域**：新版本发布只改绑未起飞（`PLANNED`）任务；已起飞任务版本锁定。巡检工单关联飞行任务时带出任务绑定的航线版本，历史影像不随新版本发布改变引用；旧版本退役但保留，航点级内容可查。
- **临时改线**：基于基准版本发起申请，自动留存版本差异（added/removed/moved/paramChanges）。**两级审批**（1 场站 → 2 运检中心）：只完成一半时保持 `PENDING` 停留待处理，不生成新版本；任一级驳回即结案；终审先对拟改内容做发布前校验，通过才落审批记录、生成新版本并发布——校验被拦时申请仍停留待处理，条件解除后可重新终审。
- **可追溯**：航线创建/版本发布/任务改绑/改线审批/禁飞区划定与取消均写入 `t_audit_event`；禁飞区只取消不删除，全量（含已取消、已过期）可查。

### 接口一览

```
POST   /api/routes                            新建航线（含首版本草稿）
GET    /api/routes/{id}                       航线详情（含全部版本）
GET    /api/routes/by-turbine/{turbineId}     风机航线列表
POST   /api/routes/{id}/versions              新增版本草稿
GET    /api/routes/versions/{vid}             版本详情（含航点串，历史版本可查）
POST   /api/routes/versions/{vid}/check       发布前校验（试算，返回冲突明细）
POST   /api/routes/versions/{vid}/publish     发布（校验通过才生效，未起飞任务改绑）
POST   /api/no-fly-zones                      划设禁飞区（临时/永久）
GET    /api/no-fly-zones                      全量列表（?activeOnly=true 只看当前生效）
POST   /api/no-fly-zones/{id}/cancel          取消（不删除，保留追溯）
POST   /api/flight-tasks                      创建飞行任务（绑定当前发布版本）
POST   /api/flight-tasks/{id}/takeoff         起飞（版本自此锁定）
POST   /api/flight-tasks/{id}/complete        完成
POST   /api/flight-tasks/{id}/cancel          取消（仅未起飞）
POST   /api/route-changes                     临时改线申请（留版本差异）
POST   /api/route-changes/{id}/approve        审批当前级次（两级；半批停留待处理）
GET    /api/route-changes/{id}                详情（拟改航点+版本差异+审批记录）
GET    /api/audit-events                      追溯查询（entityType + entityId）
```

巡检工单创建（`POST /api/inspection-orders`）新增可选字段 `flightTaskId`（任务须已起飞，自动带出航线版本）与 `routeVersionId`。

### 验证

- `FlightRouteFlowTest`：航线配置与发布、禁飞区冲突坐标与原因、风速/电量校验、禁飞区生效窗口、新版本只作用未起飞任务、历史影像引用原航线。
- `RouteChangeApprovalTest`：两级审批半批停留待处理、版本差异留存、任一级驳回结案、终审遇禁飞区被拦后停留待处理并可重试、航线与禁飞区审计追溯、历史版本退役后航点可查。

## F3 叶片缺陷管理

在 F1 缺陷登记基础上新增缺陷分类处置（schema 版本 `f3-1`）：裂纹、雷击、结冰、涂层剥落按等级配置处置时限；巡检员在影像上标注位置、尺寸与截图；专家复核可升级或降级但不覆盖初判；已关闭缺陷只能追加说明；影像标注可反查缺陷分类、原单、处理人与异常原因。

### 数据表

| 表 | 说明 |
| --- | --- |
| `t_defect` | 新增 `defect_type`、`initial_level`（初判，复核不覆盖）、`repair_advice`、`responsible_person`、`dispose_deadline` |
| `t_defect_dispose_rule` | 处置时限配置：缺陷类型 × 等级唯一，处置时限（小时）+ 维修建议模板；预置 4×4 共 16 条 |
| `t_defect_annotation` | 影像标注：位置、尺寸、截图；冗余 `order_id` 便于反查原单 |
| `t_defect_review` | 专家复核记录：from_level → to_level、复核意见、责任人 |
| `t_defect_note` | 追加说明：只增不改，已关闭缺陷唯一允许的写操作 |

### 业务规则

- **登记**：缺陷必须带分类（`CRACK/LIGHTNING/ICING/COATING_PEELING`）与初判等级；按 类型×等级 配置生成处置截止时间（登记时刻 + 时限）与初始维修建议；未配置的组合拒绝登记。
- **影像标注**：巡检员标注位置、尺寸与截图；未指定影像地址时取工单登记的无人机影像；已关闭缺陷不允许标注。
- **专家复核**：仅待维修（`OPEN`）缺陷可复核；复核等级必须与当前等级不同（构成升级或降级）；**初判等级 `initial_level` 永不覆盖**；等级变化后重新生成维修建议、按新时限从登记时刻重算处置截止时间，并记录责任人；每次复核留痕并写审计事件。
- **已关闭（`ACCEPTED`）**：复核、标注、维修全部拒绝，唯一允许的写操作是追加说明。
- **反查**：从影像标注定位缺陷分类（类型 + 初判/当前等级）、原单（工单号、风机）、处理人（标注人/最近复核人/责任人）与异常原因（缺陷描述 + 最近复核意见）。

### 接口一览

```
GET    /api/defect-rules                    处置时限配置列表
PUT    /api/defect-rules/{id}               调整处置时限与建议模板
POST   /api/defects/{id}/annotations        影像标注（位置、尺寸、截图）
POST   /api/defects/{id}/review             专家复核（升/降级，不覆盖初判）
POST   /api/defects/{id}/notes              追加说明（已关闭唯一允许的写操作）
GET    /api/defects/{id}                    缺陷详情（标注+复核记录+说明）
GET    /api/annotations/{id}/trace          反查：分类/原单/处理人/异常原因
```

缺陷登记（`POST /api/inspection-orders/{id}/defects`）新增必填字段 `defectType`；`defectLevel` 作为初判等级登记。

### 验证

- `DefectManagementTest`：时限配置预置与调整、登记生成时限与建议、影像标注落库、复核升降级（初判不变/建议重生成/责任人记录/时限重算/同级拒绝）、已关闭只能追加说明、标注反查三要素、未配置组合拒绝登记。

## F4 高空作业排班

在 F1-F3 基础上新增高空作业排班（schema 版本 `f4-1`）：读取风速、降雨和雷电天气窗口，结合人员资质、装备可用性与维修时长生成候选班次；窗口收窄自动预警但不静默改期；作业开始前二次确认天气；延期保留原计划与通知回执；过期资质人员不能被排入任务。

### 数据表

| 表 | 说明 |
| --- | --- |
| `t_worker` | 人员：资质类型 + 有效期；过期资质不能被排入任务 |
| `t_equipment` | 装备：可用状态 + 可用窗口 `[available_from, available_to)`，空表示不限 |
| `t_weather_window` | 天气窗口：风速/降雨/雷电；同一风机的重叠窗口归并为一条，补传/补录/重试递增 `revision` |
| `t_maintenance_task` | 高空作业任务：资质要求 + 人数 + 装备类型 + 维修时长；状态 `PLANNING/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED` |
| `t_work_shift` | 班次：候选/确认/作业中/完成/被取代/取消；`original_start/end` 固化原计划 |
| `t_shift_assignment` | 班次人员/装备分配 |
| `t_weather_confirmation` | 作业前二次确认：每次一条，开工以最近一次 `PASS` 为准 |
| `t_shift_postpone` | 延期记录：每次一条，保留新旧计划并关联通知回执 |
| `t_shift_notification` | 通知回执：窗口收窄预警 / 延期通知；冗余 `task_id` 便于追溯 |

### 业务规则

- **天气窗口幂等归并**：同一风机的重叠窗口为同一逻辑窗口；补传、补录和重试只更新该条记录（起止与读数以最新上报为准，`revision` 递增），"风速窗口与高空作业排班"只形成一条可用结果。
- **适窗条件**：最大风速 ≤ 10.0 m/s、无降雨、雷电风险 `NONE`（`WeatherWindowService` 常量）。
- **候选班次**：班次 = 适窗窗口起点起、维修时长的一段，须落在窗口内；人员须在职、资质类型匹配且**有效期覆盖整个班次**（过期资质不能被排入）；装备须可用且可用窗口覆盖整个班次。生成幂等：重新生成先清除上一轮落选班次再重建，不堆积重复候选；已确认班次的任务拒绝重新生成。
- **唯一可用结果**：每个任务至多一个确认班次；确认时其余候选自动取代（`SUPERSEDED`），重复确认幂等返回原结果；确认时复核人员资质，过期/离职拦截。
- **窗口收窄预警**：窗口归并更新后重新评估受影响班次，不再适窗的自动向值班调度发预警通知（留回执），**但系统不静默改期**——班次时刻保持不变，改期只能走显式延期。
- **二次确认**：开工前必须对当前天气二次确认，最近一次结果为 `PASS` 才允许开工。
- **延期**：仅已确认班次可延期；首次延期把当前计划固化为原计划（`original_start/end`，之后不再改变）；每次延期留存延期记录（新旧计划 + 原因 + 操作人）与通知回执（回执编号唯一）。

### 接口一览

```
POST   /api/weather-windows                     上报天气窗口（补传/补录/重试幂等归并）
GET    /api/weather-windows?turbineId=          窗口列表
POST   /api/workers                             人员登记（资质类型 + 有效期）
GET    /api/workers                             人员列表
POST   /api/equipment                           装备登记（可用窗口可空）
GET    /api/equipment                           装备列表
POST   /api/maintenance-tasks                   创建高空作业任务
GET    /api/maintenance-tasks/{id}              任务详情（含全部班次）
POST   /api/maintenance-tasks/{id}/generate-shifts  生成候选班次（幂等）
POST   /api/shifts/{id}/confirm                 确认班次（任务唯一可用结果）
POST   /api/shifts/{id}/confirm-weather         作业前二次确认天气
POST   /api/shifts/{id}/start                   开工（须二次确认 PASS）
POST   /api/shifts/{id}/complete                完工
POST   /api/shifts/{id}/postpone                延期（保留原计划与通知回执）
POST   /api/shifts/{id}/cancel                  取消（确认取消后任务回到排班中）
GET    /api/shifts/{id}                         班次详情（分配/确认/延期/回执）
```

### 验证

- `HighAltitudeSchedulingTest`：窗口补传/补录/重试只形成一条可用结果（`revision` 递增）、候选班次综合过滤（过期资质/类型不匹配/维保装备/窗口过短/条件不适窗均排除）、生成幂等不堆积、确认唯一且幂等、确认时资质过期拦截、窗口收窄与条件恶化自动预警且班次不变、二次确认未通过不能开工、延期保留原计划与通知回执（多次延期原计划不变）、取消确认后可重新生成。

## F5 风电叶片备件库存

在 F1-F4 基础上新增备件库存（schema 版本 `f5-1`）：叶片螺栓、传感器和涂层材料按序列号和批次入库；维修领用绑定风机和缺陷单；替换后记录旧件去向与新件状态；已安装备件不能再次出库；退库须经验收；库存、维修进度和资产档案在同一事务中更新；序列追踪规则调整只生成新版本，不改动旧追踪结论。

### 数据表

| 表 | 说明 |
| --- | --- |
| `t_spare_part` | 备件档案：序列号唯一、批次入库；状态 `IN_STOCK/ISSUED/RETURN_PENDING/INSTALLED/RETIRED` |
| `t_part_inbound` | 入库单：一批一单；记录入库时所用规则版本与追踪结论 |
| `t_part_requisition` | 维修领用单：绑定风机与缺陷单；状态 `ISSUED/INSTALLED/RETURNED` |
| `t_part_installation` | 装机台账（资产档案）：归属窗口 `[installed_at, removed_at)`，与叶片台账同一左闭右开口径；拆下时登记旧件去向 |
| `t_part_replacement` | 换件记录：旧件去向与新件状态的前后关系 |
| `t_part_return` | 退库单：退库须验收；状态 `PENDING/ACCEPTED/REJECTED` |
| `t_part_trace_rule` | 序列追踪规则：按备件类型版本化；预置 3 类备件各一个首版本 |

### 业务规则

- **入库**：按序列号和批次入库，序列号全局唯一（批次内也不得重复）；入库单与备件档案同事务落库，并按当前发布的规则版本生成追踪结论。
- **维修领用**：绑定风机与缺陷单（缺陷单须属于该风机且待维修）；仅在库备件可出库——**已安装（INSTALLED）备件不能再次出库**，已领用/待验收/已退役同样拦截。
- **换件**：旧件拆下（装机台账窗口闭合、登记去向）与新件装机（状态 `INSTALLED`）同事务完成；存在在装旧件时必须登记旧件去向；换件完成即回填维修结果推进维修进度（缺陷/工单 → `REPAIRED`）；`t_part_replacement` 保留旧件去向与新件状态的前后关系。**库存、维修进度和资产档案在同一事务中更新**，任一校验不过整体不生效。
- **退库**：仅已领用（未装机）备件可退库；退库须经验收——通过回到在库并闭环领用单，驳回退回已领用；已验收退库单不能重复验收。
- **规则版本化**：序列追踪规则调整只生成新版本（版本号递增并发布），旧版本退役但内容不改；已产生的入库/装机/换件结论继续引用旧版本，不随规则调整改变。

### 接口一览

```
POST   /api/spare-parts/inbound                 入库（按序列号和批次，生成追踪结论）
GET    /api/spare-parts                         库存列表（?partType=&status=）
GET    /api/spare-parts/{id}                    备件详情
GET    /api/spare-parts/{id}/trace              序列追踪：入库→领用→装机/退役全生命周期
POST   /api/spare-parts/requisitions            维修领用（绑定风机与缺陷单）
POST   /api/spare-parts/requisitions/{id}/replace  换件（旧件去向+新件状态，同事务）
POST   /api/spare-parts/requisitions/{id}/return   退库申请（进入待验收）
POST   /api/spare-parts/returns/{id}/accept     退库验收（pass=true 回库 / false 驳回）
GET    /api/part-rules                          规则全量版本（?partType= 过滤，含已退役）
POST   /api/part-rules/{partType}/versions      规则调整（只生成新版本）
```

### 验证

- `SparePartInventoryTest`：序列批次入库与重复序列号拦截、领用绑定风机与缺陷单、非在库拒绝再次出库、换件同事务更新库存/维修进度/资产档案并记录旧件去向与新件状态、缺旧件去向被拦截且三方状态均不变、已安装不能再次出库也不能退库、退库验收通过与驳回、规则调整只生成新版本且旧版本内容与旧结论不改动、序列追踪全生命周期可查。

## F6 叶片结冰风险预警

在 F1-F5 基础上新增叶片结冰风险预警（schema 版本 `f6-1`）：按温度、湿度、风速与历史振动计算风险等级；达到阈值生成停机建议并通知值班人员；人工确认后才执行停机，解除风险需再次确认；模型参数版本化，历史告警不随新参数重算；异常/缺失数据标记待补；风电叶片运维对象置为无效后，预警的页面、导出与后台查询同口径排除。

### 数据表

| 表 | 说明 |
| --- | --- |
| `t_icing_model_version` | 结冰风险模型参数：四因子阈值/满分/带宽、告警与危急阈值、停机建议模板；版本号递增，`PUBLISHED/RETIRED`；预置 v1 |
| `t_icing_monitor_data` | 监测数据：温度/湿度/风速/历史振动；`风机+监测时刻` 幂等，补传/补录/重试归并并递增 `revision`；缺失/异常为 `DATA_MISSING` 待补 |
| `t_icing_alert` | 结冰风险告警：固化读数、得分、等级与模型版本（冗余 version_no）；状态 `ACTIVE/SHUTDOWN_CONFIRMED/CLEARED`；记录两次确认人/时刻 |
| `t_icing_alert_confirmation` | 人工确认记录：停机确认、解除确认（含驳回）逐条留痕 |
| `t_icing_notification` | 值班通知回执：达到阈值通知值班人员，与告警同事务 |
| `t_turbine` | 新增 `run_status`（`ACTIVE/INVALID`）：运维对象有效标志，只置无效不删除 |

### 业务规则

- **四因子评分**（`IcingRiskEvaluator`）：温度低于阈值起算、湿度高于阈值起算（满分带宽内线性折算）；风速低于下限（静稳）或高于上限（携冻雨强风）取风速满分；历史振动相对基线的偏离比例折算；总分 0~100，按模型的告警阈值/危急阈值判 `WARNING/CRITICAL`。
- **阈值告警与通知**：达到阈值生成告警并固化停机建议，同事务通知值班人员（留回执）；一轮结冰过程（存在未闭环告警）只告一次，持续超阈值的重复上报不重复告警、不重复通知；闭环后再次进入结冰条件才产生新一轮告警。
- **两次人工确认**：系统只发建议——第一次人工确认后才置 `SHUTDOWN_CONFIRMED`（执行停机）；解除风险须第二次人工确认，确认通过才 `CLEARED` 闭环，解除可驳回（维持停机待查、填驳回原因、留痕）后再次确认。
- **模型参数版本化**：调整只生成新版本并发布，旧版本退役但内容不改；告警固化产生时的读数、得分、等级与 `model_version_id/version_no`，**历史告警不随新参数重算**；新上报数据按当前发布版本评分。
- **异常/缺失待补**：任一读数为 null 或超出物理量程，数据标记 `DATA_MISSING`、记录缺失字段、不评分不告警；补传按 `风机+时刻` 幂等归并，数据完整后重算（递增 `revision`），待补列表可查。
- **无效对象同口径**：`POST /api/turbines/{id}/deactivate` 将风机置为 `INVALID`（同时闭合服役窗口，档案不删除）。预警列表统一由 `IcingAlertService.listAlerts` 联表 `t_turbine`（`run_status='ACTIVE'`）产出，**页面 `/icing-alerts`、导出 `/icing-alerts/export`、后台 `/api/icing/alerts` 三处共用同一查询**；无效对象不再接受监测上报；历史告警档案仍可按编号追溯。

### 接口一览

```
POST   /api/icing/monitor-data                 上报监测数据并评估（缺失/异常返回 pendingSupplement）
GET    /api/icing/monitor-data?pending=true    监测数据（只看待补）
GET    /api/icing/alerts                       结冰风险预警列表（后台口径；?turbineId=&status=&riskLevel=&onlyOpen=）
GET    /api/icing/alerts/{id}                  告警详情（固化读数/模型版本+确认记录+通知回执）
POST   /api/icing/alerts/{id}/confirm-shutdown 第一次人工确认：确认后执行停机
POST   /api/icing/alerts/{id}/confirm-clear    第二次人工确认（?pass=true 解除 / false 驳回可再确认）
GET    /api/icing/model/versions               模型参数全量版本（含已退役）
POST   /api/icing/model/versions               参数调整（只生成新版本，历史告警不重算）
POST   /api/turbines/{id}/deactivate           运维对象置为无效（页面/导出/后台同口径排除）
GET    /icing-alerts                           叶片结冰风险预警页面
GET    /icing-alerts/export                    导出 CSV（与页面、后台同口径）
```

### 验证

- `IcingRiskWarningTest`：四因子评分与危急阈值、停机建议与值班通知回执、持续超阈值不重复告警、停机/解除两次人工确认（解除驳回可再确认、越级确认拦截）、模型调整只生成新版本且历史告警固化 v1 不重算（新数据按 v2 评分）、缺失/异常标记待补与补传幂等重算（revision 递增）、风机置无效后页面/导出/后台三口径一致排除且上报被拒、历史告警档案仍可追溯。
