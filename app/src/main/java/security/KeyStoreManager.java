package security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyStoreManager {

    private static final String KEY_ALIAS = "my_key_alias";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12; // 12 bytes para GCM (96 bits)
    private static final int TAG_LENGTH = 128; // Longitud de etiqueta en bits (recomendado)

    public KeyStoreManager() {
        try {
            generateKeyIfNeeded();
        } catch (Exception e) {
            Log.e("KeyStoreManager", "Error al generar la clave: ", e);
        }
    }

    /**
     * Genera la clave simétrica en el AndroidKeyStore si no existe.
     */
    private void generateKeyIfNeeded() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
            KeyGenParameterSpec keyGenSpec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .build();
            keyGenerator.init(keyGenSpec);
            keyGenerator.generateKey();
        }
    }

    /**
     * Obtiene la clave secreta del AndroidKeyStore.
     */
    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    /**
     * Encripta el texto plano y retorna el resultado en Base64.
     *
     * @param plainText Texto sin cifrar.
     * @return Texto cifrado codificado en Base64 o null si ocurre un error.
     */
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepara un buffer para concatenar el IV y el texto cifrado
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            byte[] combined = byteBuffer.array();

            // Retorna la cadena codificada en Base64
            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e("KeyStoreManager", "Error encriptando: ", e);
            return null;
        }
    }

    /**
     * Desencripta el texto cifrado (en Base64) y retorna el texto plano.
     *
     * @param cipherTextBase64 Texto cifrado codificado en Base64.
     * @return Texto plano o null si ocurre un error.
     */
    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            Log.e("KeyStoreManager", "Intento de desencriptar datos vacíos o nulos.");
            return ""; // 🔹 Devolver cadena vacía en lugar de lanzar excepción
        }

        try {
            byte[] combined = Base64.decode(cipherTextBase64, Base64.DEFAULT);

            // 🔹 Validar si los datos son lo suficientemente largos (IV_SIZE + mínimo 1 byte de datos cifrados)
            if (combined.length < IV_SIZE + 1) {
                Log.e("KeyStoreManager", "Error: Datos encriptados inválidos o corruptos.");
                return ""; // 🔹 Devolver cadena vacía si los datos son insuficientes
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);

            // 🔹 Extraer el IV
            byte[] iv = new byte[IV_SIZE];
            byteBuffer.get(iv);  // Leer IV

            // 🔹 Extraer el texto cifrado
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e("KeyStoreManager", "Error desencriptando: ", e);
            return ""; // 🔹 Devolver cadena vacía en caso de error para evitar crasheos
        }
    }
}