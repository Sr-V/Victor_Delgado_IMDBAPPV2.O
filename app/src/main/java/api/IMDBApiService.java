package api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Servicio para interactuar con la API de IMDb.
 * Proporciona métodos para obtener información sobre títulos de películas
 * y realizar solicitudes a los endpoints disponibles en IMDb.
 */
public class IMDBApiService {

    // Clave de API proporcionada por IMDb para autenticación
    private static final String API_KEY = "d7c42b5c70msha1a8f29b41380d3p1c344bjsn1a47f1b29511";

    // Host de la API IMDb
    private static final String API_HOST = "imdb-com.p.rapidapi.com";

    /**
     * Realiza una solicitud para obtener los títulos del top meter de IMDb.
     *
     * @return Respuesta JSON en formato String que contiene los títulos más populares.
     * @throws Exception En caso de error durante la solicitud (como errores de red o respuesta no válida).
     */
    public String getTopMeterTitles() throws Exception {
        // Construir el endpoint para el top meter
        String endpoint = "https://" + API_HOST + "/title/get-top-meter?topMeterTitlesType=ALL";
        return makeApiRequest(endpoint); // Realizar la solicitud al endpoint
    }

    /**
     * Realiza una solicitud para obtener los detalles de un título específico por su ID (tconst).
     *
     * @param tconst ID único del título en IMDb (por ejemplo, "tt0120338").
     * @return Respuesta JSON en formato String que contiene los detalles del título.
     * @throws Exception En caso de error durante la solicitud (como errores de red o respuesta no válida).
     */
    public String getTitleDetails(String tconst) throws Exception {
        // Construir el endpoint para los detalles del título
        String endpoint = "https://" + API_HOST + "/title/get-overview?tconst=" + tconst;
        return makeApiRequest(endpoint); // Realizar la solicitud al endpoint
    }

    /**
     * Realiza una solicitud GET genérica al endpoint especificado.
     * Configura los encabezados necesarios para la autenticación con la API de IMDb.
     *
     * @param endpoint URL completa del endpoint de la API.
     * @return Respuesta JSON en formato String devuelta por el servidor.
     * @throws Exception En caso de error durante la solicitud (como errores de conexión o respuestas no exitosas).
     */
    private String makeApiRequest(String endpoint) throws Exception {
        // Crear la URL y abrir una conexión HTTP
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Configurar el método de solicitud como GET
        connection.setRequestMethod("GET");

        // Establecer las cabeceras necesarias para la autenticación con la API
        connection.setRequestProperty("x-rapidapi-key", API_KEY); // Clave de la API
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
        } else {
            // Lanzar una excepción si la respuesta no es exitosa
            throw new Exception("HTTP Error: " + responseCode);
        }
    }
}