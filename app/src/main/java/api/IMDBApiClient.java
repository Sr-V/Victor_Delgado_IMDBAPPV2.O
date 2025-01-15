package api;

/**
 * Cliente para interactuar con IMDBApiService y manejar claves API.
 * Centraliza la lógica de acceso al servicio.
 */
public class IMDBApiClient {

    private static IMDBApiService apiService; // Instancia de IMDBApiService
    private static final RapidApiKeyManager apiKeyManager = new RapidApiKeyManager(); // Gestor de claves API

    /**
     * Obtiene una instancia de IMDBApiService configurada.
     *
     * @return Una instancia de IMDBApiService.
     */
    public static IMDBApiService getApiService() {
        if (apiService == null) {
            apiService = new IMDBApiService();
        }
        return apiService;
    }

    /**
     * Obtiene la clave API actual.
     *
     * @return Clave API actual como String.
     */
    public static String getApiKey() {
        return apiKeyManager.getCurrentKey();
    }

    /**
     * Cambia a la siguiente clave API en caso de error o límite alcanzado.
     */
    public static void switchApiKey() {
        apiKeyManager.switchToNextKey();
    }
}