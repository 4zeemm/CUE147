package model;

import java.time.LocalDateTime;

/** A pool table, snooker table or PS5 station. */
public class GameTable {
    public static final String POOL = "POOL";
    public static final String SNOOKER = "SNOOKER";
    public static final String PS5 = "PS5";

    private int id;
    private String name;
    private String type;
    private boolean active = true;
    private boolean underMaintenance;      // true = no games can be started on this table

    // filled in only when a session is running on this table
    private int activeSessionId;
    private LocalDateTime activeStart;
    private String activePlayer;

    public GameTable(int id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public void setActiveSession(int sessionId, LocalDateTime start, String player) {
        this.activeSessionId = sessionId;
        this.activeStart = start;
        this.activePlayer = player;
    }

    public boolean isUnderMaintenance() { return underMaintenance; }
    public void setUnderMaintenance(boolean underMaintenance) { this.underMaintenance = underMaintenance; }

    public boolean isOccupied() { return activeSessionId > 0; }

    /** OCCUPIED, MAINTENANCE or AVAILABLE. */
    public String getStatus() {
        if (isOccupied()) return "OCCUPIED";
        return underMaintenance ? "MAINTENANCE" : "AVAILABLE";
    }
    public int getActiveSessionId() { return activeSessionId; }
    public LocalDateTime getActiveStart() { return activeStart; }
    public String getActivePlayer() { return activePlayer == null ? "-" : activePlayer; }
    public String getTypeLabel() { return type.equals(PS5) ? "PS5" : type.charAt(0) + type.substring(1).toLowerCase(); }

    @Override
    public String toString() { return name; }
}
