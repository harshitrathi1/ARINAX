# ArenaX Security Specification (Payload-First Security TDD)

## 1. Data Invariants
1. **Identity Isolation & Ownership**: No unauthenticated user may read or write any collection. Users can only create profiles (`users/{userId}`), game profiles (`gameProfiles/{profileId}`), posts (`posts/{postId}`), comments, follows, challenges, chats, messages, notifications, reports, and blocks where their `request.auth.uid` matches the creator/owner field (`userId`, `authorId`, `followerId`, `challengerId`, `senderId`, `actorId`, `reporterId`, `blockerId`).
2. **PII Isolation**: `users/{userId}` stores strictly public gaming identity fields (username, IGN, game UID, Elo rating, win/loss) and zero PII (no email, phone, or physical address). Private profiles (`visibility == 'private'`) cannot be read or listed by other non-admin users.
3. **One UID Per Account**: `gameProfiles/{profileId}` uses `{game}_{gameUid}` as document ID and forbids overwriting another user's verified UID (`existing().userId == request.auth.uid`).
4. **Challenge State Machine & Terminal Locking**: Challenges in terminal states (`completed`, `declined`, `expired`, `cancelled`) cannot be mutated by non-admin clients. Only challenge participants (`request.auth.uid in existing().participantUids`) can transition challenge state.
5. **Chat Membership Isolation**: Only members in `memberUids` can read, list, or post to a `chats/{chatId}` thread or its `messages/{messageId}` subcollection.
6. **Temporal Integrity**: All `createdAt` and `updatedAt` fields must be server timestamps (`is timestamp && <= request.time`), and `createdAt` is immutable on update.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Scraper**: Unauthenticated `list` or `get` on `/users`, `/challenges`, `/posts`, or `/chats`.
2. **Identity Spoofing on Profile Create**: User `alice` attempts to create `/users/bob` or set `userId: "bob"`.
3. **Ghost Field Injection (Shadow Update)**: User updates `/users/alice` with an undeclared field `{"isAdmin": true}` or `{"hacked": 1}`.
4. **Private Profile Leak**: User `bob` attempts `get` or unfiltered `list` on `/users/alice` when `alice` has `visibility: "private"`.
5. **UID Hijacking**: User `bob` tries to overwrite `/gameProfiles/BGMI_5123456789` already owned by `alice`.
6. **Self-Follow / Fake Follow**: User `alice` tries to create a follow document on behalf of `bob` (`followerId: "bob"`).
7. **Terminal Challenge Mutation**: Participant tries to change `winnerId` or `status` on a challenge whose `status` is already `'completed'`.
8. **Outsider Challenge Sabotage**: User `mallory` (not in `participantUids`) tries to accept, decline, or submit scores on a challenge between `alice` and `bob`.
9. **Chat Eavesdropping**: User `mallory` (not in `memberUids`) tries to `get` or `list` `/chats/{chatId}` or `/chats/{chatId}/messages`.
10. **Cross-User Notification Read**: User `bob` queries `/notifications` where `recipientId == "alice"`.
11. **Value Poisoning**: User attempts to set a 10KB string on `username` or negative `stakePoints` on a challenge.
12. **Admin Email Spoofing**: User with `email == "indiafastgamers@gmail.com"` but `email_verified == false` attempts admin moderation updates on `/reports/{reportId}`.
