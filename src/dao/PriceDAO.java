package dao;

import database.DBConnection;
import model.Price;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Database operations for the prices table. Prices are NOT written in the Java code. */
public class PriceDAO {

    public List<Price> findAll() throws SQLException {
        List<Price> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM prices ORDER BY table_type, gender");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Price(rs.getString("table_type"), rs.getString("gender"), rs.getBigDecimal("hourly_rate")));
            }
        }
        return list;
    }

    public BigDecimal getRate(String tableType, String gender) throws SQLException {
        String sql = "SELECT hourly_rate FROM prices WHERE table_type = ? AND gender = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tableType);
            ps.setString(2, gender);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    public void update(String tableType, String gender, BigDecimal newRate) throws SQLException {
        String sql = "UPDATE prices SET hourly_rate = ? WHERE table_type = ? AND gender = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, newRate);
            ps.setString(2, tableType);
            ps.setString(3, gender);
            ps.executeUpdate();
        }
    }
}
