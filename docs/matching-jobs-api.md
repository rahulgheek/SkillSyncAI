# Matching jobs API

Matching generation is asynchronous. The request returns `202 Accepted` with a
job, and the client polls the job endpoint or waits for the STOMP notification.

## Project owner: rank candidates

```http
POST /api/v1/matching/dashboard/projects/{projectId}/generate
Authorization: Bearer <jwt>
```

`202 Accepted`

```json
{
  "success": true,
  "message": "Matching started. Poll the job or wait for the notification.",
  "data": {
    "id": "0f8f...",
    "type": "PROJECT_CANDIDATES",
    "status": "PENDING",
    "projectId": "5c1e...",
    "subjectId": "5c1e...",
    "candidateCount": 0,
    "aiCandidateCount": 0,
    "aiApplied": false,
    "error": null,
    "requestedAt": "2026-11-21T10:00:00Z",
    "startedAt": null,
    "finishedAt": null
  }
}
```

Read the ranked candidates (unchanged, database only):

```http
GET /api/v1/matching/dashboard/projects/{projectId}/recommendations?page=0&size=10
```

Poll a job:

```http
GET /api/v1/matching/dashboard/projects/{projectId}/recommendations/jobs/{jobId}
GET /api/v1/matching/dashboard/projects/{projectId}/recommendations/jobs/latest
```

## Student: rank projects

```http
POST /api/v1/matching/dashboard/me/recommendations/generate
```

`202 Accepted` with `type: "STUDENT_PROJECTS"`.

```http
GET /api/v1/matching/dashboard/me/recommendations
GET /api/v1/matching/dashboard/me/recommendations/jobs/{jobId}
GET /api/v1/matching/dashboard/me/recommendations/jobs/latest
```

## Job statuses

| Status | Meaning |
| --- | --- |
| `PENDING` | Queued. The worker has not claimed it yet. |
| `RUNNING` | Shortlisting, calling the LLM, or persisting. |
| `READY` | Finished. `candidateCount` rows were persisted. |
| `FAILED` | The job could not complete. `error` explains why. |

`aiApplied` is `false` when the LLM failed but deterministic skill-overlap
scores were persisted. In that case `status` is still `READY` and `error`
contains a warning; the dashboard should show the results with a small
"AI rationale unavailable" note.

## Behaviour guarantees

- Repeated clicks return the existing active job instead of starting a second
  LLM run.
- A `READY` job with the same project requirements is reused for the cooldown
  window (`matching.ai.cooldown-minutes`, default 10 minutes).
- A `PENDING`/`RUNNING` job older than `matching.job.stale-minutes` is marked
  `FAILED` lazily on the next request or status poll, so no scheduler is needed.
- The database unique index on `lock_key` is the final guard against duplicate
  concurrent jobs.
- `GET .../recommendations` never calls the LLM.
