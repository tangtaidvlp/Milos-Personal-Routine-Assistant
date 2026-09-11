# System Architecture Context: Personal Routine Manager

## 1. Project Overview
* **Application:** Personal Routine Manager (Agentic Application)
* **Core Paradigm:** A hybrid application combining traditional CRUD UI with a conversational AI "Chat-UI" (Routine Buddy) to manage personal schedules.
* **Architecture Style:** Decoupled Three-Tier Architecture deployed on AWS.

## 2. Infrastructure & Hosting (AWS)
The application is decoupled into three distinct layers to ensure scalability and independent deployments:

* **Frontend (Presentation Tier):**
  * Hosted on **AWS S3** (Static Website Hosting) and distributed globally via **AWS CloudFront** (CDN).
* **Backend (Application Tier):**
  * Containerized via **Docker** and stored in **AWS ECR** (Elastic Container Registry).
  * Hosted on a single **AWS EC2** instance (or AWS ECS with Fargate for serverless container management).
* **Database (Data Tier):**
  * Hosted on **AWS RDS (PostgreSQL)** to ensure automated backups, persistence, and decoupling from the EC2 instance.

## 3. Technology Stack
* **Frontend:** React / HTML5. Consumes APIs via modern `fetch`.
* **Backend:** Java 25 with **Spring Boot 3.x**.
* **Security:** Spring Security with stateless **JWT Authentication**.
* **Database Access:** Spring Data JPA / Hibernate.
* **AI Integration:** **Spring AI** (`spring-ai-starter-
* **CI/CD Host:** **Github Action

