package api;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Clase que gestiona múltiples claves API de RapidAPI.
 * Permite cambiar entre claves para evitar el límite de solicitudes.
 */
public class RapidApiKeyManager {

    private final List<String> apiKeys; // Lista de claves API disponibles
    private int currentKeyIndex; // Índice de la clave API actual
    private static final String TAG = "RapidApiKeyManager"; // Etiqueta para Logcat

    /**
     * Constructor de la clase. Inicializa las claves API.
     */
    public RapidApiKeyManager() {
        apiKeys = new ArrayList<>();
        currentKeyIndex = 0;

        // Agrega tus claves de API aquí
        apiKeys.add("7b8272cc5amshb1aa2d315d97673p1a2950jsn1ec70cb6974f");
        apiKeys.add("c770c35ad3msh3e5af9fb3f4063fp185b3ejsnf8f717dcd6db");
        apiKeys.add("d96f7dfb4amsh8f58b26d4370130p1f1c87jsn9544897da46d");

        // Log para mostrar que las claves se han inicializado
        Log.d(TAG, "RapidApiKeyManager inicializado con " + apiKeys.size() + " claves API.");
    }

    /**
     * Obtiene la clave API actual.
     *
     * @return Clave API actual como String.
     */
    public String getCurrentKey() {
        // Log para mostrar la clave API seleccionada
        Log.d(TAG, "Clave API actual seleccionada: " + apiKeys.get(currentKeyIndex));
        return apiKeys.get(currentKeyIndex);
    }

    /**
     * Cambia a la siguiente clave API en la lista.
     */
    public void switchToNextKey() {
        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
        // Log para mostrar el cambio de clave API
        Log.d(TAG, "Se ha cambiado a la siguiente clave API: " + apiKeys.get(currentKeyIndex));
    }

    /**
     * Obtiene el número total de claves API disponibles.
     *
     * @return Número de claves en la lista.
     */
    public int getApiKeysCount() {
        return apiKeys.size();
    }
}