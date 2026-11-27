package com.example.marketingplatform.member.infrastructure;

import com.example.marketingplatform.member.application.MemberProfileRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMemberProfileRepository implements MemberProfileRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemberProfileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MemberRecord> findByUserId(UUID userId) {
        List<MemberRecord> rows = jdbc.query("""
                SELECT id,display_name,birth_date,gender,registered_at,membership_tier,
                       order_count,total_spending,last_order_at,last_login_at
                FROM members WHERE user_id=:userId
                """, Map.of("userId", userId), (rs, row) -> {
            Date birth = rs.getDate("birth_date");
            Timestamp lastOrder = rs.getTimestamp("last_order_at");
            Timestamp lastLogin = rs.getTimestamp("last_login_at");
            return new MemberRecord(rs.getObject("id", UUID.class), rs.getString("display_name"),
                    birth == null ? null : birth.toLocalDate(), rs.getString("gender"),
                    rs.getTimestamp("registered_at").toInstant(), rs.getString("membership_tier"),
                    rs.getInt("order_count"), rs.getBigDecimal("total_spending"),
                    lastOrder == null ? null : lastOrder.toInstant(), lastLogin == null ? null : lastLogin.toInstant());
        });
        return rows.stream().findFirst();
    }
}
