package service;

import dao.UserDAO;
import exception.BusinessException;
import exception.ClubException;
import exception.InvalidInputException;
import util.AppLogger;
import model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import util.Validator;

/**
 * Login logic. Passwords are never stored as plain text.
 * We store "salt:hash" where
 *   salt = random bytes (so two users with the same password get different hashes)
 *   hash = PBKDF2 (a slow, secure hashing method that is built into Java).
 */
public class AuthService {
    private static final int ITERATIONS = 65536;
    private static final int KEY_BITS = 256;
    private final UserDAO userDAO = new UserDAO();

    /** Creates the first owner and staff accounts when the users table is empty. */
    public void createDefaultUsers() throws SQLException {
        if (userDAO.countUsers() == 0) {
            userDAO.insert("owner", "Club Owner", hashPassword("owner123"), "OWNER");
            userDAO.insert("staff", "Counter Staff", hashPassword("staff123"), "STAFF");
        }
    }

    /** Returns the logged-in User (an Owner or a Staff object) or throws BusinessException. */
    public User login(String username, String password) throws SQLException, BusinessException {
        User user = userDAO.findByUsername(username);
        // same message for "no such user" and "wrong password" so nobody can guess usernames
        if (user == null || !verifyPassword(password, user.getPasswordHash())) {
            throw new BusinessException("Wrong username or password.");
        }
        if (!user.isActive()) {
            throw new BusinessException("This account has been deactivated. Please contact the owner.");
        }
        return user;
    }

    /** Validator throws InvalidInputException (a ClubException) for wrong input, so we declare ClubException. */
    public void addUser(String username, String fullName, String password, String role) throws SQLException, ClubException {
        username = Validator.requireUsername(username);
        fullName = Validator.requireName(fullName);
        Validator.requirePassword(password);
        if (userDAO.findByUsername(username) != null) throw new BusinessException("This username is already taken.");
        userDAO.insert(username, fullName, hashPassword(password), role);
    }

    public void resetPassword(int userId, String newPassword) throws SQLException, ClubException {
        Validator.requirePassword(newPassword);
        userDAO.updatePassword(userId, hashPassword(newPassword));
    }

    public String hashPassword(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean verifyPassword(String password, String stored) {
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual = pbkdf2(password, salt);
        return MessageDigest.isEqual(expected, actual);   // compares without leaking timing information
    }

    /** Any user can change their own password. The current password must be typed first. */
    public void changeOwnPassword(User user, String currentPassword, String newPassword, String repeatPassword)
            throws SQLException, ClubException {
        User stored = userDAO.findByUsername(user.getUsername());   // read the saved hash again
        if (stored == null || !verifyPassword(currentPassword, stored.getPasswordHash())) {
            throw new BusinessException("The current password is wrong.");
        }
        Validator.requirePassword(newPassword);
        if (!newPassword.equals(repeatPassword)) throw new InvalidInputException("The two new passwords are not the same.");
        if (newPassword.equals(currentPassword)) throw new InvalidInputException("The new password must be different from the old one.");
        userDAO.updatePassword(stored.getId(), hashPassword(newPassword));
        AppLogger.info("User " + user.getUsername() + " changed their own password");
    }

    private byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        }
    }
}
