# Graph Report - .  (2026-07-11)

## Corpus Check
- 254 files · ~95,043 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2221 nodes · 3765 edges · 165 communities (135 shown, 30 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 150 edges (avg confidence: 0.79)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- User Service
- RabbitMQ Messaging
- User Service
- Security Annotations
- Admin Category API
- Admin Category API
- Scheduled Jobs
- User Service
- User Service
- Message Queue Service
- User Service
- Category Service
- Admin Category API
- SKU Service
- Frontend Tooling
- Admin Category API
- Admin Logistics API
- Admin Order API
- Uniapp Config
- Admin Category API
- Admin Category API
- Admin Logistics API
- User Service
- Uniapp Pages
- Admin Order API
- Redis Config
- Admin SKU API
- Data Entities
- Data Entities
- Admin Order API
- Security Annotations
- Request Response VO
- User Service
- Exception Handling
- Request Response VO
- Admin Product API
- Hardware Mall
- Redis Caching
- Uniapp Pages
- Data Entities
- User Service
- Security Annotations
- Uniapp Pages
- Hardware Mall
- User Service
- Logistics Service
- Unit Tests
- Admin Controller
- Admin Order API
- Request Response VO
- User Service
- Hardware Mall
- Dcloudio Uni
- Admin SPU API
- Uniapp Pages
- Redis Caching
- Docker Deployment
- Frontend Tooling
- Order Service
- Uniapp Pages
- Uniapp Pages
- Uniapp Pages
- Uniapp Pages
- Security Annotations
- Admin Order API
- Unit Tests
- Admin Order API
- System Constants
- Category Service
- Skuservice Hardware
- Uniapp Pages
- Hardware Mall
- Hardware Mall
- Admin Controller
- File Service
- Unit Tests
- Uniapp Pages
- Uniapp Pages
- Uniapp Pages
- Unit Tests
- Frontend Tooling
- Admin User API
- Data Entities
- Data Entities
- Mapper Tests
- Uniapp Pages
- Mqmessagemapper Hardware
- Hardware Mall
- Hardware Mall
- Spectemplateservice Hardware
- Uniapp Pages
- Redis Caching
- Admin Controller
- Frontend Tooling
- Scheduled Jobs
- Addressservice Hardware
- Specitemservice Hardware
- Hardware Mall
- Hardware Mall
- Hardware Mall
- Scheduled Jobs
- Hardware Mall
- Opencode Skills
- Alert Service
- Hardwaremallapplication Hardware
- Regions Hardware
- Data Entities
- Skumapper Hardware
- Hardware Mall
- Hardware Mall
- Uniapp Pages
- Admin Order API
- Interceptors
- Cartmapper Hardware
- Hardware Mall
- Hardware Mall
- Object Storage
- Hardware Mall
- Uniapp Pages
- Tab Bar Icons
- Object Storage
- Hardware Mall
- Data Entities
- Data Entities
- Data Entities
- Data Entities
- Addressmapper Hardware
- Categorymapper Hardware
- Orderitemmapper Hardware
- Paymentrecordmapper Hardware
- Spectemplatemapper Hardware
- Category Icons
- Hardware Mall
- Hardware Mall
- Data Entities
- Data Entities
- Data Entities
- Tab Bar Icons
- Hardware Mall
- Hardware Mall
- Hardware Mall
- Hardware Mall
- Hardware Mall
- Hardware Mall
- Unit Tests
- Documentation
- Documentation
- WeChat Payment
- Documentation
- Documentation
- Documentation
- Documentation
- Docker Deployment
- Documentation
- Default Avatar
- Pkg Hardware

## God Nodes (most connected - your core abstractions)
1. `Result` - 119 edges
2. `RedisUtil` - 42 edges
3. `OrderServiceImpl` - 38 edges
4. `SkuServiceImpl` - 35 edges
5. `OrderServiceImplTest` - 34 edges
6. `SkuService` - 22 edges
7. `SpuServiceImpl` - 22 edges
8. `SpuServiceImplTest` - 20 edges
9. `MqMessageService` - 19 edges
10. `MqMessageServiceImpl` - 19 edges

## Surprising Connections (you probably didn't know these)
- `Admin Management Frontend Entry HTML` --semantically_similar_to--> `Uni-app Mini Program Entry HTML`  [INFERRED] [semantically similar]
  hardware-mall-admin/index.html → hardware-mall-uniapp/index.html
- `Admin Management Frontend Entry HTML` --semantically_similar_to--> `Uni-app Source Entry HTML`  [INFERRED] [semantically similar]
  hardware-mall-admin/index.html → hardware-mall-uniapp/src/index.html
- `生产级落地执行计划书` --conceptually_related_to--> `五金商城系统`  [INFERRED]
  docs/PRODUCTION_DEPLOYMENT_PLAN.md → README.md
- `安全加固` --semantically_similar_to--> `安全加固实施计划`  [INFERRED] [semantically similar]
  docs/PRODUCTION_DEPLOYMENT_PLAN.md → docs/superpowers/plans/2026-05-06-security-hardening.md
- `Hardware Mall Mini Program Design Spec v2.0` --conceptually_related_to--> `Uni-app Mini Program Entry HTML`  [INFERRED]
  docs/superpowers/specs/2026-03-31-hardware-mall-design.md → hardware-mall-uniapp/index.html

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Apifox API Test Suite** — apifox_test_sets_01_auth_auth_module_test, apifox_test_sets_02_category_category_module_test, apifox_test_sets_03_product_product_module_test, apifox_test_sets_04_cart_cart_module_test, apifox_test_sets_05_order_order_module_test, apifox_test_sets_06_address_address_module_test, apifox_test_sets_07_logistics_logistics_module_test [INFERRED 0.85]
- **Security Hardening Implementation Tasks** — docs_superpowers_plans_2026_05_06_security_hardening_env_variable_migration, docs_superpowers_plans_2026_05_06_security_hardening_jwt_util_getrolefromtoken, docs_superpowers_plans_2026_05_06_security_hardening_requireadmin_annotation, docs_superpowers_plans_2026_05_06_security_hardening_admin_role_interceptor, docs_superpowers_plans_2026_05_06_security_hardening_global_exception_handler_fix, docs_superpowers_plans_2026_05_06_security_hardening_cors_env_fix [INFERRED 0.85]
- **Spring Boot Profile Configuration Group** — hardware_mall_backend_src_main_resources_application, hardware_mall_backend_src_main_resources_application_dev, hardware_mall_backend_src_test_resources_application_test [INFERRED 0.85]
- **SPU SKU Spec Model Document Cluster** — docs____________, docs_______, docs__________ [INFERRED 0.85]
- **Frontend Application Entry Points** — hardware_mall_admin_index, hardware_mall_uniapp_index, hardware_mall_uniapp_src_index [INFERRED 0.75]
- **Hardware Mall Category Icons** — hardware_mall_uniapp_src_static_category_glue_glue, hardware_mall_uniapp_src_static_category_light_light, hardware_mall_uniapp_src_static_category_lock_lock, hardware_mall_uniapp_src_static_category_tool_tool [INFERRED 0.85]
- **Tab Bar Navigation Icons** — hardware_mall_uniapp_src_static_tabbar_cart_active, hardware_mall_uniapp_src_static_tabbar_cart, hardware_mall_uniapp_src_static_tabbar_category_active [INFERRED 0.85]
- **Tab Bar Navigation Icons** — hardware_mall_uniapp_src_static_tabbar_category, hardware_mall_uniapp_src_static_tabbar_home, hardware_mall_uniapp_src_static_tabbar_home_active, hardware_mall_uniapp_src_static_tabbar_user, hardware_mall_uniapp_src_static_tabbar_user_active [INFERRED 0.85]

## Communities (165 total, 30 thin omitted)

### Community 0 - "User Service"
Cohesion: 0.06
Nodes (22): Claims, Override, Page, RequiredArgsConstructor, Service, Slf4j, User, UserMapper (+14 more)

### Community 1 - "RabbitMQ Messaging"
Cohesion: 0.06
Nodes (38): Binding, Channel, ConnectionFactory, DirectExchange, Bean, Configuration, Slf4j, RabbitMQConfig (+30 more)

### Community 2 - "User Service"
Cohesion: 0.08
Nodes (23): Override, Page, RequiredArgsConstructor, Service, Sku, SkuMapper, Slf4j, SpecItemMapper (+15 more)

### Community 3 - "Security Annotations"
Cohesion: 0.07
Nodes (35): HandlerInterceptor, Documented, Retention, Target, RateLimit, Documented, Retention, Target (+27 more)

### Community 4 - "Admin Category API"
Cohesion: 0.07
Nodes (32): createSku(), deleteSku(), generateSkus(), getSkusBySpu(), Sku, SpecVO, updateSku(), uploadProductImage() (+24 more)

### Community 5 - "Admin Category API"
Cohesion: 0.08
Nodes (32): createSpecItem(), createSpecTemplate(), deleteSpecItem(), deleteSpecTemplate(), getSpecItemList(), getSpecTemplateList(), SpecItem, SpecTemplate (+24 more)

### Community 6 - "Scheduled Jobs"
Cohesion: 0.09
Nodes (23): Component, OrderService, RequiredArgsConstructor, Scheduled, Slf4j, OrderCancelStaleJob, Mapper, Select (+15 more)

### Community 7 - "User Service"
Cohesion: 0.10
Nodes (18): StatusConstants, Override, Page, RequiredArgsConstructor, Service, SpecTemplate, SpecTemplateMapper, SpecTemplateServiceImpl (+10 more)

### Community 8 - "User Service"
Cohesion: 0.10
Nodes (21): ActiveProfiles, BeforeEach, SkuMapper, SpringBootTest, Sql, Test, Transactional, SkuMapperTest (+13 more)

### Community 9 - "Message Queue Service"
Cohesion: 0.12
Nodes (12): AlertService, MqMessage, Override, RequiredArgsConstructor, Service, Slf4j, MqMessageServiceImpl, MqMessage (+4 more)

### Community 10 - "User Service"
Cohesion: 0.12
Nodes (19): ConditionalOnProperty, Config, Bean, Configuration, WechatPayConfig, OrderMapper, Override, PaymentRecord (+11 more)

### Community 11 - "Category Service"
Cohesion: 0.14
Nodes (15): CategoryService, CategoryServiceImpl, Category, CategoryMapper, Override, Page, RequiredArgsConstructor, Service (+7 more)

### Community 12 - "Admin Category API"
Cohesion: 0.11
Nodes (18): AdminSpecController, DeleteMapping, GetMapping, Page, PostMapping, PutMapping, RequestMapping, RequireAdmin (+10 more)

### Community 13 - "SKU Service"
Cohesion: 0.12
Nodes (18): AddressMapper, ApplicationEventPublisher, CartMapper, CartService, DashboardStatsVO, LogisticsMapper, OrderItemMapper, OrderMapper (+10 more)

### Community 14 - "Frontend Tooling"
Cohesion: 0.07
Nodes (29): compilerOptions, allowImportingTsExtensions, baseUrl, isolatedModules, jsx, lib, module, moduleResolution (+21 more)

### Community 15 - "Admin Category API"
Cohesion: 0.10
Nodes (20): AdminCategoryController, CategoryService, DeleteMapping, GetMapping, Page, PostMapping, PutMapping, RequestMapping (+12 more)

### Community 16 - "Admin Logistics API"
Cohesion: 0.10
Nodes (20): AdminLogisticsController, DeleteMapping, GetMapping, LogisticsService, Page, PostMapping, PutMapping, RequestMapping (+12 more)

### Community 17 - "Admin Order API"
Cohesion: 0.09
Nodes (26): getLogisticsList(), getOrderList(), refundOrder(), shipOrder(), clearUserFilter(), confirmShip(), currentOrder, detailVisible (+18 more)

### Community 18 - "Uniapp Config"
Cohesion: 0.07
Nodes (28): changeOrigin, target, app-plus, usingComponents, appid, description, port, proxy (+20 more)

### Community 19 - "Admin Category API"
Cohesion: 0.10
Nodes (23): Category, CategoryQuery, createCategory(), deleteCategory(), getCategoryList(), updateCategory(), CATEGORY_STATUS_TEXT, confirmSubmit() (+15 more)

### Community 20 - "Admin Category API"
Cohesion: 0.15
Nodes (14): Data, Result, AdminSpuController, CategoryService, DeleteMapping, GetMapping, Page, PostMapping (+6 more)

### Community 21 - "Admin Logistics API"
Cohesion: 0.12
Nodes (21): createLogistics(), deleteLogistics(), Logistics, LogisticsQuery, updateLogistics(), updateLogisticsStatus(), confirmSubmit(), dialogVisible (+13 more)

### Community 22 - "User Service"
Cohesion: 0.18
Nodes (10): Override, Page, ProductDetailVO, ProductListVO, RequiredArgsConstructor, Service, Slf4j, Spu (+2 more)

### Community 23 - "Uniapp Pages"
Cohesion: 0.08
Nodes (17): appStore, currentImages, currentSku, currentSkuPrice, currentSkuStock, detailData, isFavorite, isSpecAvailable() (+9 more)

### Community 24 - "Admin Order API"
Cohesion: 0.14
Nodes (12): PostMapping, PutMapping, PostMapping, RequestMapping, RequireAdmin, RequiredArgsConstructor, RestController, UploadController (+4 more)

### Community 25 - "Redis Config"
Cohesion: 0.14
Nodes (11): Bean, Configuration, RedisConfig, BeforeEach, RedisUtil, SpringBootTest, Test, RedisIntegrationTest (+3 more)

### Community 26 - "Admin SKU API"
Cohesion: 0.13
Nodes (15): AdminSkuController, DeleteMapping, GetMapping, Page, PostMapping, PutMapping, RequestMapping, RequireAdmin (+7 more)

### Community 27 - "Data Entities"
Cohesion: 0.15
Nodes (14): AddressService, AddressController, DeleteMapping, GetMapping, JwtUtil, PostMapping, PutMapping, RequestMapping (+6 more)

### Community 28 - "Data Entities"
Cohesion: 0.15
Nodes (13): CartService, CartController, DeleteMapping, GetMapping, JwtUtil, PostMapping, PutMapping, RequestMapping (+5 more)

### Community 29 - "Admin Order API"
Cohesion: 0.13
Nodes (17): getUserList(), updateUserRegion(), updateUserStatus(), User, UserQuery, editingUserId, handleRegionSubmit(), handleToggleStatus() (+9 more)

### Community 30 - "Security Annotations"
Cohesion: 0.19
Nodes (12): GetMapping, JwtUtil, PostMapping, PutMapping, RateLimit, RedisUtil, RequestMapping, RequiredArgsConstructor (+4 more)

### Community 31 - "Request Response VO"
Cohesion: 0.14
Nodes (6): CreateOrderRequest, DashboardStatsVO, OrderVO, Page, RecentOrderVO, OrderService

### Community 32 - "User Service"
Cohesion: 0.15
Nodes (16): Address, AddressMapper, ApplicationEventPublisher, BeforeEach, CartMapper, CartService, ExtendWith, Logistics (+8 more)

### Community 33 - "Exception Handling"
Cohesion: 0.20
Nodes (9): BindException, ExceptionHandler, BusinessException, GlobalExceptionHandler, Slf4j, HttpServletRequest, MethodArgumentNotValidException, MethodArgumentTypeMismatchException (+1 more)

### Community 34 - "Request Response VO"
Cohesion: 0.19
Nodes (12): CreateOrderRequest, GetMapping, JwtUtil, OrderService, OrderVO, Page, PostMapping, PutMapping (+4 more)

### Community 35 - "Admin Product API"
Cohesion: 0.11
Nodes (19): API限流, CI/CD 流水线, 容器化部署, 数据库优化, 库存乐观锁, 多环境配置, 生产级落地执行计划书, 安全加固 (+11 more)

### Community 36 - "Hardware Mall"
Cohesion: 0.17
Nodes (11): RequestMapping, RequiredArgsConstructor, RestController, SkuService, SpecItemService, SpecTemplateService, SpuService, ProductController (+3 more)

### Community 37 - "Redis Caching"
Cohesion: 0.12
Nodes (4): Component, RequiredArgsConstructor, Slf4j, RedisUtil

### Community 38 - "Uniapp Pages"
Cohesion: 0.13
Nodes (11): cancelOrder(), confirmReceive(), currentTab, loading, loadMore(), loadOrders(), noMore, orders (+3 more)

### Community 39 - "Data Entities"
Cohesion: 0.22
Nodes (13): BaseTypeHandler, CallableStatement, Data, SpecVO, Override, SpecVOTypeHandler, JdbcType, MappedJdbcTypes (+5 more)

### Community 40 - "User Service"
Cohesion: 0.22
Nodes (10): Cart, CartItemVO, CartServiceImpl, CartMapper, Override, RequiredArgsConstructor, Service, SkuMapper (+2 more)

### Community 41 - "Security Annotations"
Cohesion: 0.17
Nodes (13): ConditionalOnBean, JwtUtil, PostMapping, RateLimit, RequestMapping, RequiredArgsConstructor, RestController, Slf4j (+5 more)

### Community 42 - "Uniapp Pages"
Cohesion: 0.11
Nodes (11): addresses, buyerRemark, cartStore, freightAmount, goodsAmount, isDirectBuy, orderItems, payAmount (+3 more)

### Community 43 - "Hardware Mall"
Cohesion: 0.20
Nodes (16): getMockProductImages(), MOCK_ADDRESSES, MOCK_BANNERS, MOCK_CART, MOCK_CATEGORIES, MOCK_ORDERS, MOCK_PRODUCTS, MOCK_USER_INFO (+8 more)

### Community 44 - "User Service"
Cohesion: 0.29
Nodes (8): AddressServiceImpl, Address, AddressMapper, Override, RequiredArgsConstructor, Service, Transactional, UserMapper

### Community 45 - "Logistics Service"
Cohesion: 0.26
Nodes (7): Logistics, LogisticsMapper, Override, Page, RequiredArgsConstructor, Service, LogisticsServiceImpl

### Community 46 - "Unit Tests"
Cohesion: 0.19
Nodes (5): Page, ProductDetailVO, ProductListVO, Spu, SpuService

### Community 47 - "Admin Controller"
Cohesion: 0.16
Nodes (7): LoginData, LoginResult, UploadResult, app, router, routes, request

### Community 48 - "Admin Order API"
Cohesion: 0.20
Nodes (10): AdminOrderController, GetMapping, OrderService, OrderVO, Page, PutMapping, RequestMapping, RequireAdmin (+2 more)

### Community 49 - "Request Response VO"
Cohesion: 0.22
Nodes (10): GetMapping, Page, RequestMapping, RequiredArgsConstructor, RestController, SkuService, SpecTemplateService, SpuService (+2 more)

### Community 51 - "Hardware Mall"
Cohesion: 0.12
Nodes (15): Address, CartItem, Category, Logistics, Order, OrderItem, Product, ProductDetail (+7 more)

### Community 52 - "Dcloudio Uni"
Cohesion: 0.13
Nodes (15): @dcloudio/uni-app, @dcloudio/uni-app-plus, @dcloudio/uni-components, @dcloudio/uni-h5, @dcloudio/uni-mp-weixin, dependencies, @dcloudio/uni-app, @dcloudio/uni-app-plus (+7 more)

### Community 53 - "Admin SPU API"
Cohesion: 0.17
Nodes (13): createSpu(), deleteSpu(), getSpuList(), Spu, SpuQuery, updateSpu(), updateSpuStatus(), confirmSubmit() (+5 more)

### Community 54 - "Uniapp Pages"
Cohesion: 0.13
Nodes (5): appStore, loaded, manageMode, selectedCount, selectedIds

### Community 55 - "Redis Caching"
Cohesion: 0.23
Nodes (10): Async, Getter, StockSyncEvent, Component, RedisUtil, RequiredArgsConstructor, SkuService, Slf4j (+2 more)

### Community 56 - "Docker Deployment"
Cohesion: 0.33
Nodes (12): check_forked(), command_exists(), deprecation_notice(), do_install(), echo_docker_as_nonroot(), is_darwin(), is_dry_run(), is_wsl() (+4 more)

### Community 57 - "Frontend Tooling"
Cohesion: 0.14
Nodes (14): devDependencies, typescript, unplugin-auto-import, unplugin-vue-components, vite, @vitejs/plugin-vue, vue-tsc, vite (+6 more)

### Community 58 - "Order Service"
Cohesion: 0.21
Nodes (6): CreateOrderRequest, Component, RequiredArgsConstructor, Slf4j, RedisLockUtil, RedissonClient

### Community 59 - "Uniapp Pages"
Cohesion: 0.14
Nodes (4): appStore, cartStore, isSelectAll, manageMode

### Community 60 - "Uniapp Pages"
Cohesion: 0.18
Nodes (11): categories, currentCategory, loadCategories(), loading, loadMore(), loadProducts(), noMore, page (+3 more)

### Community 61 - "Uniapp Pages"
Cohesion: 0.18
Nodes (12): currentSort, loading, loadMore(), loadProducts(), noMore, onSearch(), onSort(), page (+4 more)

### Community 62 - "Uniapp Pages"
Cohesion: 0.16
Nodes (10): hasSearched, historyKeywords, hotKeywords, keyword, loaded, loading, onHistoryClick(), onSearch() (+2 more)

### Community 63 - "Security Annotations"
Cohesion: 0.27
Nodes (10): AdminRoleInterceptor, CorsRegistry, Configuration, RequiredArgsConstructor, WebMvcConfig, InterceptorRegistry, JwtInterceptor, Override (+2 more)

### Community 64 - "Admin Order API"
Cohesion: 0.24
Nodes (10): DashboardStatsVO, DashboardController, GetMapping, OrderService, RequestMapping, RequireAdmin, RequiredArgsConstructor, RestController (+2 more)

### Community 65 - "Unit Tests"
Cohesion: 0.24
Nodes (13): Project Background Document, Database Design Document V2.0, Project Planning Document, API Interface Specification V2.0, SPU + SKU + Spec Template Database Model, Hardware Mall Mini Program Design Spec v2.0, Production Deployment Design, Admin Management Frontend Entry HTML (+5 more)

### Community 66 - "Admin Order API"
Cohesion: 0.21
Nodes (9): DashboardStats, getDashboardStats(), getRecentOrders(), RecentOrder, currentTime, loadDashboardData(), quickActions, recentOrders (+1 more)

### Community 67 - "System Constants"
Cohesion: 0.15
Nodes (12): CATEGORY_STATUS, LOGISTICS_STATUS, LOGISTICS_STATUS_TEXT, ORDER_STATUS, ORDER_STATUS_TEXT, ORDER_STATUS_TYPE, PRODUCT_STATUS, PRODUCT_STATUS_TEXT (+4 more)

### Community 68 - "Category Service"
Cohesion: 0.32
Nodes (6): Override, RequiredArgsConstructor, Service, SpecItem, SpecItemMapper, SpecItemServiceImpl

### Community 69 - "Skuservice Hardware"
Cohesion: 0.23
Nodes (4): Page, Sku, SpecVO, SkuService

### Community 70 - "Uniapp Pages"
Cohesion: 0.15
Nodes (5): appStore, loaded, manageMode, selectedCount, selectedIds

### Community 71 - "Hardware Mall"
Cohesion: 0.17
Nodes (12): axios, element-plus, @element-plus/icons-vue, dependencies, axios, element-plus, @element-plus/icons-vue, vue (+4 more)

### Community 72 - "Hardware Mall"
Cohesion: 0.29
Nodes (9): BaseMapper, Mapper, LogisticsMapper, Mapper, SpecItem, SpecItemMapper, Mapper, SpuMapper (+1 more)

### Community 73 - "Admin Controller"
Cohesion: 0.23
Nodes (9): RedisConstants, AdminAuthController, JwtUtil, RateLimit, RedisUtil, RequestMapping, RequireAdmin, RequiredArgsConstructor (+1 more)

### Community 74 - "File Service"
Cohesion: 0.26
Nodes (8): MultipartFile, Override, RequiredArgsConstructor, Service, OssServiceImpl, MultipartFile, OssService, OssProperties

### Community 75 - "Unit Tests"
Cohesion: 0.27
Nodes (3): Logistics, Page, LogisticsService

### Community 76 - "Uniapp Pages"
Cohesion: 0.17
Nodes (5): categories, CategoryItem, displayCategories, loading, products

### Community 78 - "Uniapp Pages"
Cohesion: 0.17
Nodes (3): formData, saving, userStore

### Community 79 - "Unit Tests"
Cohesion: 0.36
Nodes (11): API 测试报告, 购物车订单 500 错误, phones JSON 字段问题, 完整购买流程测试, 01-认证模块测试, 02-分类模块测试, 03-商品模块测试, 04-购物车模块测试 (+3 more)

### Community 80 - "Frontend Tooling"
Cohesion: 0.18
Nodes (11): @dcloudio/types, @dcloudio/uni-automator, @dcloudio/uni-cli-shared, @dcloudio/vite-plugin-uni, devDependencies, @dcloudio/types, @dcloudio/uni-automator, @dcloudio/uni-cli-shared (+3 more)

### Community 81 - "Admin User API"
Cohesion: 0.27
Nodes (9): AdminUserController, GetMapping, Page, RequestMapping, RequireAdmin, RequiredArgsConstructor, RestController, User (+1 more)

### Community 82 - "Data Entities"
Cohesion: 0.36
Nodes (8): Data, TableName, Spu, Data, Sku, SpecItem, SpecTemplate, ProductDetailVO

### Community 83 - "Data Entities"
Cohesion: 0.25
Nodes (4): CartItemVO, Data, CartService, Cart

### Community 84 - "Mapper Tests"
Cohesion: 0.31
Nodes (7): CategoryMapperTest, ActiveProfiles, CategoryMapper, SpringBootTest, Sql, Test, Transactional

### Community 85 - "Uniapp Pages"
Cohesion: 0.18
Nodes (4): addresses, isManaging, loaded, selectMode

### Community 86 - "Mqmessagemapper Hardware"
Cohesion: 0.38
Nodes (5): Delete, Mapper, MqMessage, Select, MqMessageMapper

### Community 87 - "Hardware Mall"
Cohesion: 0.20
Nodes (9): name, private, scripts, build, dev, lint, preview, type (+1 more)

### Community 88 - "Hardware Mall"
Cohesion: 0.20
Nodes (6): currentPageTitle, isCollapsed, menuItems, route, router, theme

### Community 89 - "Spectemplateservice Hardware"
Cohesion: 0.33
Nodes (3): Page, SpecTemplate, SpecTemplateService

### Community 90 - "Uniapp Pages"
Cohesion: 0.22
Nodes (6): avatarUrl, canLogin, loginLoading, navigateBack(), nickname, onGetPhoneNumber()

### Community 91 - "Redis Caching"
Cohesion: 0.36
Nodes (7): CommandLineRunner, Component, Override, RedisUtil, RequiredArgsConstructor, Slf4j, StockWarmupRunner

### Community 92 - "Admin Controller"
Cohesion: 0.25
Nodes (8): login(), form, formRef, handleLogin(), loading, rememberMe, router, rules

### Community 93 - "Frontend Tooling"
Cohesion: 0.22
Nodes (8): compilerOptions, allowSyntheticDefaultImports, composite, module, moduleResolution, skipLibCheck, include, vite.config.ts

### Community 94 - "Scheduled Jobs"
Cohesion: 0.39
Nodes (7): Component, RedisUtil, RequiredArgsConstructor, Scheduled, SkuService, Slf4j, StockSyncRetryJob

### Community 97 - "Hardware Mall"
Cohesion: 0.22
Nodes (8): name, private, scripts, build:h5, build:mp-weixin, dev:h5, dev:mp-weixin, version

### Community 99 - "Hardware Mall"
Cohesion: 0.36
Nodes (8): emit, isFocus, onBlur(), onClear(), onFocus(), onInput(), onSearch(), Props

### Community 100 - "Scheduled Jobs"
Cohesion: 0.43
Nodes (6): Component, MqMessageService, RequiredArgsConstructor, Scheduled, Slf4j, MqMessageCleanupJob

### Community 101 - "Hardware Mall"
Cohesion: 0.32
Nodes (7): checkEnv(), ci, fs, main(), path, preparePrivateKey(), PROJECT_PATH

### Community 102 - "Opencode Skills"
Cohesion: 0.25
Nodes (7): plugin, $schema, skills, paths, nextlevelbuilder/ui-ux-pro-max-skill, .opencode/skills, superpowers@git+https://github.com/obra/superpowers.git

### Community 103 - "Alert Service"
Cohesion: 0.43
Nodes (5): AlertService, Override, Service, Slf4j, LogAlertServiceImpl

### Community 104 - "Hardwaremallapplication Hardware"
Cohesion: 0.48
Nodes (5): EnableAsync, EnableScheduling, HardwareMallApplication, MapperScan, SpringBootApplication

### Community 105 - "Regions Hardware"
Cohesion: 0.29
Nodes (6): allCities, City, cityOptions, Province, provinceCityOptions, regions

### Community 106 - "Data Entities"
Cohesion: 0.43
Nodes (5): Data, TableName, User, Mapper, UserMapper

### Community 107 - "Skumapper Hardware"
Cohesion: 0.48
Nodes (4): Mapper, Sku, Update, SkuMapper

### Community 112 - "Admin Order API"
Cohesion: 0.33
Nodes (3): Order, OrderItem, OrderQuery

### Community 113 - "Interceptors"
Cohesion: 0.53
Nodes (4): Bean, Configuration, MybatisPlusConfig, MybatisPlusInterceptor

### Community 114 - "Cartmapper Hardware"
Cohesion: 0.53
Nodes (4): CartMapper, Cart, Mapper, Update

### Community 116 - "Hardware Mall"
Cohesion: 0.53
Nodes (5): decrease(), emit, increase(), onBlur(), Props

### Community 117 - "Hardware Mall"
Cohesion: 0.40
Nodes (4): displayPrice, emit, onClick(), Props

### Community 118 - "Object Storage"
Cohesion: 0.70
Nodes (4): Component, ConfigurationProperties, Data, OssProperties

### Community 119 - "Hardware Mall"
Cohesion: 0.40
Nodes (4): decimalPart, integerPart, priceValue, Props

### Community 121 - "Tab Bar Icons"
Cohesion: 0.50
Nodes (5): Tab Bar Category Icon, Tab Bar Home Icon (Inactive), Tab Bar Home Icon (Active), Tab Bar User Icon (Inactive), Tab Bar User Icon (Active)

### Community 122 - "Object Storage"
Cohesion: 0.83
Nodes (3): EnableConfigurationProperties, Configuration, OssConfig

### Community 123 - "Hardware Mall"
Cohesion: 0.50
Nodes (3): ComponentCustomProperties, GlobalComponents, vue

### Community 124 - "Data Entities"
Cohesion: 0.67
Nodes (3): Data, TableName, Order

### Community 125 - "Data Entities"
Cohesion: 0.67
Nodes (3): Data, TableName, OrderItem

### Community 126 - "Data Entities"
Cohesion: 1.00
Nodes (3): CartItem, CreateOrderRequest, Data

### Community 127 - "Data Entities"
Cohesion: 0.83
Nodes (3): Data, OrderItem, OrderVO

### Community 128 - "Addressmapper Hardware"
Cohesion: 0.83
Nodes (3): AddressMapper, Address, Mapper

### Community 129 - "Categorymapper Hardware"
Cohesion: 0.83
Nodes (3): CategoryMapper, Category, Mapper

### Community 130 - "Orderitemmapper Hardware"
Cohesion: 0.83
Nodes (3): Mapper, OrderItem, OrderItemMapper

### Community 131 - "Paymentrecordmapper Hardware"
Cohesion: 0.83
Nodes (3): Mapper, PaymentRecordMapper, PaymentRecord

### Community 132 - "Spectemplatemapper Hardware"
Cohesion: 0.83
Nodes (3): Mapper, SpecTemplate, SpecTemplateMapper

### Community 133 - "Category Icons"
Cohesion: 1.00
Nodes (4): Glue Category Icon, Light Category Icon, Lock Category Icon, Tool Category Icon

### Community 134 - "Hardware Mall"
Cohesion: 0.50
Nodes (3): FavoriteItem, FootprintItem, useAppStore

### Community 135 - "Hardware Mall"
Cohesion: 0.67
Nodes (3): pinia, pinia, pinia

### Community 141 - "Tab Bar Icons"
Cohesion: 0.67
Nodes (3): Cart Tab Icon, Active Cart Tab Icon, Active Category Tab Icon

## Knowledge Gaps
- **361 isolated node(s):** `vue`, `GlobalComponents`, `ComponentCustomProperties`, `name`, `version` (+356 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Result` connect `Admin Category API` to `Admin Order API`, `Exception Handling`, `Request Response VO`, `Admin Controller`, `Security Annotations`, `Admin Category API`, `Admin Category API`, `Admin Logistics API`, `Admin Order API`, `Admin User API`, `Request Response VO`, `Admin Order API`, `Admin SKU API`, `Data Entities`, `Data Entities`, `Security Annotations`?**
  _High betweenness centrality (0.110) - this node is a cross-community bridge._
- **Why does `RedisUtil` connect `Redis Caching` to `User Service`, `User Service`, `User Service`, `Category Service`, `SKU Service`, `Logistics Service`, `User Service`, `Redis Config`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `OrderService` connect `Request Response VO` to `SKU Service`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `SkuServiceImpl` (e.g. with `.testCartesianProduct_2x2()` and `.testCartesianProduct_3x2()`) actually correct?**
  _`SkuServiceImpl` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `vue`, `GlobalComponents`, `ComponentCustomProperties` to the rest of the system?**
  _374 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `User Service` be split into smaller, more focused modules?**
  _Cohesion score 0.05734767025089606 - nodes in this community are weakly interconnected._
- **Should `RabbitMQ Messaging` be split into smaller, more focused modules?**
  _Cohesion score 0.06487434248977206 - nodes in this community are weakly interconnected._