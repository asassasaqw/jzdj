# jzo2o Home Services Platform

## Project Overview

jzo2o is a microservices-based professional home services platform designed to provide a comprehensive solution for the home services industry. The system supports functionalities such as service provider management, agency management, order scheduling, and coupon marketing, meeting the diverse needs of end users, service providers, agencies, and operations teams.

## System Architecture

### Module Description

| Module | Description |
|--------|-------------|
| jzo2o-api | API module providing Feign client interfaces for inter-service communication |
| jzo2o-gateway | API gateway responsible for request routing, authentication, and rate limiting |
| jzo2o-customer | Customer service module managing users, service providers, and agencies |
| jzo2o-foundations | Foundation service module managing service types, service items, regions, etc. |
| jzo2o-market | Marketing service module managing campaigns, coupons, etc. |
| jzo2o-orders | Order module |
| ├─ jzo2o-orders-base | Order foundation service |
| ├─ jzo2o-orders-dispatch | Order dispatch service |
| └─ jzo2o-orders-history | Historical order service |

### Technology Stack

- **Backend Framework**: Spring Boot  
- **Microservices Framework**: Spring Cloud  
- **Service Registration & Discovery**: Eureka/Nacos  
- **API Gateway**: Spring Cloud Gateway  
- **Database**: MySQL  
- **Cache**: Redis  
- **Search Engine**: Elasticsearch  
- **Message Queue**: RabbitMQ  
- **Distributed Transaction**: Seata  
- **Scheduled Tasks**: XXL-Job  

## Core Features

### User End (C-end)
- User registration and login  
- Address book management  
- Service browsing and search  
- Order placement and payment  
- Coupon claiming and usage  
- Service evaluation  

### Service Provider/Agency End (B-end)
- Service provider/agency registration and authentication  
- Service skill configuration  
- Service area configuration  
- Order picking and assignment  
- Service execution and completion  
- Income withdrawal  

### Operations End
- Region management  
- Service type and service item management  
- Order management  
- Coupon campaign management  
- Service provider/agency authentication review  
- Data statistics  

## Project Structure

```
jzo2o/
├── jzo2o-api/                    # API interface definitions
├── jzo2o-gateway/               # Gateway service
├── jzo2o-customer/             # Customer service center
├── jzo2o-foundations/           # Foundation service center
├── jzo2o-market/                # Marketing service center
└── jzo2o-orders/               # Order center
    ├── jzo2o-orders-base/       # Order foundation
    ├── jzo2o-orders-dispatch/  # Order dispatch
    └── jzo2o-orders-history/   # Historical orders
```

## Quick Start

### Environment Requirements

- JDK 11+
- Maven 3.6+
- MySQL 5.7+
- Redis 5.0+
- Elasticsearch 7.x
- RabbitMQ 3.8+

### Build Project

```bash
# Clone the project
git clone https://gitee.com/Vincent_HWW/jzo2o.git

# Enter project directory
cd jzo2o

# Build all modules
mvn clean package -DskipTests
```

### Service Startup Order

1. Start MySQL, Redis, Elasticsearch, RabbitMQ  
2. Start jzo2o-gateway (gateway)  
3. Start jzo2o-foundations (foundation service)  
4. Start jzo2o-customer (customer service)  
5. Start jzo2o-market (marketing service)  
6. Start jzo2o-orders-* (order services)  

### Configuration Details

Configuration files for each module are located under `src/main/resources/`:

- `bootstrap.yml` - General configuration  
- `bootstrap-dev.yml` - Development environment configuration  
- `bootstrap-test.yml` - Test environment configuration  
- `bootstrap-prod.yml` - Production environment configuration  

## Core Interface Examples

### User Login

```http
POST /customer/inner/common-user/login
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456"
}
```

### Retrieve Service List

```http
GET /customer/serve/firstPageServeList?regionId=1
```

### Place Order

```http
POST /orders-manager/inner/orders
Content-Type: application/json

{
  "serveId": 1,
  "serveAddress": "No. xxx, Chaoyang District, Beijing",
  "contactsName": "Zhang San",
  "contactsPhone": "13800138000",
  "serveStartTime": "2024-01-01 10:00:00"
}
```

### Claim Coupon

```http
POST /consumer/coupon/seize
Content-Type: application/json

{
  "activityId": 1
}
```

## Order Status Flow

```
Pending Payment (0) → Dispatching (100) → Awaiting Service (200) → In Service (300) → Awaiting Evaluation (400) → Completed (500)
                                    ↓
                              Cancelled (600) / Refunded (700)
```

## Dispatching Strategies

The system supports three order dispatching strategies:

1. **Distance Priority** – Assign to the closest service provider  
2. **Rating Priority** – Assign to the highest-rated service provider  
3. **Least Orders Priority** – Assign to the service provider with the fewest current orders  

## Development Guide

### Adding a New Service

1. Create a Controller under the `controller` package of the corresponding module  
2. Create a Service interface and implementation under the `service` package  
3. Create a Mapper interface under the `mapper` package  
4. Create an entity class under the `model/domain` package  

### Adding a Feign Interface

Create the API interface in the `jzo2o-api` module:

```java
@FeignClient(contextId = "xxx", value = "jzo2o-xxx", path = "/xxx/inner/xxx")
public interface XxxApi {
    @GetMapping("/{id}")
    XxxResDTO findById(@PathVariable("id") Long id);
}
```

## License

This project is for learning and communication purposes only.

## Contact

For any issues, please submit an Issue.