package com.axiom.launcher.db;

import com.axiom.launcher.security.CryptoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRUD operations for the {@code proxies} table.
 *
 * The {@link #hasLinkedAccounts(int)} guard should be checked before deleting a
 * proxy — the FK is defined as ON DELETE SET NULL, so SQLite will null out
 * linked accounts rather than raising an error, but callers should warn the user.
 */
public class ProxyRepository
{
    private static final Logger log = LoggerFactory.getLogger(ProxyRepository.class);

    private static final String SELECT_ALL =
        "SELECT id, name, host, port, username, password_enc " +
        "FROM proxies ORDER BY name COLLATE NOCASE";

    private static final String INSERT =
        "INSERT INTO proxies (name, host, port, username, password_enc) " +
        "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE proxies SET name = ?, host = ?, port = ?, username = ?, password_enc = ? " +
        "WHERE id = ?";

    private static final String DELETE = "DELETE FROM proxies WHERE id = ?";

    private static final String COUNT_LINKED =
        "SELECT COUNT(*) FROM accounts WHERE proxy_id = ?";

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns all proxies ordered by name, or an empty list on error. */
    public List<Proxy> findAll()
    {
        List<Proxy> result = new ArrayList<>();
        try (Statement stmt = Database.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(SELECT_ALL))
        {
            while (rs.next()) result.add(mapRow(rs));
        }
        catch (Exception e)
        {
            log.error("ProxyRepository.findAll failed", e);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns true if at least one account currently references this proxy.
     * Used to warn the user before deleting a proxy that has linked accounts.
     */
    public boolean hasLinkedAccounts(int proxyId)
    {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(COUNT_LINKED))
        {
            ps.setInt(1, proxyId);
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
        catch (Exception e)
        {
            log.error("ProxyRepository.hasLinkedAccounts({}) failed", proxyId, e);
            return false; // conservative: don't block delete on DB error
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Inserts a new proxy row and sets {@code proxy.id} to the generated key.
     *
     * @throws RuntimeException if the name is not unique or the insert fails
     */
    public void insert(Proxy proxy)
    {
        try (PreparedStatement ps = Database.getConnection()
                .prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS))
        {
            bindWriteParams(ps, proxy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) proxy.id = keys.getInt(1);
            }
            log.debug("Inserted proxy id={} name='{}'", proxy.id, proxy.name);
        }
        catch (Exception e)
        {
            log.error("ProxyRepository.insert failed for '{}'", proxy.name, e);
            throw new RuntimeException("Failed to insert proxy: " + e.getMessage(), e);
        }
    }

    /**
     * Updates all mutable fields for an existing proxy.
     *
     * @throws RuntimeException if the update fails
     */
    public void update(Proxy proxy)
    {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(UPDATE))
        {
            bindWriteParams(ps, proxy);
            ps.setInt(6, proxy.id);
            int rows = ps.executeUpdate();
            if (rows == 0) log.warn("ProxyRepository.update: no row found for id={}", proxy.id);
            else           log.debug("Updated proxy id={}", proxy.id);
        }
        catch (Exception e)
        {
            log.error("ProxyRepository.update failed for id={}", proxy.id, e);
            throw new RuntimeException("Failed to update proxy: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes the proxy with the given ID.
     * Linked accounts will have their {@code proxy_id} set to NULL (ON DELETE SET NULL).
     *
     * @throws RuntimeException if the delete fails
     */
    public void delete(int id)
    {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(DELETE))
        {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            log.debug("Deleted proxy id={} (rows affected={})", id, rows);
        }
        catch (Exception e)
        {
            log.error("ProxyRepository.delete failed for id={}", id, e);
            throw new RuntimeException("Failed to delete proxy: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Proxy mapRow(ResultSet rs) throws Exception
    {
        Proxy p       = new Proxy();
        p.id          = rs.getInt("id");
        p.name        = rs.getString("name");
        p.host        = rs.getString("host");
        p.port        = rs.getInt("port");
        p.username    = rs.getString("username");
        // Decrypt at read time — legacy plaintext values pass through unchanged
        p.passwordEnc = CryptoManager.decrypt(rs.getString("password_enc"));
        return p;
    }

    /** Binds the 5 writable fields (excludes id). Shared by insert and update. */
    private static void bindWriteParams(PreparedStatement ps, Proxy p) throws Exception
    {
        ps.setString(1, p.name);
        ps.setString(2, p.host);
        ps.setInt(3, p.port);
        ps.setString(4, p.username);
        // Encrypt at write time — null/empty stored as null
        String pw = (p.passwordEnc != null && !p.passwordEnc.isEmpty())
            ? CryptoManager.encrypt(p.passwordEnc) : null;
        ps.setString(5, pw);
    }
}
