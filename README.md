# SkillSync AI — Intelligent Team Building & Collaboration Platform

Turn ideas into teams. Turn skills into opportunities.

SkillSync AI is an intelligent student collaboration platform designed to reduce the friction of finding the right people with the right skills at the right time.

Instead of functioning as a traditional job board or networking site, SkillSync acts as an orchestration layer for student collaboration. Users can build professional profiles, create projects, discover suitable collaborators, inspect their experience, communicate in real time, form teams, and use AI-powered intelligence for skill and career insights.

This repository contains the Spring Boot backend responsible for authentication, domain orchestration, persistence, event processing, AI integrations, recommendations, real-time communication, and cloud infrastructure integration.

## Table of Contents

- [What Problem Does SkillSync Solve?](#what-problem-does-skillsync-solve)
- [Core Product Flow](#core-product-flow)
- [Key Features](#key-features)
- [Architecture](#architecture)
  - [Why a Modular Monolith?](#why-a-modular-monolith)
  - [Event-Driven Architecture](#event-driven-architecture)
  - [Kafka Integration](#kafka-integration)
  - [Redis Integration](#redis-integration)
  - [AI Matching Pipeline](#ai-matching-pipeline)
  - [Authentication & Security](#authentication--security)
  - [Real-Time Communication](#real-time-communication)
  - [Data & Persistence](#data--persistence)
- [Backend Modules](#backend-modules)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Local Development](#local-development)
- [Production Infrastructure](#production-infrastructure)
- [Engineering Principles](#engineering-principles)
- [Future Evolution](#future-evolution)
- [Project Status](#project-status)
- [Author](#author)
- [License](#license)

## What Problem Does SkillSync Solve?

Finding teammates for a college project, hackathon, research project, or startup idea is often harder than building the project itself.

**Students usually know:**
- what they want to build
- what skills they currently have
- what skills they are missing

**But they often don't know:**
- who has the required skills
- which candidates are actually suitable
- who has worked on similar projects
- what skills their team is missing
- how to approach potential collaborators
- how to coordinate after forming a team

SkillSync brings these capabilities into a single platform.

The platform combines:
**Skill Intelligence + Project Management + Intelligent Matching + Real-Time Communication + AI-Powered Analysis**
to create a complete team-building ecosystem.

## Core Product Flow

A typical SkillSync workflow looks like:

1. Create Account
2. Build Profile
3. Add Skills / Resume / Projects
4. Create Project
5. Define Required Skills
6. Discover Candidates
7. Heuristic Skill Filtering
8. AI Semantic Analysis
9. View Candidate Profiles
10. Message / Invite
11. Form Team
12. Collaborate in Real Time

The goal is to reduce the distance between:
*"I have an idea."*
and
*"I have the right team to build it."*

## Key Features

### 1. Intelligent User Profiles
Students can maintain structured professional profiles containing:
- Name
- University
- Major
- Graduation year
- Bio
- Skills
- Skill proficiency
- Resume
- Previous projects
- GitHub
- LinkedIn
- Portfolio
- Profile picture

Skills are represented as structured entities rather than simple strings, allowing them to be used by recommendation and matching workflows.

### 2. Project Creation
Users can create projects and define:
- Project title
- Description
- Category
- Required skills
- Team requirements
- Project metadata
- Project ownership

A project becomes a structured representation of team requirements rather than simply a text post.

### 3. Intelligent Candidate Discovery
SkillSync uses a multi-stage candidate discovery pipeline instead of sending every available user directly to an AI model.

**Stage 1 — Candidate Isolation**
Candidates are first restricted to the relevant student population, such as students belonging to the same college.

**Stage 2 — Heuristic Filtering**
Candidates are evaluated against the project's required skills using deterministic skill-overlap logic.
Only promising candidates continue to the expensive AI stage.

**Stage 3 — AI Semantic Analysis**
Shortlisted candidates can be analyzed using AI to determine deeper semantic compatibility.

*For example:*
Project Requirement: "Backend Development"
Candidate Skills: "Spring Boot + REST APIs + Hibernate"
A semantic model can recognize the relationship even when the exact strings do not match.

### 4. Candidate Profiles & Project History
Before collaborating with someone, users can inspect their public profile, including available:
- Current skills
- Skill proficiency
- Previous projects
- Team history
- GitHub
- LinkedIn
- Portfolio

### 5. Team Formation
SkillSync supports:
- Discovering potential teammates
- Viewing candidate profiles
- Sending collaboration requests
- Accepting or rejecting invitations
- Joining projects
- Viewing project leaders
- Viewing team members
- Managing team participation

### 6. Real-Time Messaging
SkillSync uses Spring WebSocket and STOMP for real-time communication.
This enables:
- New messages
- Team invitations
- Collaboration updates
- Notifications
- Background processing results
without requiring continuous frontend polling.

### 7. User Search
Users can search for other users using keywords such as:
- Name
- Skills
- University
- Projects
- Other searchable profile information

### 8. Resume Processing
Users can upload resumes that can be processed to extract:
- Skills
- Technologies
- Experience
- Projects
- Career-related information

PDF documents are processed using Apache PDFBox before downstream analysis.

### 9. AI-Powered Skill Intelligence
Potential AI capabilities include:
- Resume analysis
- Skill extraction
- Skill inference
- Skill-gap analysis
- Candidate compatibility analysis
- Career roadmaps
- Personalized guidance
- Project/team recommendations

The AI layer is isolated behind an AI gateway abstraction so the business layer does not depend directly on a specific AI provider.

## Architecture

SkillSync AI follows a **Modular Monolith** architecture using Spring Modulith.
The application runs as a single Spring Boot deployment while maintaining strong internal domain boundaries.

Major domains include:
- Authentication
- User Profile
- Projects
- Matching
- AI
- Messaging
- Notifications
- Todo / Roadmaps
- Resume Processing

The architecture gains many organizational benefits of microservices without prematurely introducing distributed-system complexity.

### Why a Modular Monolith?
SkillSync intentionally avoids splitting every feature into a separate microservice.
A modular monolith provides:
- Clear domain boundaries
- Controlled dependencies
- Easier local development
- Single deployment
- Lower infrastructure complexity
- Simpler debugging
- Easier database management

Strong module boundaries also leave room for individual modules to be extracted into services if scale eventually requires it.

### Event-Driven Architecture
Cross-module communication uses domain events whenever asynchronous communication makes sense.
Instead of tightly coupling modules through chains of direct calls, modules can publish events that other modules react to.

*Example:*
1. Resume Processed
2. Skill Extraction Event
3. AI Processing
4. Skill Updated Event
5. Profile / Matching

Spring Modulith application events are used for internal domain communication, while Kafka provides durable event streaming for workflows that benefit from broker-based asynchronous processing.

### Kafka Integration
Apache Kafka is used for asynchronous event-driven workflows.
Kafka is particularly useful when work should continue in the background after an HTTP request has completed.

*Example:*
1. Resume Uploaded
2. HTTP Request Completes
3. Resume Processing Event
4. Kafka
5. Background Consumer
6. PDF Parsing
7. AI Analysis
8. Profile Update

This prevents expensive resume-analysis work from unnecessarily occupying the original HTTP request.

Kafka can also support:
- AI processing jobs
- Recommendation generation
- Notification events
- Activity events
- Audit events
- Background document processing

### Redis Integration
Redis is used for low-latency and short-lived application data.
Use cases include:
- OTP storage
- Rate limiting
- Caching
- Temporary authentication data
- Frequently accessed profile information
- AI response caching
- Distributed coordination

Bucket4j can use Redis-backed state for rate limiting expensive operations such as AI requests.

### AI Matching Pipeline
The recommendation system uses a controlled funnel rather than sending every student directly to an AI model.

1. Project
2. Required Skills
3. College / User Filtering
4. Skill Overlap Heuristic
5. Top Candidates
6. AI Semantic Analysis
7. Compatibility Score
8. Recommendation

This provides two major advantages:
1. **Performance**: AI processing is limited to candidates that have already passed deterministic filtering.
2. **Cost Control**: Expensive AI inference is reserved for candidates that have a reasonable probability of being relevant.

### Authentication & Security
SkillSync uses Spring Security with a stateless authentication architecture.
Supported mechanisms include:
- Email/password authentication
- OTP-based verification
- Google OAuth2
- JWT authentication

After successful authentication, the backend issues a JWT for subsequent protected API requests. The stateless design avoids traditional server-side authentication sessions.

**OAuth2 Authentication**
Google OAuth2 is integrated through Spring Security OAuth2 Client. After successful OAuth authentication, the backend can issue a SkillSync JWT for the frontend to use when accessing protected endpoints.

**Rate Limiting**
Bucket4j can protect abuse-sensitive operations such as:
- AI endpoints
- Authentication endpoints
- OTP generation
- Search endpoints
- Messaging operations
Redis can be used as the shared backing store for rate-limit state.

### PostgreSQL & Data Modeling
PostgreSQL acts as the primary source of truth.
The backend uses:
- Spring Data JPA
- Hibernate
- PostgreSQL JSONB
- UUID-based identifiers
- Auditing
- Domain-specific entities

Relational modeling is used where strong relationships and querying are important.
JSONB is used for naturally hierarchical or flexible data, including certain AI-generated structures and roadmap information.

### Media & Document Processing
Cloudinary is used for media management such as:
- Profile images
- Uploaded media
- Other user-generated assets

Resume PDFs are processed using Apache PDFBox before being passed into downstream processing workflows.

### Real-Time Architecture
Messaging uses Spring WebSocket + STOMP.
Instead of repeatedly polling:
Frontend → Poll → Backend

the platform can push events directly:
Backend Event → WebSocket / STOMP → Connected Client

This is useful for messaging, notifications, team events, and asynchronous workflow updates.

## Backend Modules

- **Authentication**: Registration, login, OTP verification, password management, Google OAuth2, JWT generation, authentication and authorization.
- **User Profile**: Student profiles, skills, proficiency, resume references, social links, previous projects, and profile discovery.
- **Project**: Project creation, management, required skills, ownership, and team requirements.
- **Matching**: Candidate discovery, skill overlap, candidate filtering, recommendation generation, and compatibility processing.
- **AI**: Gemini integration, prompt construction, resume intelligence, skill analysis, semantic candidate matching, and AI-generated insights.
- **Messaging**: User-to-user communication, conversation management, real-time messaging, and WebSocket communication.
- **Notification**: Team invitations, collaboration events, system notifications, and background processing updates.
- **Todo / Roadmap**: AI-generated roadmaps, tasks, subtasks, checkpoints, and progress tracking.

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot |
| Architecture | Spring Modulith |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Cache / Key-Value | Redis |
| Event Streaming | Apache Kafka |
| Messaging | Spring Kafka |
| Security | Spring Security |
| Authentication | JWT + OAuth2 |
| Real-Time | Spring WebSocket + STOMP |
| AI | Google Gemini API |
| Resume Processing | Apache PDFBox |
| Media | Cloudinary |
| Rate Limiting | Bucket4j |
| JSON | Jackson |
| Build | Maven |
| Utilities | Lombok |
| Infrastructure | Docker / Managed Cloud Services |

## Project Structure

```
src/main/java/com/skillsync/skillsync_ai
├── authentication
├── userprofile
├── project
├── matching
├── ai
├── messaging
├── notification
├── todo
├── common
└── config
```

The module structure is intentionally aligned with business domains rather than technical layers alone.

## Configuration

Environment-specific values should be supplied through environment variables or an ignored `.env` file.

Typical configuration includes:
```
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

REDIS_HOST
REDIS_PORT
REDIS_PASSWORD

KAFKA_BOOTSTRAP_SERVERS
KAFKA_USERNAME
KAFKA_PASSWORD

JWT_SECRET

GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET

GEMINI_API_KEY

CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET

MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD

# Shared state backend: redis (default) | cacheify — routes OTP storage,
# OAuth2 exchange codes and rate limiting through one store layer
KV_STORE
CACHEIFY_BASE_URL
CACHEIFY_DB_ID
CACHEIFY_API_KEY
```

**Never commit API keys, JWT secrets, database passwords, Redis credentials, Kafka credentials, OAuth secrets, SMTP credentials, or private keys to source control.**

## Local Development

**Prerequisites**
- Java 21+
- Maven
- PostgreSQL
- Redis
- Apache Kafka
- Git

Clone the repository:
```bash
git clone <repository-url>
cd skillsync-ai
```

Configure the required environment variables.

**Run with Maven**

Linux / macOS:
```bash
./mvnw spring-boot:run
```

Windows:
```bash
.\mvnw.cmd spring-boot:run
```

The backend normally starts at:
`http://localhost:8080`

## Production Infrastructure

A possible production deployment is:

React Frontend
      │
      ▼
    Vercel
      │
      ▼
Spring Boot Backend
      │
      ├── PostgreSQL
      ├── Upstash Redis
      ├── Aiven Kafka
      ├── Cloudinary
      ├── Google Gemini
      └── Brevo SMTP

Managed infrastructure reduces the operational burden of running databases, brokers, caching infrastructure, media storage, email delivery, and AI services manually.

## Engineering Principles

- **Modular Design**: Domain responsibilities are separated into independently understandable modules.
- **Loose Coupling**: Cross-module communication favors interfaces and domain events over direct access to another module's implementation details.
- **Asynchronous Processing**: Long-running operations such as AI analysis and resume processing can be moved into background workflows.
- **Stateless Authentication**: JWT authentication allows protected requests without traditional server-side sessions.
- **Defense in Depth**: Authentication, authorization, validation, rate limiting, secure transport, and infrastructure controls work together.
- **Cost-Aware AI**: Deterministic filtering is performed before expensive AI inference.
- **Real-Time Communication**: WebSockets allow the backend to push important events to clients immediately.
- **Cloud-Native Infrastructure**: Managed PostgreSQL, Redis, Kafka, Cloudinary, email infrastructure, and AI services can be integrated without coupling the core business domain to a particular infrastructure provider.

## Future Evolution

Potential future improvements include:
- Vector-based semantic search
- Embedding-powered candidate matching
- Recommendation feedback loops
- Advanced team compatibility scoring
- Dedicated AI processing workers
- Distributed notification workers
- Event analytics
- Dedicated search infrastructure
- AI-powered project planning
- Automated skill verification
- Extraction of high-load modules into independent services

The current modular monolith provides a strong foundation for these capabilities while keeping development and deployment manageable.

## Project Status

SkillSync AI is being developed as a full-stack portfolio project focused on production-oriented backend engineering and modern system design.

The project demonstrates how a complex platform can combine:
**Domain Modularity + Event-Driven Architecture + AI + Real-Time Communication + Cloud Infrastructure**
within a maintainable backend architecture.

The primary engineering challenge is coordinating multiple workflows while keeping domain boundaries clear, expensive operations asynchronous, and the application responsive.

## Author

**Rahul Gheek**
Computer Science Engineering Student Java Developer

**Focus Areas**
- Java
- Spring Boot
- Spring Modulith
- System Design
- Distributed Systems
- PostgreSQL
- Kafka
- Redis
- AI Integration

## License

This project is developed for educational, portfolio, and demonstration purposes.
