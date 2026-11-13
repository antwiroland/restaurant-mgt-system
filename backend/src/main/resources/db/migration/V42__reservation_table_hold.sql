-- A reservation holds its table from one hour before the booked time. table_held_at records that this reservation
-- put the table on RESERVED, so a cancel or a no-show can hand it back. seated_at records that the party arrived
-- (an order was placed at the held table), so the hold is not reapplied after they pay and leave.
ALTER TABLE reservations ADD COLUMN table_held_at TIMESTAMPTZ;
ALTER TABLE reservations ADD COLUMN seated_at TIMESTAMPTZ;
