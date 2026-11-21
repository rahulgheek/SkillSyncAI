# Roadmap Sessions API

This document describes the backend contract for AI roadmap sessions. All
endpoints require a valid JWT and operate only on the authenticated user's
data.

Base path: `/api/v1/roadmaps/sessions`

## Concepts

- **Roadmap session** — one AI-generated learning journey. A user can create
  and keep multiple sessions.
- **Roadmap** — a curriculum path inside a session. A session can contain more
  than one roadmap.
- **Checkpoint** — a milestone/topic inside a roadmap.
- **Task** — a saved to-do generated from a checkpoint. Tasks always include
  their subtasks.
- **lastViewedAt** — updated when a session is opened. The dashboard uses it to
  show the most recently viewed session.

## Create a session

### Custom session

`POST /api/v1/roadmaps/sessions`

```json
{
  "title": "Backend journey",
  "targetRole": "Backend Developer",
  "currentKnowledge": "Basic Java and SQL",
  "knowledgeGaps": "Spring Security, testing, deployment"
}
```

All fields are optional. If `targetRole` is omitted the AI recommends a path
based on the user's profile and recent chats.

### Session from a skill-gap analysis

`POST /api/v1/roadmaps/sessions/from-skill-gap/{skillGapId}`

or include `skillGapId` in the body of the custom endpoint:

```json
{
  "skillGapId": "2f0f7e6e-3f26-4d6e-9b5e-6b3a8f2a1c11"
}
```

When `skillGapId` is present the other free-text fields are ignored.

### Response

`201 Created`

```json
{
  "success": true,
  "message": "AI roadmap session created successfully",
  "data": {
    "id": "a3b1c2d3-...",
    "title": "Path to Backend Developer",
    "targetRole": "Backend Developer",
    "description": "AI-generated custom roadmap based on your profile and chats.",
    "status": "ACTIVE",
    "lastViewedAt": "2026-11-21T10:15:30Z",
    "createdAt": "2026-11-21T10:15:30Z",
    "updatedAt": "2026-11-21T10:15:30Z",
    "progress": {
      "roadmapCount": 1,
      "checkpointCount": 4,
      "completedCheckpoints": 0,
      "taskCount": 4,
      "completedTasks": 0,
      "progressPercentage": 0
    },
    "roadmaps": [
      {
        "id": "b4c2d3e4-...",
        "title": "Path to Backend Developer",
        "description": "...",
        "status": "ACTIVE",
        "currentTopic": "Spring Boot fundamentals",
        "completedTopics": [],
        "curriculum": ["Spring Boot fundamentals", "Testing", "Deployment"],
        "createdAt": "2026-11-21T10:15:30Z",
        "checkpoints": [
          {
            "id": "c5d3e4f5-...",
            "title": "Learn: Build a REST controller",
            "description": "...",
            "orderIndex": 1,
            "status": "PENDING",
            "dueDate": "2026-11-22",
            "completedAt": null,
            "subtasks": [
              {
                "id": "0b6f...",
                "title": "Read the Spring MVC docs",
                "completed": false
              }
            ],
            "learningResources": [
              {
                "title": "Spring Boot Tutorial for Beginners",
                "channel": "freeCodeCamp.org",
                "url": "https://www.youtube.com/watch?v=...",
                "type": "YOUTUBE_VIDEO",
                "description": "A popular full-length introduction to the topic.",
                "duration": "1h 20m"
              }
            ]
          }
        ]
      }
    ],
    "tasks": [
      {
        "id": "d6e4f5a6-...",
        "title": "Learn: Build a REST controller",
        "description": "...",
        "status": "PENDING",
        "priority": "HIGH",
        "dueDate": "2026-11-22T10:15:30Z",
        "completedAt": null,
        "relatedEntityId": "c5d3e4f5-...",
        "roadmapSessionId": "a3b1c2d3-...",
        "sourceTitle": "Roadmap: Path to Backend Developer | Topic: Spring Boot fundamentals",
        "createdAt": "2026-11-21T10:15:30Z",
        "subtasks": [
          {
            "id": "0b6f...",
            "title": "Read the Spring MVC docs",
            "completed": false
          }
        ],
        "learningResources": [
          {
            "title": "Spring Boot Tutorial for Beginners",
            "channel": "freeCodeCamp.org",
            "url": "https://www.youtube.com/watch?v=...",
            "type": "YOUTUBE_VIDEO",
            "description": "A popular full-length introduction to the topic.",
            "duration": "1h 20m"
          }
        ]
      }
    ]
  }
}
```

## List sessions

`GET /api/v1/roadmaps/sessions?page=0&size=20&status=ACTIVE`

Returns a `Page` of session summaries. Supported query parameters:

| Parameter | Description |
|---|---|
| `page` | Zero-based page index |
| `size` | Page size |
| `status` | `ACTIVE`, `COMPLETED`, `ARCHIVED`, `FAILED` |
| `sort` | Spring Data sort, for example `updatedAt,desc` |

## Dashboard: most recently viewed session

`GET /api/v1/roadmaps/sessions/recent`

Alias: `GET /api/v1/ai/dashboard/recent-roadmap-session`

Returns the session with the newest `lastViewedAt`. If the user has never
opened a session, the most recently created session is returned. If the user
has no sessions, `data` is `null` with a `200 OK` response.

## Open a session

`GET /api/v1/roadmaps/sessions/{sessionId}`

Returns the full workspace (roadmaps, checkpoints, tasks, subtasks) and updates
`lastViewedAt`. Opening a session is what makes it the dashboard's most recent
session.

## Mark a session viewed without loading it

`POST /api/v1/roadmaps/sessions/{sessionId}/view`

Useful when the frontend renders a session card and wants the dashboard to
reflect the view without downloading the full detail payload.

## Get session tasks

`GET /api/v1/roadmaps/sessions/{sessionId}/tasks?status=PENDING`

Returns the saved tasks for the session, including every subtask. The global
task list also supports a session filter:

`GET /api/v1/todos?sessionId={sessionId}&status=PENDING`

## Update or archive a session

`PATCH /api/v1/roadmaps/sessions/{sessionId}`

```json
{
  "title": "Renamed journey",
  "status": "ARCHIVED"
}
```

Both fields are optional. Archive instead of deleting so historical progress
and tasks remain available.

## Task and subtask endpoints

- `PATCH /api/v1/todos/{todoId}/status?status=COMPLETED`
- `PATCH /api/v1/todos/{todoId}/subtasks/{subtaskId}/toggle`
- `GET /api/v1/todos?sessionId={sessionId}`

### Completion rules

- Toggling the last incomplete subtask automatically marks the task
  `COMPLETED` and records `completedAt`.
- A task with at least one incomplete subtask is never marked `COMPLETED`.
  `PATCH /status?status=COMPLETED` returns `400` with
  "Complete all subtasks before marking this task as completed."
- Unchecking a subtask on a completed task reopens it (`IN_PROGRESS` or
  `PENDING`) and clears `completedAt`.
- Reopening a task moves its checkpoint back to `PENDING` and reactivates a
  completed roadmap/session, so roadmap progress stays consistent.
- Tasks without subtasks (manually created to-dos) can still be completed
  directly.

Completing every checkpoint in a roadmap automatically generates the next
topic's tasks. Completing every roadmap in a session marks the session
`COMPLETED`.

## Learning materials

Every checkpoint and task includes a `learningResources` array:

```json
[
  {
    "title": "Spring Boot Tutorial for Beginners",
    "channel": "freeCodeCamp.org",
    "url": "https://www.youtube.com/watch?v=...",
    "type": "YOUTUBE_VIDEO",
    "description": "A popular full-length introduction to the topic.",
    "duration": "1h 20m"
  },
  {
    "title": "Spring Boot Reference Documentation",
    "channel": "Spring",
    "url": "https://docs.spring.io/spring-boot/",
    "type": "DOCUMENTATION",
    "description": "Official reference for the framework.",
    "duration": null
  }
]
```

The AI is asked for popular, widely watched YouTube videos from reputable
channels. Supported `type` values are `YOUTUBE_VIDEO`, `DOCUMENTATION`,
`ARTICLE`, `COURSE`, and `OTHER`.

Because a language model cannot guarantee that a specific video URL still
exists, the backend validates every link:

- a valid YouTube URL is kept as-is;
- a missing or non-YouTube "video" URL is replaced with a working YouTube
  search URL for the task title and topic;
- if the AI returns no resources at all, a YouTube search fallback is still
  added, so every task has at least one video link.

Generate or refresh materials for an existing checkpoint (for example a
roadmap created before this feature):

`POST /api/v1/roadmaps/checkpoints/{checkpointId}/learning-materials?refresh=true`

Without `refresh=true`, stored materials are returned and the AI is not called.
The updated list is also copied to the matching task automatically.

## Database migration

Apply `docs/migrations/2026-11-21-roadmap-sessions.sql` and
`docs/migrations/2026-11-22-roadmap-learning-resources.sql` before deploying.
The application runs with `spring.jpa.hibernate.ddl-auto=update`, so Hibernate
can also create the new columns, but the SQL files are the explicit production
path. The session migration backfills existing roadmaps and tasks into sessions
so old data continues to appear in the new UI.
