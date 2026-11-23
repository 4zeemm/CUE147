package exception;

/** Thrown by the Validator when something typed by the user is wrong (empty name, bad phone, bad date ...). */
public class InvalidInputException extends ClubException {
    public InvalidInputException(String message) {
        super(message);
    }
}
