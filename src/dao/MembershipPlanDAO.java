package dao;

import database.DBConnection;
import model.MembershipPlan;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** The three membership plans (Monthly, Quarterly, Yearly). The owner changes price, months and discount. */
public class MembershipPlanDAO {

    public List<MembershipPlan> findAll() throws SQLException {
        List<MembershipPlan> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM membership_plans ORDER BY sort_order");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new MembershipPlan(rs.getInt("plan_id"), rs.getString("name"), rs.getInt("duration_months"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("discount_percent")));
            }
        }
        return list;
    }

    public void update(int planId, int months, BigDecimal price, BigDecimal discountPercent) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE membership_plans SET duration_months = ?, price = ?, discount_percent = ? WHERE plan_id = ?")) {
            ps.setInt(1, months);
            ps.setBigDecimal(2, price);
            ps.setBigDecimal(3, discountPercent);
            ps.setInt(4, planId);
            ps.executeUpdate();
        }
    }
}
