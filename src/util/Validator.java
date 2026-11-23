package util;

import exception.InvalidInputException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Checks everything the user types. Every form calls this class, so the rules
 * and the error messages are the same everywhere.
 * A Pattern is a "regular expression": a short rule that describes what text is allowed.
 */
public class Validator {
    private static final Pattern PHONE = Pattern.compile("\\d{10}");
    private static final Pattern PERSON_NAME = Pattern.compile("[A-Za-z][A-Za-z .'-]{1,59}");
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,20}");

    public static String requireNotEmpty(String value, String fieldName) throws InvalidInputException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(fieldName + " is required.");
        }
        return value.trim();
    }

    /** A person's name: letters, spaces, dots, apostrophes and hyphens, 2 to 60 characters. */
    public static String requireName(String value) throws InvalidInputException {
        String name = requireNotEmpty(value, "Name");
        if (!PERSON_NAME.matcher(name).matches()) {
            throw new InvalidInputException("Name must contain only letters and spaces (2 to 60 characters).");
        }
        return name;
    }

    /** A label such as a table name ("Pool Table 4"): anything, but 1 to 40 characters. */
    public static String requireLabel(String value, String fieldName) throws InvalidInputException {
        String text = requireNotEmpty(value, fieldName);
        if (text.length() > 40) {
            throw new InvalidInputException(fieldName + " must be at most 40 characters.");
        }
        return text;
    }

    public static String requirePhone(String value) throws InvalidInputException {
        String phone = requireNotEmpty(value, "Phone number");
        if (!PHONE.matcher(phone).matches()) {
            throw new InvalidInputException("Phone number must be exactly 10 digits.");
        }
        return phone;
    }

    public static String requireUsername(String value) throws InvalidInputException {
        String username = requireNotEmpty(value, "Username");
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidInputException("Username must have 3 to 20 characters: small letters, digits or _ only.");
        }
        return username;
    }

    public static void requirePassword(String password) throws InvalidInputException {
        if (password == null || password.length() < 6) {
            throw new InvalidInputException("Password must have at least 6 characters.");
        }
    }

    /** Turns "2026-11-04" into a LocalDate or throws a friendly exception. */
    public static LocalDate requireDate(String text) throws InvalidInputException {
        try {
            return LocalDate.parse(requireNotEmpty(text, "Date"));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Please enter the date as yyyy-MM-dd, for example " + LocalDate.now() + ".");
        }
    }

    public static void requireNotPast(LocalDate date) throws InvalidInputException {
        if (date.isBefore(LocalDate.now())) throw new InvalidInputException("The date cannot be in the past.");
    }

    public static void requireNotFuture(LocalDate date) throws InvalidInputException {
        if (date.isAfter(LocalDate.now())) throw new InvalidInputException("The date cannot be in the future.");
    }

    public static BigDecimal requirePositive(BigDecimal amount, String fieldName) throws InvalidInputException {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidInputException(fieldName + " must be more than zero.");
        }
        return amount;
    }
}
