

# jzo2o 家政服务平台

## 项目简介

jzo2o 是一个专业的家政服务平台微服务架构系统，采用微服务架构设计，为家政服务行业提供完整的解决方案。系统支持服务人员管理、机构管理、订单调度、优惠券营销等功能，满足用户端、服务人员端、机构端和运营端的多方需求。

## 系统架构

### 模块说明

| 模块 | 说明 |
|------|------|
| jzo2o-api | API模块，提供Feign客户端接口用于服务间通信 |
| jzo2o-gateway | API网关，负责请求路由、认证和限流 |
| jzo2o-customer | 客户服务模块，管理用户、服务人员、机构等 |
| jzo2o-foundations | 基础服务模块，管理服务类型、服务项、区域等 |
| jzo2o-market | 营销服务模块，管理活动、优惠券等 |
| jzo2o-orders | 订单模块 |
| ├─ jzo2o-orders-base | 订单基础服务 |
| ├─ jzo2o-orders-dispatch | 订单调度服务 |
| └─ jzo2o-orders-history | 历史订单服务 |

### 技术栈

- **后端框架**: Spring Boot
- **微服务框架**: Spring Cloud
- **服务注册与发现**: Eureka/Nacos
- **API网关**: Spring Cloud Gateway
- **数据库**: MySQL
- **缓存**: Redis
- **搜索引擎**: Elasticsearch
- **消息队列**: RabbitMQ
- **分布式事务**: Seata
- **定时任务**: XXL-Job

## 核心功能

### 用户端 (C端)
- 用户注册登录
- 地址簿管理
- 服务浏览与搜索
- 订单下单与支付
- 优惠券领取与使用
- 评价服务

### 服务人员/机构端 (B端)
- 服务人员/机构注册认证
- 服务技能设置
- 服务范围设置
- 抢单/派单接单
- 订单服务与完成
- 收入提现

### 运营端
- 区域管理
- 服务类型/服务项管理
- 订单管理
- 优惠券活动管理
- 服务人员/机构认证审核
- 数据统计

## 项目结构

```
jzo2o/
├── jzo2o-api/                    # API接口定义
├── jzo2o-gateway/               # 网关服务
├── jzo2o-customer/             # 客户服务中心
├── jzo2o-foundations/           # 基础服务中心
├── jzo2o-market/                # 营销服务中心
└── jzo2o-orders/               # 订单中心
    ├── jzo2o-orders-base/       # 订单基础
    ├── jzo2o-orders-dispatch/  # 订单调度
    └── jzo2o-orders-history/   # 历史订单
```

## 快速开始

### 环境要求

- JDK 11+
- Maven 3.6+
- MySQL 5.7+
- Redis 5.0+
- Elasticsearch 7.x
- RabbitMQ 3.8+

### 构建项目

```bash
# 克隆项目
git clone https://gitee.com/Vincent_HWW/jzo2o.git

# 进入项目目录
cd jzo2o

# 构建所有模块
mvn clean package -DskipTests
```

### 服务启动顺序

1. 启动 MySQL、Redis、Elasticsearch、RabbitMQ
2. 启动 jzo2o-gateway (网关)
3. 启动 jzo2o-foundations (基础服务)
4. 启动 jzo2o-customer (客户服务)
5. 启动 jzo2o-market (营销服务)
6. 启动 jzo2o-orders-* (订单服务)

### 配置说明

各模块配置文件位于 `src/main/resources/` 目录下：

- `bootstrap.yml` - 通用配置
- `bootstrap-dev.yml` - 开发环境配置
- `bootstrap-test.yml` - 测试环境配置
- `bootstrap-prod.yml` - 生产环境配置

## 核心接口示例

### 用户登录

```http
POST /customer/inner/common-user/login
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456"
}
```

### 获取服务列表

```http
GET /customer/serve/firstPageServeList?regionId=1
```

### 下单

```http
POST /orders-manager/inner/orders
Content-Type: application/json

{
  "serveId": 1,
  "serveAddress": "北京市朝阳区xxx",
  "contactsName": "张三",
  "contactsPhone": "13800138000",
  "serveStartTime": "2024-01-01 10:00:00"
}
```

### 抢券

```http
POST /consumer/coupon/seize
Content-Type: application/json

{
  "activityId": 1
}
```

## 订单状态流转

```
待支付(0) → 派单中(100) → 待服务(200) → 服务中(300) → 待评价(400) → 订单完成(500)
                                    ↓
                               订单取消(600) / 已退单(700)
```

## 调度策略

系统支持三种订单调度策略：

1. **距离优先策略** - 优先分配距离最近的服务人员
2. **评分优先策略** - 优先分配评分最高的服务人员
3. **最少接单优先策略** - 优先分配接单最少的服务人员

## 开发指南

### 添加新服务

1. 在对应模块的 `controller` 包下创建Controller
2. 在 `service` 包下创建Service接口和实现
3. 在 `mapper` 包下创建Mapper接口
4. 在 `model/domain` 包下创建实体类

### 添加Feign接口

在 `jzo2o-api` 模块中创建API接口：

```java
@FeignClient(contextId = "xxx", value = "jzo2o-xxx", path = "/xxx/inner/xxx")
public interface XxxApi {
    @GetMapping("/{id}")
    XxxResDTO findById(@PathVariable("id") Long id);
}
```

## 许可证

本项目仅供学习交流使用。

## 联系方式

如有问题，请提交 Issue。