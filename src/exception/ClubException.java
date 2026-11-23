package exception;

/**
 * Parent of all exceptions that WE create for the club application.
 * The message of these exceptions is written for the user and is shown in a dialog.
 * (SQLException and IOException come from Java itself.)
 */
public class ClubException extends Exception {
    public ClubException(String message) {
        super(message);
    }
}
