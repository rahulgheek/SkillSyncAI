# Real-time notifications over STOMP

Notifications are delivered over the existing STOMP/WebSocket connection.
Server-Sent Events (SSE) and the old `/api/v1/notifications/stream` endpoint
have been removed.

## Connect

```text
ws(s)://<host>/ws
```

STOMP `CONNECT` headers:

```text
Authorization: Bearer <jwt>
```

The JWT is validated once on `CONNECT`. The principal name is the user's email.

## Subscribe

```text
/user/queue/notifications
```

The server pushes a `NotificationResponse`:

```json
{
  "id": "3d4e...",
  "type": "MATCHING_READY",
  "title": "AI Recommendations Ready",
  "message": "We ranked 18 matches for you. Open your dashboard to review them.",
  "isRead": false,
  "relatedEntityId": "5c1e...",
  "createdAt": "2026-11-21T10:00:05Z"
}
```

Existing chat destinations are unchanged:

- `/topic/projects/{projectId}` for project chat
- `/user/queue/messages` for direct messages

## Heartbeats and reconnect

- The broker sends and expects heartbeats every 15 seconds.
- The simple broker has no replay. After a reconnect the client must resync:
  - `GET /api/v1/notifications?page=0&size=20`
  - `GET /api/v1/notifications/unread-count`
  - `GET /api/v1/chat/conversations`
- Use exponential backoff with jitter and resubscribe on every successful
  reconnect. Do not keep a parallel polling loop while the socket is healthy.

## Why one transport

Chat already requires bidirectional STOMP. Keeping SSE for notifications
meant two handshakes, two token lifecycles, two reconnect implementations and
two reverse-proxy configurations. Notifications are one-way and map cleanly to
`/user/queue/notifications`, so STOMP is the single channel.

Notifications are persisted first and pushed with
`@TransactionalEventListener(AFTER_COMMIT)`, so a rolled-back transaction can
never deliver a notification that does not exist in the database.
