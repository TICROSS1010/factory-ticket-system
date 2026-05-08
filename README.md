> 🚧 **Work in progress** — actively being developed. Core ticket workflow and AWS integration in progress.
# Factory Ticket System

A web-based order ticket management system for an internal factory environment. Workers at each production stage confirm tickets through a role-based interface. The system is event-driven using AWS SQS priority queues, ensuring rush orders are always processed first.

Built as a portfolio project demonstrating Spring Boot, AWS services, and event-driven architecture.

---

## Tech Stack

| Layer | Technology                               |
|-------|------------------------------------------|
| Backend | Spring Boot 4.x / Java 21                |
| Frontend | Thymeleaf + CSS                          |
| Auth (Dev) | Spring Security form login               |
| Auth (Prod) | AWS Cognito hosted UI                    |
| Database (Dev) | DynamoDB Local                           |
| Database (Prod) | AWS DynamoDB                             |
| Queues | AWS SQS FIFO — 15 queues across 5 stages |
| Notifications | AWS SNS                                  |
| File Storage | AWS S3 — shipping labels and reports     |
| Batch Jobs | Spring Batch + AWS EventBridge           |
| Deployment | AWS Elastic Beanstalk                    |
| Container Registry | AWS ECR                                  |
| Monitoring | AWS CloudWatch                           |
| Infrastructure | AWS CloudFormation                       |

---

## How It Works

Orders enter the system from an external source and move through five worker stages:

```
External System → Sales → Line Worker → Quality → Packer → Shipping → Delivered
```

Each stage uses three SQS FIFO queues — Rush, High, and Normal. The queue poller always checks Rush first, then High, then Normal. When a worker confirms a ticket:

1. DynamoDB order record updated with new stage
2. History entry appended to orderHistory table
3. Message deleted from current SQS queue
4. Message sent to next stage queue
5. SNS notifies the next worker

If Quality fails an order it goes back to Line Worker as Rush priority regardless of its original priority.

---

## Worker Roles

| Role | Actions |
|------|---------|
| Sales | Confirm or reject incoming orders |
| Line Worker | Start and end production |
| Quality | Pass or fail completed orders |
| Packer | Confirm order is packed |
| Shipping | Upload shipping label, confirm shipped |

All roles share one screen — buttons change based on the logged-in user's role.

---

## Running Locally

### Prerequisites

- Java 21
- Maven
- DynamoDB Local
- AWS CLI
- Docker (optional — only needed if running DynamoDB Local via Docker)

### 1. Start DynamoDB Local

```bash
docker-compose up
```

Or if running the DynamoDB Local jar directly:

```bash
java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb
```

### 2. Create DynamoDB tables

```bash
aws dynamodb create-table --table-name orders --attribute-definitions AttributeName=orderId,AttributeType=S --key-schema AttributeName=orderId,KeyType=HASH --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000
```

```bash
aws dynamodb create-table --table-name orderHistory --attribute-definitions AttributeName=orderId,AttributeType=S AttributeName=timestamp,AttributeType=S --key-schema AttributeName=orderId,KeyType=HASH AttributeName=timestamp,KeyType=RANGE --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000
```

### 3. Create SQS Queues

Requires AWS CLI configured with your credentials.

```bash
bash scripts/create-queues.sh
```

Creates all 15 FIFO queues across 5 production stages (Rush, High, Normal per stage).

### 4. Run the app

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. Open in browser

```
http://localhost:8080
```

### Dev login credentials

| Username | Password | Role |
|----------|----------|------|
| sales | test | Sales |
| line | test | Line Worker |
| quality | test | Quality |
| packer | test | Packer |
| shipping | test | Shipping |

---

## Project Structure

```
src/main/java/com/factoryapp/
├── config/
│   ├── AwsConfig.java              DynamoDB, SQS, SNS, S3 beans
│   └── SecurityConfig.java         Dev form login / Prod OAuth2
├── controller/
│   └── TicketController.java       Single controller for all roles
├── service/
│   ├── OrderService.java           Stage transition logic
│   ├── QueueService.java           SQS poll, send, delete
│   └── NotificationService.java    SNS notifications
├── repository/
│   ├── OrderRepository.java        DynamoDB orders table
│   └── OrderHistoryRepository.java DynamoDB orderHistory table
├── model/
│   ├── Order.java
│   ├── OrderHistory.java
│   ├── Priority.java               Enum: RUSH / HIGH / NORMAL
│   ├── Stage.java                  Enum: SALES / LINE_WORKER / QUALITY / PACKER / SHIPPING / DELIVERED
│   └── Action.java                 Enum: CONFIRMED / REJECTED / STARTED / COMPLETED / PASSED / FAILED etc.
└── poller/
    └── StageQueuePoller.java       Polls Rush then High then Normal per stage
```

---

## DynamoDB Schema

### orders table

| Attribute | Type | Key |
|-----------|------|-----|
| orderId | String | PK |
| customer | String | |
| priority | String | RUSH / HIGH / NORMAL |
| orderType | String | |
| quantity | Number | |
| currentStage | String | |
| returnCount | Number | |
| returnReason | String | |
| createdAt | String | ISO 8601 |
| dueDate | String | ISO 8601 |
| notes | String | |
| assignedTo | String | |
| updatedAt | String | ISO 8601 |

### orderHistory table

| Attribute | Type | Key |
|-----------|------|-----|
| orderId | String | PK |
| timestamp | String | SK |
| stage | String | |
| workerId | String | |
| action | String | |
| notes | String | |

---

## Environments

| | Dev | Prod |
|-|-----|------|
| Login | Spring Security form login | Cognito hosted UI |
| Database | DynamoDB Local (localhost:8000) | AWS DynamoDB |
| Credentials | Dummy keys in properties file | IAM role on Elastic Beanstalk |
| Activate | `spring.profiles.active=dev` | `SPRING_PROFILES_ACTIVE=prod` |

---

## Future Phases

- **Phase 2** — Admin dashboard with full order history and quota reports
- **Phase 3** — Per-item pass/fail quality control with partial order splits
- **Phase 4** — Product types and inventory tracking
- **Phase 5** — Defect tracking and root cause analysis

---

## Documentation

Full system design and architecture document available in `/docs/factory-system-design-v3.docx`