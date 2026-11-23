package database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JUnit tests for the database settings and the SQL file reader (no MySQL needed). */
class DatabaseSetupTest {

    @Test
    void sqlFileIsSplitIntoStatementsWithoutComments() {
        List<String> parts = SchemaMigrator.splitStatements(
                "-- a comment; with a semicolon\nCREATE TABLE a (x INT); -- end comment\n"
                        + "INSERT INTO a VALUES ('text; with -- signs');\n\n");
        assertEquals(2, parts.size());
        assertEquals("CREATE TABLE a (x INT)", parts.get(0));
        assertEquals("INSERT INTO a VALUES ('text; with -- signs')", parts.get(1));
    }

    @Test
    void baseSchemaFileIsInsideTheProgramAndCreatesAllTables() throws Exception {
        try (var in = SchemaMigrator.class.getResourceAsStream("/db/" + SchemaMigrator.MIGRATIONS[0])) {
            String sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            long tables = SchemaMigrator.splitStatements(sql).stream().filter(s -> s.startsWith("CREATE TABLE")).count();
            assertEquals(11, tables);
        }
    }

    @Test
    void connectionAddressUsesTheSettings() {
        DatabaseConfig c = new DatabaseConfig("192.168.1.20", 3307, "cue147", "cue147_app", "secret");
        assertTrue(c.jdbcUrl().startsWith("jdbc:mysql://192.168.1.20:3307/"));
        assertTrue(c.jdbcUrl().contains("connectTimeout=5000"));
    }
}
