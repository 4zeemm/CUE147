# CUE 147 - Snooker & Pool Club Management System
Java Swing + JDBC + MySQL (AOOP college project).

```
./setup-db.sh      # once: creates the database
./run.sh           # starts MySQL if needed, makes today's backup, starts the app
./test.sh          # all automatic tests
```
MySQL 8.4 is installed in `~/cue147-mysql` (no admin rights needed). JDBC driver in `lib/`.
Other scripts: `start-mysql.sh`, `stop-mysql.sh`, `ensure-mysql.sh` (start only if not running), `backup-db.sh` (daily copy in `backups/`, newest 7 kept), `upgrade-db.sh`.

- `src/` packages: `app` `model` `dao` `service` `database` `exception` `util` `ui`
- `sql/schema.sql` database script (+ `sql/upgrade_v2.sql` for an existing database), `test/` automatic tests
- `VIVA.md` older viva notes. `docs/` has the final report (CUE147_Project_Report.docx), presentations (CUE147_Final_Review_Presentation.pptx, Review 3: CUE147_Presentation.pptx), demo script and checklist, testing checklist (CUE147_Testing_Checklist.docx), viva question bank, diagrams, screenshots and `test-results/`
