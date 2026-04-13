package com.axiom.launcher.db;

/**
 * Represents a managed OSRS account.
 *
 * {@code bankPinEnc}     — bank PIN encrypted at rest (AES, managed by a future CryptoUtil).
 * {@code jagexCharacterId} — the stable Jagex account character ID used to identify the
 *                           session to the Axiom plugin inside RuneLite.
 * {@code proxyId}        — nullable FK to proxies(id); null means direct connection.
 */
public class Account
{
    public int     id;
    public String  displayName;
    public String  jagexCharacterId;
    public String  bankPinEnc;
    public int     preferredWorld;
    public Integer proxyId;           // nullable
    public String  notes;

    /** No-arg constructor required for JDBC mapping. */
    public Account() {}

    public Account(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return "Account{id=" + id + ", displayName='" + displayName + "'}";
    }
}
