package org.localts.model;

import java.util.UUID;

public class MinecraftProfileResponse {
    private UUID uuid;
    private String username;

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public void setUuid(UUID v) { uuid = v; }
    public void setUsername(String v) { username = v; }
}
