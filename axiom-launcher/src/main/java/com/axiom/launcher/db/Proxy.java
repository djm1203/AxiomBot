package com.axiom.launcher.db;

/**
 * Represents an HTTP/HTTPS proxy configuration.
 *
 * {@code passwordEnc} — proxy password encrypted at rest.
 *                       Decryption is handled by a future CryptoUtil before
 *                       the value is written to -Dhttp.proxyPassword at launch time.
 */
public class Proxy
{
    public int    id;
    public String name;
    public String host;
    public int    port;
    public String username;
    public String passwordEnc;  // nullable; encrypted at rest

    /** No-arg constructor required for JDBC mapping. */
    public Proxy() {}

    public Proxy(String name, String host, int port)
    {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString()
    {
        return "Proxy{id=" + id + ", name='" + name + "', host='" + host + ":" + port + "'}";
    }
}
