package net.vjdv.filecalli.util;

import lombok.extern.slf4j.Slf4j;
import net.vjdv.filecalli.exceptions.CryptException;
import net.vjdv.filecalli.exceptions.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;

@Slf4j
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

    /**
     * Encrypts a input stream to a file using AES/CBC/PKCS5Padding
     *
     * @param input  Input stream
     * @param output Output file
     * @param key    Secret key
     * @throws IOException If an I/O error occurs
     */
    public static void encrypt(InputStream input, Path output, SecretKey key) throws IOException {
        //random iv
        byte[] randomBytes = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);
        //cipher
        try (input) {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(randomBytes));
            //in and out streams
            try (var outputStream = Files.newOutputStream(output)) {
                CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
                //write iv
                outputStream.write(randomBytes);
                //write file
                input.transferTo(cipherOutputStream);
                cipherOutputStream.close();
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException ex) {
            throw new CryptException("Error encrypting file", ex);
        }
    }

    /**
     * Decrypts a input stream to a file using AES/CBC/PKCS5Padding
     *
     * @param input  input stream of encrypted data
     * @param output output file path
     * @param key    secret key
     * @throws IOException if an I/O error occurs
     */
    public static void decrypt(InputStream input, Path output, SecretKey key) throws IOException {
        //read iv
        byte[] iv = new byte[16];
        int readed = input.read(iv);
        if (readed != 16) {
            throw new CryptException("Error reading IV");
        }
        //cipher
        try (input) {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            //in and out streams
            try (var outputStream = Files.newOutputStream(output)) {
                CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
                //write file
                input.transferTo(cipherOutputStream);
                cipherOutputStream.close();
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException ex) {
            throw new CryptException("Error decrypting file", ex);
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
