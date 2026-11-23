package model;

/** A name in the "Billed by" list (managed by the owner in Settings). Not a login account. */
public class StaffMember {
    private final int id;
    private final String name;
    private final boolean active;

    public StaffMember(int id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }

    @Override
    public String toString() { return name; }   // shown in the "Billed by" drop-down
}
