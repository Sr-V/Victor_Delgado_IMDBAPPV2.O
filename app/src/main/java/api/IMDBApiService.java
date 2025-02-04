package api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Servicio para interactuar con la API de IMDb.
 * Proporciona métodos para obtener información sobre títulos de películas
 * y manejar múltiples claves de API en caso de que se alcance el límite de solicitudes.
 */
public class IMDBApiService {

    private static final String API_HOST = "imdb-com.p.rapidapi.com"; // Host de la API IMDb
    private final RapidApiKeyManager apiKeyManager; // Gestor de claves API
    private static final int HTTP_TOO_MANY_REQUESTS = 429; // Código HTTP 429: Demasiadas solicitudes
    private static final int HTTP_BAD_GATEWAY = 502; // Código HTTP 502: Bad Gateway

    /**
     * Constructor de la clase. Inicializa el gestor de claves API.
     */
    public IMDBApiService() {
        this.apiKeyManager = new RapidApiKeyManager();
    }

    /**
     * Obtiene los títulos más populares del top meter de IMDb.
     *
     * @return Respuesta JSON en formato String que contiene los títulos más populares.
     * @throws Exception En caso de error durante la solicitud.
     */
    public String getTopMeterTitles() throws Exception {
        // Construir el endpoint para el top meter
        String endpoint = "https://" + API_HOST + "/title/get-top-meter?topMeterTitlesType=ALL&limit=10";
        return makeApiRequest(endpoint); // Realizar la solicitud al endpoint
    }

    /**
     * Obtiene los detalles de un título específico por su ID (tconst).
     *
     * @param tconst ID único del título en IMDb (por ejemplo, "tt0120338").
     * @return Respuesta JSON en formato String que contiene los detalles del título.
     * @throws Exception En caso de error durante la solicitud.
     */
    public String getTitleDetails(String tconst) throws Exception {
        // Construir el endpoint para los detalles del título
        String endpoint = "https://" + API_HOST + "/title/get-overview?tconst=" + tconst;
        return makeApiRequest(endpoint); // Realizar la solicitud al endpoint
    }

    /**
     * Realiza una solicitud GET genérica al endpoint especificado.
     * Cambia automáticamente de clave si se alcanza el límite de solicitudes.
     *
     * @param endpoint URL completa del endpoint de la API.
     * @return Respuesta JSON en formato String devuelta por el servidor.
     * @throws Exception En caso de error durante la solicitud (como errores de conexión o respuestas no exitosas).
     */
    private String makeApiRequest(String endpoint) throws Exception {
        int maxRetries = apiKeyManager.getApiKeysCount(); // Número máximo de intentos igual al número de claves disponibles
        int attempt = 0; // Contador de intentos

        while (attempt < maxRetries) {
            String apiKey = apiKeyManager.getCurrentKey(); // Obtener la clave API actual
            attempt++; // Incrementar el contador de intentos

            try {
                // Crear la URL y abrir una conexión HTTP
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Configurar el método de solicitud como GET
                connection.setRequestMethod("GET");

                // Establecer las cabeceras necesarias para la autenticación con la API
                connection.setRequestProperty("x-rapidapi-key", apiKey); // Clave de la API
                connection.setRequestProperty("x-rapidapi-host", API_HOST); // Host de la API

                // Obtener el código de respuesta del servidor
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Leer y procesar la respuesta si el código de respuesta es 200 (OK)
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    // Leer la respuesta línea por línea
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    // Cerrar el lector y devolver la respuesta
                    reader.close();
                    return response.toString();
                } else if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                    // Si se alcanza el límite de solicitudes, cambia a la siguiente clave
                    apiKeyManager.switchToNextKey();
                } else if (responseCode == HTTP_BAD_GATEWAY) {
                    // Si el servidor devuelve un error 502, se decide reintentar con la misma clave
                    System.err.println("Servidor remoto retornó 502. Reintentando...");
                } else {
                    // Lanzar una excepción si la respuesta no es exitosa
                    throw new Exception("HTTP Error: " + responseCode);
                }
            } catch (Exception e) {
                // Cambiar a la siguiente clave en caso de error
                if (attempt >= maxRetries) {
                    throw new Exception("Todas las claves han sido agotadas. Último error: " + e.getMessage());
                } else {
                    apiKeyManager.switchToNextKey(); // Cambiar a la siguiente clave
                }
            }
        }

        throw new Exception("No se pudo completar la solicitud después de probar todas las claves.");
    }
}