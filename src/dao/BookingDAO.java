package dao;

import database.DBConnection;
import model.Booking;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the bookings table. */
public class BookingDAO {

    private static final String SELECT_BOOKING =
            "SELECT b.*, c.name, t.table_name FROM bookings b "
            + "JOIN customers c ON c.customer_id = b.customer_id JOIN game_tables t ON t.table_id = b.table_id ";

    private Booking readBooking(ResultSet rs) throws SQLException {
        return new Booking(rs.getInt("booking_id"), rs.getInt("customer_id"), rs.getString("name"),
                rs.getInt("table_id"), rs.getString("table_name"), rs.getDate("booking_date").toLocalDate(),
                rs.getTime("booking_time").toLocalTime(), rs.getBigDecimal("duration_hours"),
                rs.getString("source"), rs.getString("status"));
    }

    public List<Booking> findAll() throws SQLException {
        return query(SELECT_BOOKING + "ORDER BY b.booking_date DESC, b.booking_time DESC", 0);
    }

    /** Next confirmed bookings from today on (used on the dashboard). */
    public List<Booking> findUpcoming(int limit) throws SQLException {
        return query(SELECT_BOOKING + "WHERE b.status = 'CONFIRMED' AND b.booking_date >= CURDATE() "
                + "ORDER BY b.booking_date, b.booking_time LIMIT ?", limit);
    }

    private List<Booking> query(String sql, int limit) throws SQLException {
        List<Booking> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (limit > 0) ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readBooking(rs));
            }
        }
        return list;
    }

    /**
     * True if a new booking (start time + hours) overlaps a CONFIRMED booking of the same table on that date.
     * Two time ranges overlap when: old start < new end  AND  old end > new start.
     * Times are compared in seconds after midnight (TIME_TO_SEC), so 18:30 = 66600.
     */
    public boolean isTableBooked(int tableId, LocalDate date, LocalTime time, BigDecimal hours) throws SQLException {
        int newStart = time.toSecondOfDay();
        int newEnd = newStart + (int) Math.round(hours.doubleValue() * 3600);
        String sql = "SELECT COUNT(*) FROM bookings WHERE table_id = ? AND booking_date = ? AND status = 'CONFIRMED' "
                + "AND TIME_TO_SEC(booking_time) < ? AND TIME_TO_SEC(booking_time) + duration_hours * 3600 > ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, newEnd);
            ps.setInt(4, newStart);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** The CONFIRMED booking of a table that covers this moment (start <= moment < end), or null. */
    public Booking findBookingAt(int tableId, LocalDateTime moment) throws SQLException {
        String sql = SELECT_BOOKING + "WHERE b.table_id = ? AND b.status = 'CONFIRMED' AND b.booking_date = ? "
                + "AND TIME_TO_SEC(b.booking_time) <= ? AND TIME_TO_SEC(b.booking_time) + b.duration_hours * 3600 > ? LIMIT 1";
        int second = moment.toLocalTime().toSecondOfDay();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.setDate(2, Date.valueOf(moment.toLocalDate()));
            ps.setInt(3, second);
            ps.setInt(4, second);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readBooking(rs) : null;
            }
        }
    }

    public int add(int customerId, int tableId, LocalDate date, LocalTime time, BigDecimal hours, String source) throws SQLException {
        String sql = "INSERT INTO bookings (customer_id, table_id, booking_date, booking_time, duration_hours, source) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setInt(2, tableId);
            ps.setDate(3, Date.valueOf(date));
            ps.setTime(4, Time.valueOf(time));
            ps.setBigDecimal(5, hours);
            ps.setString(6, source);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void cancel(int bookingId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE bookings SET status = 'CANCELLED' WHERE booking_id = ?")) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        }
    }
}
