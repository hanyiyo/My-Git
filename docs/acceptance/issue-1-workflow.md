# T1 登记、候诊与审批验收记录

## Public seams

- Spring Boot HTTP `/api/v1/patient/appointments`, `/api/v1/patient/appointments/{id}/check-in`, `/restore-queue` and `/api/v1/doctor/reception/{id}/{call|start|miss}` cover date-fixed registration, queue restoration and reception transitions.
- Catalog and governance HTTP endpoints cover published schedules, department visibility and suspension/substitution state.
- Playwright browser flows use the same endpoints through the doctor and patient views.

## Acceptance cases

| Case | Test | Result | Limitation |
| --- | --- | --- | --- |
| Same-day registration is `WALK_IN`; future registration is `ADVANCE` | `BookingHttpTest` date boundary cases | Pending full gate | Requires fake hospital clock |
| Restoration returns to the original source queue once | `BookingHttpTest` queue ordering case | Pending full gate | None |
| Online call waits five minutes and a patient reply prevents missed marking | `ReceptionServiceTest`/HTTP reception case | Pending full gate | Clinical message observer is a read-only mapper seam |
| No-show releases the patient half-day guard without refund | reception scheduler HTTP case | Pending full gate | No automatic refund is intentionally created |
| Approval cutoff blocks late actions and direct suspension cancels/refunds | governance HTTP cases | Existing coverage; pending full gate | Substitute migration schedule remains integration work |
