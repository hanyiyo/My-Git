# Issue 1 — pharmacy acceptance evidence

| Criterion | Public seam | Test/evidence | Result |
| --- | --- | --- | --- |
| Admin manages enabled medicine, price, stock and threshold | `GET/POST/PUT/DELETE /api/v1/admin/pharmacy/medicines` | `PharmacyServiceTest.adminCannotLowerStockBelowPendingHold` | Implemented; HTTP integration pending root full gate |
| Only active medicines can be prescribed; line name/price is snapshotted | `POST /api/v1/doctor/pharmacy/appointments/{id}/prescriptions` | `PharmacyService.reservePrescription` | Implemented; HTTP integration pending |
| Prescription reserves stock serially and rolls back on insufficient stock | same doctor endpoint | database row locks and ordered IDs in service | Implemented; concurrency HTTP test pending |
| Payment is idempotent and timeout releases stock independently | `POST /api/v1/patient/pharmacy/orders/{id}/pay`, scheduler | order state conditional updates and due scanner | Implemented; fake-clock HTTP test pending |
| Patient owns medicine orders and paid clinical record remains | `GET /api/v1/patient/pharmacy/orders` | controller role boundary and independent order tables | Implemented; browser test pending |

The complete acceptance run is `pwsh -NoProfile -File scripts/verify.ps1` after root integrates RulesService and clinical finish coordination.
