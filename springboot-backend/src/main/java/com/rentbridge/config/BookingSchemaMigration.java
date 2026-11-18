package com.rentbridge.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public BookingSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE equipment ADD COLUMN IF NOT EXISTS instant_booking BOOLEAN NOT NULL DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE equipment ADD COLUMN IF NOT EXISTS total_quantity INTEGER");
        jdbcTemplate.execute(
            "UPDATE equipment e SET total_quantity = GREATEST(COALESCE(e.quantity, 1), 1) + " +
            "COALESCE((SELECT SUM(b.quantity) FROM bookings b " +
            "WHERE b.equipment_id = e.id AND b.status = 'active'), 0) " +
            "WHERE e.total_quantity IS NULL");

        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS hold_expires_at TIMESTAMPTZ");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS approval_expires_at TIMESTAMPTZ");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS pickup_at TIMESTAMPTZ");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS return_by TIMESTAMPTZ");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancellation_deadline TIMESTAMPTZ");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_order_id VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS refund_status VARCHAR(30)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS refund_updated_at TIMESTAMPTZ");
        jdbcTemplate.execute(
            "UPDATE bookings SET pickup_at = start_date + TIME '09:00', " +
            "return_by = end_date + TIME '17:00', " +
            "cancellation_deadline = start_date - INTERVAL '1 day' + TIME '09:00' " +
            "WHERE pickup_at IS NULL OR return_by IS NULL OR cancellation_deadline IS NULL");

        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS equipment_blocks (" +
            "id BIGSERIAL PRIMARY KEY, " +
            "equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE, " +
            "created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, " +
            "start_date DATE NOT NULL, " +
            "end_date DATE NOT NULL, " +
            "reason VARCHAR(255), " +
            "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
            "CONSTRAINT equipment_blocks_valid_dates CHECK (end_date > start_date))");

        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS bookings_equipment_dates_status_idx " +
            "ON bookings (equipment_id, start_date, end_date, status)");
        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS bookings_hold_expiry_idx " +
            "ON bookings (hold_expires_at) WHERE status = 'payment_pending'");
        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS bookings_approval_expiry_idx " +
            "ON bookings (approval_expires_at) WHERE status = 'pending_approval'");
        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS equipment_blocks_dates_idx " +
            "ON equipment_blocks (equipment_id, start_date, end_date)");
    }
}
