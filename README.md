> 🚧 **Work in progress** — actively being developed. Core ticket workflow and AWS integration in progress.

# Factory Ticket System

A web-based order ticket management system for an internal factory environment. Workers at each production stage confirm tickets through a role-based interface. The system is event-driven using AWS SQS priority queues, ensuring rush orders are always processed first.

Built as a portfolio project demonstrating Spring Boot, AWS services, and event-driven architecture.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.6 / Java 21 (Amazon Corretto) |
| Frontend | React 18 + Vite (upgraded from Thymeleaf) |
| Auth (Dev) | Spring Security form login |
| Auth (Prod) | AWS Cognito hosted UI |
| Database (Dev) | DynamoDB Local (Docker) |
| Database (Prod) | AWS DynamoDB |
| Queues | AWS SQS FIFO — 15 queues across 5 stages |
| Notifications | AWS SNS |
| File Storage | AWS S3 — shipping labels and reports |
| Batch Jobs | Spring Batch + AWS EventBridge |
| Deployment | AWS Elastic Beanstalk |
| Container Registry | AWS ECR |
| Monitoring | AWS CloudWatch |
| Infrastructure | AWS CloudFormation |

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

### Option A — Local Machine

#### Prerequisites

- Java 21 (Amazon Corretto)
- Maven
- Node.js 20+
- Docker
- AWS CLI

#### 1. Start DynamoDB Local and create tables

`docker-compose.yml` handles everything automatically — starts DynamoDB Local and creates both tables on startup.

```bash
docker compose up -d
```

#### 2. Create properties file

Create `src/main/resources/application-dev.properties`:

```properties
amazon.dynamodb.endpoint=http://localhost:8000
aws.region=us-east-1
```

> For SQS, SNS, and S3 you will need real AWS credentials and queue URLs. See [AWS setup](#aws-setup) below.

#### 3. Run the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 4. Run the frontend dev server

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

#### 5. Open in browser

```
http://localhost:3000
```

> The Vite dev server proxies `/api`, `/login`, and `/logout` to the Spring Boot backend on port 8080. In production (`./mvnw package`), the React app is built automatically and bundled into the Spring Boot jar — no separate frontend process needed.

---

### Option B — GitHub Codespaces (recommended)

The `.devcontainer/devcontainer.json` fully automates the environment:

- Java 21 Corretto, Maven, Node.js, AWS CLI, and Docker pre-installed
- `application-dev.properties` created automatically
- DynamoDB Local starts automatically on every Codespace open
- Both DynamoDB tables created automatically via `docker-compose.yml`

No manual setup needed.

#### 1. Open Codespace

Go to the repo on GitHub → **Code → Codespaces → Create codespace on main**.

Wait for the environment to finish building — everything is set up automatically.

#### 2. Run the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 3. Run the frontend dev server

In a second terminal:

```bash
cd frontend && npm run dev
```

#### 4. Open in browser

Run this in the terminal to get your Vite URL:

```bash
echo "https://${CODESPACE_NAME}-3000.app.github.dev"
```

---

## AWS Setup

Required for SQS queues, SNS notifications, and S3 file storage.

#### Create SQS queues

```bash
bash scripts/create-queues.sh
```

Creates all 15 FIFO queues across 5 production stages (Rush, High, Normal per stage).

#### Configure AWS properties

Add your AWS credentials to `src/main/resources/application-dev.properties`. That file is listed in `.gitignore` and should never be committed.

---


## Project Structure

```
frontend/                           React + Vite frontend
├── src/
│   ├── api.js                      Fetch wrapper for all /api/* calls
│   ├── App.jsx                     React Router root
│   ├── index.css                   Global styles
│   ├── components/
│   │   ├── Navbar.jsx
│   │   ├── TicketRow.jsx           Role-based action buttons, inline panels
│   │   ├── CommentPanel.jsx        Chat history + new message form
│   │   └── FailReasonPanel.jsx     QC rejection form
│   └── pages/
│       ├── LoginPage.jsx
│       └── TicketsPage.jsx
├── vite.config.js                  Dev proxy (:3000 → :8080), prod build → static/
└── package.json

src/main/java/com/factoryapp/
├── config/
│   ├── AwsConfig.java              DynamoDB, SQS, SNS, S3 beans
│   └── SecurityConfig.java         Dev form login / Prod OAuth2
├── controller/
│   ├── ApiController.java          REST API — /api/me, /api/tickets, actions, comments
│   ├── SpaController.java          Forwards all non-API routes to index.html
│   └── TicketController.java       Legacy form-POST endpoints (backward compat)
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

src/main/resources/static/          React production build output (auto-generated)
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
| Credentials | Local properties file | IAM role on Elastic Beanstalk |
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