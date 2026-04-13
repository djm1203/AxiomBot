package com.axiom.launcher.db;

import com.axiom.launcher.security.CryptoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRUD operations for the {@code accounts} table.
 *
 * All methods catch JDBC exceptions internally and log them; callers receive
 * an empty result or null on read failures, and a RuntimeException on write
 * failures (so UI code can surface the error to the user).
 */
public class AccountRepository
{
    private static final Logger log = LoggerFactory.getLogger(AccountRepository.class);

    private static final String SELECT_ALL =
        "SELECT id, display_name, jagex_character_id, bank_pin_enc, " +
        "       preferred_world, proxy_id, notes " +
        "FROM accounts ORDER BY display_name COLLATE NOCASE";

    private static final String SELECT_BY_ID =
        "SELECT id, display_name, jagex_character_id, bank_pin_enc, " +
        "       preferred_world, proxy_id, notes " +
        "FROM accounts WHERE id = ?";

    private static final String INSERT =
        "INSERT INTO accounts (display_name, jagex_character_id, bank_pin_enc, " +
        "                      preferred_world, proxy_id, notes) " +
        "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE accounts SET display_name = ?, jagex_character_id = ?, bank_pin_enc = ?, " +
        "                    preferred_world = ?, proxy_id = ?, notes = ? " +
        "WHERE id = ?";

    private static final String DELETE = "DELETE FROM accounts WHERE id = ?";

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all accounts ordered by display_name, or an empty list on error. */
    public List<Account> findAll()
    {
        List<Account> result = new ArrayList<>();
        try (Statement stmt = Database.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(SELECT_ALL))
        {
            while (rs.next()) result.add(mapRow(rs));
        }
        catch (Exception e)
        {
            log.error("AccountRepository.findAll failed", e);
        }
        return Collections.unmodifiableList(result);
    }

    /** Returns the account with the given ID, or null if not found. */
    public Account findById(int id)
    {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(SELECT_BY_ID))
        {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next()) return mapRow(rs);
            }
        }
        catch (Exception e)
        {
            log.error("AccountRepository.findById({}) failed", id, e);
        }
        return null;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Inserts a new account row and sets {@code account.id} to the generated key.
     *
     * @throws RuntimeException if the insert fails
     */
    public void insert(Account account)
    {
        try (PreparedStatement ps = Database.getConnection()
                .prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS))
        {
            bindWriteParams(ps, account);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) account.id = keys.getInt(1);
            }
            log.debug("Inserted account id={} displayName='{}'", account.id, account.displayName);
        }
        catch (Exception e)
        {
            log.error("AccountRepository.insert failed for '{}'", account.displayName, e);
            throw new RuntimeException("Failed to insert account: " + e.getMessage(), e);
        }
    }

    /**
     * Updates all mutable fields for an existing account.
     *
     * @throws RuntimeException if the update fails
     */
    public void update(Account account)
    {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(UPDATE))
        {
            bindWriteParams(ps, account);
            ps.setInt(7, account.id);
            int rows = ps.executeUpdate();
            if (rows == 0) log.warn("AccountRepository.update: no row found for id={}", account.id);
            else           log.debug("Updated account id={}", account.id);
        }
        catch (Exception e)
        {
            log.error("AccountRepository.update failed for id={}", account.id, e);
            throw new RuntimeException("Failed to update account: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes the account with the given ID.
     *
     * @throws RuntimeException if the delete fails
     */
    public void delete(int id)
    {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(DELETE))
        {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            log.debug("Deleted account id={} (rows affected={})", id, rows);
        }
        catch (Exception e)
        {
            log.error("AccountRepository.delete failed for id={}", id, e);
            throw new RuntimeException("Failed to delete account: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Account mapRow(ResultSet rs) throws Exception
    {
        Account a          = new Account();
        a.id               = rs.getInt("id");
        a.displayName      = rs.getString("display_name");
        a.jagexCharacterId = rs.getString("jagex_character_id");
        // Decrypt at read time — legacy plaintext values pass through unchanged
        a.bankPinEnc       = CryptoManager.decrypt(rs.getString("bank_pin_enc"));
        a.preferredWorld   = rs.getInt("preferred_world");
        int proxyId        = rs.getInt("proxy_id");
        a.proxyId          = rs.wasNull() ? null : proxyId;
        a.notes            = rs.getString("notes");
        return a;
    }

    /** Binds the 6 writable fields (excludes id). Shared by insert and update. */
    private static void bindWriteParams(PreparedStatement ps, Account a) throws Exception
    {
        ps.setString(1, a.displayName);
        ps.setString(2, a.jagexCharacterId);
        // Encrypt at write time — null/empty stored as null
        String pin = (a.bankPinEnc != null && !a.bankPinEnc.isEmpty())
            ? CryptoManager.encrypt(a.bankPinEnc) : null;
        ps.setString(3, pin);
        ps.setInt(4, a.preferredWorld);
        if (a.proxyId != null) ps.setInt(5, a.proxyId);
        else                   ps.setNull(5, Types.INTEGER);
        ps.setString(6, a.notes);
    }
}
