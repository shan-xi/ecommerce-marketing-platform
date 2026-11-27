package com.example.marketingplatform.member;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.identity.infrastructure.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MemberProfileIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtTokenService tokens;

    @Test
    void profileIsMemberOnlyAndAlwaysResolvedFromPrincipal() throws Exception {
        UUID user = UUID.randomUUID(), member = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'MEMBER','ACTIVE',now(),now())", user, "profile-" + user + "@example.test", "x");
        jdbc.update("INSERT INTO members(id,user_id,display_name,birth_date,gender,registered_at,membership_tier,order_count,total_spending,created_at,updated_at) VALUES(?,?,?,DATE '1990-01-01','FEMALE',now(),'GOLD',2,20.00,now(),now())", member, user, "Profile Member");
        try {
            mvc.perform(get("/api/v1/member/profile")).andExpect(status().isUnauthorized());
            String memberToken = tokens.createAccessToken(new UserPrincipal(user, "MEMBER"));
            mvc.perform(get("/api/v1/member/profile").cookie(new Cookie("access_token", memberToken)).param("memberId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.memberId").value(member.toString()))
                    .andExpect(jsonPath("$.data.displayName").value("Profile Member"));
            UUID admin = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'ADMIN','ACTIVE',now(),now())", admin, "profile-admin-" + admin + "@example.test", "x");
            mvc.perform(get("/api/v1/member/profile").cookie(new Cookie("access_token", tokens.createAccessToken(new UserPrincipal(admin, "ADMIN")))))
                    .andExpect(status().isForbidden());
            jdbc.update("DELETE FROM users WHERE id=?", admin);
        } finally {
            jdbc.update("DELETE FROM members WHERE id=?", member);
            jdbc.update("DELETE FROM users WHERE id=?", user);
        }
    }
}
