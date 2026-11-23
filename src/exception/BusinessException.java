package exception;

/** Thrown when a rule of the club is broken (for example: the table already has a running game). */
public class BusinessException extends ClubException {
    public BusinessException(String message) {
        super(message);
    }
}
