package cn.medical.identity;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    @Select("SELECT a.id FROM account a JOIN account_role r ON r.account_id=a.id WHERE a.enabled=TRUE AND r.role='ADMIN'") List<String> administrators();
    record Account(String id, String identifier, String passwordHash, boolean enabled) {}
    record RefreshSession(String id, String accountId, String tokenHash, long expiresAt, boolean revoked) {}

    @Select("SELECT id, identifier, password_hash, enabled FROM account WHERE identifier = #{identifier}")
    Account byIdentifier(String identifier);
    @Select("SELECT id, identifier, password_hash, enabled FROM account WHERE id = #{id}")
    Account byId(String id);
    @Select("SELECT role FROM account_role WHERE account_id = #{id} ORDER BY role")
    List<String> roles(String id);
    @Insert("INSERT INTO account (id, identifier, password_hash, enabled, created_at) VALUES (#{id}, #{identifier}, #{passwordHash}, TRUE, #{now})")
    void insertAccount(@Param("id") String id, @Param("identifier") String identifier,
                       @Param("passwordHash") String passwordHash, @Param("now") long now);
    @Insert("INSERT INTO account_role (account_id, role) VALUES (#{id}, #{role})")
    void insertRole(@Param("id") String id, @Param("role") String role);
    @Insert("INSERT INTO doctor_profile (account_id, credential_status) VALUES (#{id}, 'PENDING')")
    void insertDoctor(String id);
    @Select("SELECT credential_status FROM doctor_profile WHERE account_id = #{id}")
    String credentialStatus(String id);
    @Select("SELECT a.id, a.identifier, d.display_name, d.title, d.biography, d.credential_status, a.enabled FROM doctor_profile d JOIN account a ON a.id=d.account_id ORDER BY a.id")
    List<IdentityService.Doctor> doctors();
    @Select("SELECT a.id, a.identifier, d.display_name, d.title, d.biography, d.credential_status, a.enabled FROM doctor_profile d JOIN account a ON a.id=d.account_id WHERE a.id=#{id} FOR UPDATE")
    IdentityService.Doctor lockDoctor(String id);
    @Update("UPDATE doctor_profile SET display_name=#{displayName}, title=#{title}, biography=#{biography}, credential_status=#{credentialStatus} WHERE account_id=#{id}")
    void updateDoctor(IdentityService.Doctor doctor);
    @Insert("INSERT INTO refresh_session (id, account_id, token_hash, expires_at, revoked) VALUES (#{id}, #{accountId}, #{tokenHash}, #{expiresAt}, FALSE)")
    void insertSession(RefreshSession session);
    @Select("SELECT id, account_id, token_hash, expires_at, revoked FROM refresh_session WHERE token_hash = #{hash} FOR UPDATE")
    RefreshSession lockRefresh(String hash);
    @Select("SELECT id, account_id, token_hash, expires_at, revoked FROM refresh_session WHERE id = #{id}")
    RefreshSession sessionById(String id);
    @Update("UPDATE refresh_session SET revoked = TRUE WHERE id = #{id}")
    void revokeSession(String id);
    @Update("UPDATE account SET enabled = #{enabled} WHERE id = #{id}")
    int setEnabled(@Param("id") String id, @Param("enabled") boolean enabled);
}
