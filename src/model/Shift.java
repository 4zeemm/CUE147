package model;

import java.time.Duration;
import java.time.LocalDateTime;

/** A work shift of a staff member (a row of staff_shifts). */
public class Shift {
    private int id;
    private String userName;
    private LocalDateTime start;
    private LocalDateTime end;

    public Shift(int id, String userName, LocalDateTime start, LocalDateTime end) {
        this.id = id;
        this.userName = userName;
        this.start = start;
        this.end = end;
    }

    public int getId() { return id; }
    public String getUserName() { return userName; }
    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }

    public String getHoursText() {
        LocalDateTime until = end != null ? end : LocalDateTime.now();
        long minutes = Duration.between(start, until).toMinutes();
        return (minutes / 60) + " h " + (minutes % 60) + " m" + (end == null ? " (running)" : "");
    }
}
