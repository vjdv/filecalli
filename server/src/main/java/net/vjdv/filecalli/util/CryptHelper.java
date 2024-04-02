package net.vjdv.filecalli.util;

import net.vjdv.filecalli.exceptions.ServiceException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CryptHelper {

    /**
     * Generates a hash from a text with salt using SHA-256 algorithm
     *
     * @param text Text to hash
     * @return Hashed text
     */
    public static byte[] hashBytes(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            text = Configuration.getInstance().getSalt() + text;
            return digest.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException("Error hashing text", ex);
        }
    }

    public static String bytes2hex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte hashByte : bytes) {
            String hex = Integer.toHexString(0xff & hashByte);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

}
