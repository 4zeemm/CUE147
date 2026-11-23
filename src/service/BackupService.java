package service;

import database.DBConnection;
import database.DatabaseBackup;
import database.SchemaMigrator;
import exception.BusinessException;
import util.AppLogger;
import util.AppSettings;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backups of the whole database: automatic once a day, "Backup now", and "Restore from backup".
 * Files go to the folder chosen in Settings > Backups; only the newest N are kept.
 * Every backup and restore is written to the table backup_log (shown in Settings).
 */
public class BackupService {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);   // never two backups at the same time
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /** Files of this database start with this, e.g. "cue147-cue147-2026-11-23_17-30-00.sql". */
    private String prefix() { return "cue147-" + DBConnection.getConfig().effectiveDatabase() + "-"; }

    public File folder() { return new File(AppSettings.getBackupFolder()); }

    /** Makes a backup now. kind = AUTOMATIC, MANUAL or BEFORE_RESTORE. Returns the new file. */
    public File backupNow(String kind) throws SQLException, IOException, BusinessException {
        if (!RUNNING.compareAndSet(false, true)) throw new BusinessException("A backup is already running. Please wait a moment.");
        try {
            File dir = folder();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create the backup folder " + dir);
            String base = prefix() + LocalDateTime.now().format(FILE_TIME);
            File file = new File(dir, base + ".sql");
            for (int n = 2; file.exists(); n++) file = new File(dir, base + "_" + n + ".sql");
            File temp = new File(dir, file.getName() + ".part");   // the real name appears only when the file is complete
            try (Connection con = DBConnection.getConnection();
                 Writer out = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
                DatabaseBackup.write(con, out, "made " + LocalDateTime.now().format(SHOW) + " on " + computerName()
                        + ", database " + DBConnection.getConfig().effectiveDatabase() + " version " + currentVersion(con));
            } catch (SQLException | IOException e) {
                Files.deleteIfExists(temp.toPath());
                throw e;
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
            removeOldBackups();
            log(kind, file);
            AppLogger.info(kind + " backup saved: " + file);
            return file;
        } finally {
            RUNNING.set(false);
        }
    }

    /** True when automatic backups are on for this computer and today's backup is not there yet. */
    public boolean isAutomaticBackupDue() {
        if (!AppSettings.isAutoBackup()) return false;
        String today = prefix() + LocalDate.now();
        return listBackups().stream().noneMatch(f -> f.getName().startsWith(today));
    }

    /** Runs today's automatic backup if it is due. Never throws: problems are logged and returned as text (null = fine). */
    public String automaticBackupIfDue() {
        if (!isAutomaticBackupDue()) return null;
        try {
            backupNow("AUTOMATIC");
            return null;
        } catch (Exception e) {
            AppLogger.error("Automatic backup failed", e);
            return "Automatic backup failed: " + e.getMessage();
        }
    }

    /** Backups of this database in the folder, newest last. */
    public List<File> listBackups() {
        File[] files = folder().listFiles((d, name) -> name.startsWith(prefix()) && name.endsWith(".sql"));
        List<File> list = new ArrayList<>(files == null ? List.of() : Arrays.asList(files));
        list.sort(Comparator.comparing(File::getName));
        return list;
    }

    private void removeOldBackups() {
        List<File> all = listBackups();
        int keep = Math.max(1, AppSettings.getBackupKeep());
        for (int i = 0; i < all.size() - keep; i++) {
            if (!all.get(i).delete()) AppLogger.info("Could not delete old backup " + all.get(i));
        }
    }

    /**
     * Replaces ALL data with the data of the backup file. A safety backup of the current data is made first,
     * and after the restore the tables are brought up to date (if the backup is from an older version).
     */
    public void restore(File backup) throws SQLException, IOException, BusinessException {
        String text = new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8);
        if (!text.startsWith(DatabaseBackup.HEADER)) throw new BusinessException("This file is not a CUE 147 backup:\n" + backup.getName());
        File safety = backupNow("BEFORE_RESTORE");
        try (Connection con = DBConnection.getConnection()) {
            DatabaseBackup.restore(con, text);
        }
        SchemaMigrator.migrate();
        log("RESTORED", backup);
        AppLogger.info("Database restored from " + backup + " (safety backup: " + safety + ")");
    }

    /** "23 Nov 2026, 17:30 on COUNTER-PC (automatic)", or a sentence saying there is none yet. */
    public String lastSuccessfulBackup() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT made_at, computer, kind FROM backup_log "
                     + "WHERE kind IN ('AUTOMATIC','MANUAL','BEFORE_RESTORE') ORDER BY made_at DESC, backup_id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return "No backup has been made yet.";
            return rs.getTimestamp(1).toLocalDateTime().format(SHOW) + " on " + rs.getString(2) + " (" + rs.getString(3).toLowerCase().replace('_', ' ') + ")";
        }
    }

    private void log(String kind, File file) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO backup_log (made_at, computer, file_name, size_bytes, kind) VALUES (NOW(), ?, ?, ?, ?)")) {
            ps.setString(1, computerName());
            String name = file.getAbsolutePath();
            ps.setString(2, name.length() > 250 ? name.substring(name.length() - 250) : name);
            ps.setLong(3, file.length());
            ps.setString(4, kind);
            ps.executeUpdate();
        }
    }

    private static int currentVersion(Connection con) {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    /** The name of this computer (Windows: COMPUTERNAME). */
    static String computerName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) {
            try {
                name = java.net.InetAddress.getLocalHost().getHostName();
            } catch (IOException e) {
                name = "this computer";
            }
        }
        return name.length() > 60 ? name.substring(0, 60) : name;
    }
}
