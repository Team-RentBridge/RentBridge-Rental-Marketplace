# Booking and availability

Rental date ranges use `[pickup, return)`: the pickup date is reserved and the return date is checkout, not another rental night. The calendar displays remaining units per day and disables owner-blocked or fully reserved dates. Listing availability limits and date blocks are checked again by the server.

## Booking lifecycle

- Creating a reservation places a 15-minute payment hold. Expired holds release their dates automatically.
- Owners can enable instant booking when creating a rental listing. Payment confirms these bookings immediately.
- Other paid bookings wait for owner approval for up to 24 hours. A decline or missed approval deadline releases the dates and triggers an automatic full Razorpay refund.
- Renters may cancel before the recorded cancellation deadline; cancellation releases the dates and triggers a full refund.
- Pickup is scheduled for 9:00 AM on the pickup date. Return is due by 5:00 PM on the return date. The cancellation cutoff is 9:00 AM on the day before pickup.
- Owners can block and unblock dates from their listing's availability calendar. A block cannot overlap a reservation.

## Concurrency and database setup

The backend locks the equipment row while checking capacity and creating a hold. Availability blocks use the same lock, so concurrent requests cannot reserve beyond the listing's total quantity. Expiry and booking metadata columns, indexes, and the `equipment_blocks` table are applied idempotently at backend startup by `BookingSchemaMigration`. The PostgreSQL user therefore needs permission to alter tables and create indexes/tables.

Legacy active bookings are counted when the migration initializes total listing quantity, preserving their reserved units.

## Payment configuration

Set `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` for the backend and `REACT_APP_RAZORPAY_KEY_ID` for the frontend. Rental totals and currency are calculated/selected by the backend; the frontend does not choose the amount for a booking hold.
