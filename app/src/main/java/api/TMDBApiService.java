package api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TMDBApiService {

    private static final String API_KEY = "104fec1644296318fbf9466c2de0935b";
    private static final String BASE_URL = "https://api.themoviedb.org/3";

    /**
     * Realiza una solicitud GET para obtener todos los géneros de películas desde la API de TMDB.
     *
     * @return Respuesta JSON en formato String que contiene todos los géneros de películas.
     * @throws Exception En caso de error durante la solicitud HTTP.
     */
    public String getGenres() throws Exception {
        String endpoint = BASE_URL + "/genre/movie/list?api_key=" + API_KEY;
        return makeApiRequest(endpoint);
    }

    /**
     * Realiza una solicitud GET para obtener películas filtradas por género y año.
     *
     * @param genreId ID del género para filtrar las películas.
     * @param year Año específico de las películas que se desean obtener.
     * @return Respuesta JSON en formato String que contiene una lista de películas.
     * @throws Exception En caso de error durante la solicitud HTTP.
     */
    public String getMoviesByGenreAndYear(int genreId, int year) throws Exception {
        String endpoint = BASE_URL + "/discover/movie?with_genres=" + genreId
                + "&primary_release_year=" + year
                + "&language=en-US&api_key=" + API_KEY;
        return makeApiRequest(endpoint);
    }

    /**
     * Realiza una solicitud GET para obtener los detalles de una película usando su ID.
     *
     * @param movieId ID único de la película en TMDB.
     * @return Respuesta JSON en formato String con los detalles de la película.
     * @throws Exception En caso de error durante la solicitud HTTP.
     */
    public String getMovieDetailsById(String movieId) throws Exception {
        String endpoint = BASE_URL + "/movie/" + movieId + "?api_key=" + API_KEY + "&language=en-US";
        return makeApiRequest(endpoint);
    }

    /**
     * Método genérico para realizar solicitudes GET a un endpoint de la API.
     *
     * @param endpoint URL del endpoint de la API al que se hará la solicitud.
     * @return Respuesta JSON en formato String.
     * @throws Exception En caso de error durante la solicitud HTTP.
     */
    private String makeApiRequest(String endpoint) throws Exception {
        // Crear una URL a partir del endpoint
        URL url = new URL(endpoint);
        // Abrir la conexión HTTP
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Configurar método y encabezados
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization",
                "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxMDRmZWMxNjQ0Mjk2MzE4ZmJmOTQ2NmMyZGUwOTM1YiIsIm5iZiI6MTczNjY0NzU0OS40NTksInN1YiI6IjY3ODMyMzdkYzgxYWNhYTYzZGJiYmI1NiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.b0ryv2z-3nyVdScYG2g8SO4bWRYW-KXfDGRBEkMwT-o");
        connection.setRequestProperty("Content-Type", "application/json");

        // Verificar el código de respuesta HTTP
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Leer la respuesta en caso de éxito
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } else {
            // Lanzar excepción en caso de error HTTP
            throw new Exception("HTTP Error: " + responseCode);
        }
    }
}