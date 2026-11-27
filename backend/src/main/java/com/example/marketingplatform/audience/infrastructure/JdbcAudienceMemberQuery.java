package com.example.marketingplatform.audience.infrastructure;

import com.example.marketingplatform.audience.application.AudienceMemberQuery;
import com.example.marketingplatform.audience.application.AudiencePreviewResult;
import com.example.marketingplatform.audience.domain.AudienceRuleSet;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

@Repository
public class JdbcAudienceMemberQuery implements AudienceMemberQuery {
    private static final Map<String, String> SQL_OPERATORS = Map.of(
            "=", "=", "!=", "!=", ">", ">", ">=", ">=", "<", "<", "<=", "<=",
            "IN", "IN", "NOT IN", "NOT IN");
    private static final Map<String, String> COLUMNS = Map.of(
            "gender", "m.gender", "registeredAt", "m.registered_at", "membershipTier", "m.membership_tier",
            "orderCount", "m.order_count", "totalSpending", "m.total_spending", "lastOrderAt", "m.last_order_at",
            "lastLoginAt", "m.last_login_at");
    private static final Set<String> ENUM_FIELDS = Set.of("gender", "membershipTier");
    private static final Set<String> NULLABLE_FIELDS = Set.of("gender", "lastOrderAt", "lastLoginAt");
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAudienceMemberQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AudiencePreviewResult preview(List<AudienceRuleSet.Condition> conditions, LocalDate asOfDate, int sampleSize) {
        if (conditions == null || conditions.isEmpty())
            throw new IllegalArgumentException("At least one audience condition is required.");
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();
        int index = 0;
        for (AudienceRuleSet.Condition condition : conditions) {
            String column = "age".equals(condition.field())
                    ? "CAST(EXTRACT(YEAR FROM age(CAST(:asOfDate AS date), m.birth_date)) AS integer)"
                    : COLUMNS.get(condition.field());
            if (column == null) throw new IllegalArgumentException("Unsupported audience field.");
            String operator = SQL_OPERATORS.get(condition.operator());
            if (operator == null) throw new IllegalArgumentException("Unsupported audience operator.");
            if (ENUM_FIELDS.contains(condition.field()) && !Set.of("=", "!=", "IN", "NOT IN").contains(operator))
                throw new IllegalArgumentException("Ordered enum comparisons are not supported.");
            String nullGuard = "age".equals(condition.field()) ? "m.birth_date IS NOT NULL AND "
                    : NULLABLE_FIELDS.contains(condition.field()) ? column + " IS NOT NULL AND " : "";
            String parameter = "condition" + index++;
            if ("age".equals(condition.field())) params.addValue("asOfDate", asOfDate);
            params.addValue(parameter, condition.value());
            if (operator.equals("IN") || operator.equals("NOT IN")) {
                clauses.add("(" + nullGuard + column + (operator.equals("IN") ? " IN (:" : " NOT IN (:") + parameter + "))");
            } else {
                clauses.add("(" + nullGuard + column + " " + operator + " :" + parameter + ")");
            }
        }
        params.addValue("sampleLimit", Math.max(0, Math.min(sampleSize, 20)));
        String where = String.join(" AND ", clauses);
        List<PreviewRow> rows = jdbc.query("""
                WITH matching AS (
                    SELECT m.id, m.display_name FROM members m WHERE %s
                ), sample_rows AS (
                    SELECT id, display_name FROM matching ORDER BY id LIMIT :sampleLimit
                )
                SELECT (SELECT count(*) FROM matching) AS total_count,
                       sample_rows.id AS member_id, sample_rows.display_name
                FROM (VALUES (1)) AS anchor(dummy)
                LEFT JOIN sample_rows ON TRUE
                ORDER BY sample_rows.id
                """.formatted(where), params, (rs, row) -> new PreviewRow(rs.getLong("total_count"),
                rs.getObject("member_id", UUID.class), rs.getString("display_name")));
        long count = rows.isEmpty() ? 0 : rows.getFirst().count();
        List<AudiencePreviewResult.SampleMember> sample = rows.stream().filter(row -> row.memberId() != null)
                .map(row -> new AudiencePreviewResult.SampleMember(row.memberId(), row.displayName())).toList();
        return new AudiencePreviewResult(count, sample);
    }


    @Override
    public List<UUID> findMemberIds(List<AudienceRuleSet.Condition> conditions, LocalDate asOfDate) {
        if (conditions == null || conditions.isEmpty())
            throw new IllegalArgumentException("At least one audience condition is required.");
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();
        int index = 0;
        for (AudienceRuleSet.Condition condition : conditions) {
            String column = "age".equals(condition.field())
                    ? "CAST(EXTRACT(YEAR FROM age(CAST(:asOfDate AS date), m.birth_date)) AS integer)"
                    : COLUMNS.get(condition.field());
            if (column == null) throw new IllegalArgumentException("Unsupported audience field.");
            String operator = SQL_OPERATORS.get(condition.operator());
            if (operator == null) throw new IllegalArgumentException("Unsupported audience operator.");
            if (ENUM_FIELDS.contains(condition.field()) && !Set.of("=", "!=", "IN", "NOT IN").contains(operator))
                throw new IllegalArgumentException("Ordered enum comparisons are not supported.");
            String nullGuard = "age".equals(condition.field()) ? "m.birth_date IS NOT NULL AND "
                    : NULLABLE_FIELDS.contains(condition.field()) ? column + " IS NOT NULL AND " : "";
            String parameter = "condition" + index++;
            if ("age".equals(condition.field())) params.addValue("asOfDate", asOfDate);
            params.addValue(parameter, condition.value());
            if (operator.equals("IN") || operator.equals("NOT IN"))
                clauses.add("(" + nullGuard + column + (operator.equals("IN") ? " IN (:" : " NOT IN (:") + parameter + "))");
            else clauses.add("(" + nullGuard + column + " " + operator + " :" + parameter + ")");
        }
        return jdbc.query("SELECT m.id FROM members m WHERE " + String.join(" AND ", clauses) + " ORDER BY m.id", params,
                (rs, row) -> rs.getObject(1, UUID.class));
    }

    private record PreviewRow(long count, UUID memberId, String displayName) {
    }
}
