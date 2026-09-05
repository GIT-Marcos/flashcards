package com.cards.api.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utilidad estática de cifrado AES-256-GCM, parametrizable por clave.
 *
 * Es idéntica en algoritmo a {@link com.cards.api.config.encryption.AesEncryptionConverter},
 * pero con una diferencia clave: recibe la clave como par&aacute;metro en cada llamada,
 * en lugar de leerla de {@code API_KEY_ENCRYPTION_SECRET} al construirse.
 *
 * <p><b>&iquest;Por qu&eacute; existe si ya hay un Converter?</b>
 * <br>
 * {@code AesEncryptionConverter} es un {@code AttributeConverter} de JPA vinculado a
 * {@code EncryptionProperties} (V1). En operaci&oacute;n normal es transparente y correcto.
 * Pero durante una <b>rotaci&oacute;n de clave maestra</b> necesitamos cifrar con una clave
 * <i>distinta</i> (V2) sin que JPA re-aplique el converter con V1 al flush.
 *
 * <p>Flujo en {@code KeyReEncryptionRunner}:
 * <ol>
 *   <li>{@code repository.findAll()} &rarr; JPA invoca el converter &rarr; descifra con V1
 *   <li>{@code EncryptionUtil.encrypt(plaintext, v2Secret)} &rarr; cifra con V2
 *   <li>{@code jdbcTemplate.update("UPDATE user_api_keys SET encrypted_key = ? WHERE id = ?", ...)}
 *       &rarr; bypassea el converter, persiste el cifrado con V2
 * </ol>
 *
 * <p>Esta clase es <b>temporal</b> &mdash; existe solo para la ventana de rotaci&oacute;n.
 * Una vez que {@code API_KEY_ENCRYPTION_SECRET} apunte a la nueva clave y se elimine
 * el runner, este c&oacute;digo puede eliminarse. El converter vuelve a ser la &uacute;nica v&iacute;a.
 */
public final class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private EncryptionUtil() {
    }

    public static String encrypt(String plaintext, String base64Key) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(IV_LENGTH + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encrypt with provided key", e);
        }
    }

    public static String decrypt(String cipherBase64, String base64Key) {
        if (cipherBase64 == null) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            byte[] combined = Base64.getDecoder().decode(cipherBase64);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decrypt with provided key", e);
        }
    }
}
