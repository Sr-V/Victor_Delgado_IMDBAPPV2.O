package edu.pmdm.delgado_victorimdbapp.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import api.TMDBApiService;
import edu.pmdm.delgado_victorimdbapp.MovieListActivity;
import edu.pmdm.delgado_victorimdbapp.R;

/**
 * Fragmento para buscar películas por género y año.
 * Carga los géneros disponibles desde TMDB y realiza una búsqueda basada en los parámetros seleccionados.
 */
public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment"; // Etiqueta para logs de depuración

    private Spinner spinnerGenres; // Desplegable para seleccionar género
    private EditText editTextYear; // Campo para ingresar el año

    private final Map<String, Integer> genresMap = new HashMap<>(); // Mapa para almacenar géneros y sus IDs

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflar el layout del fragmento
        View root = inflater.inflate(R.layout.fragment_slideshow, container, false);

        spinnerGenres = root.findViewById(R.id.spinner_genres); // Inicializar el spinner de géneros
        editTextYear = root.findViewById(R.id.edit_text_year); // Inicializar el campo de texto para el año
        Button buttonSearch = root.findViewById(R.id.button_search); // Botón para realizar la búsqueda

        // Cargar géneros en el spinner
        loadGenres();

        // Configurar la acción del botón de búsqueda
        buttonSearch.setOnClickListener(v -> {
            String selectedGenre = (String) spinnerGenres.getSelectedItem(); // Obtener el género seleccionado
            String year = editTextYear.getText().toString(); // Obtener el año ingresado

            // Validar el año (debe ser de 4 dígitos)
            if (year.length() != 4 || !year.matches("\\d+")) {
                Toast.makeText(getContext(), "Introduce un año válido de 4 cifras", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validar que el género sea válido
            if (selectedGenre == null || !genresMap.containsKey(selectedGenre)) {
                Toast.makeText(getContext(), "Selecciona un género válido", Toast.LENGTH_SHORT).show();
                return;
            }

            // Obtener el ID del género y validar que no sea nulo
            Integer genreId = genresMap.get(selectedGenre);
            if (genreId == null) {
                Toast.makeText(getContext(), "No se pudo obtener el ID del género", Toast.LENGTH_SHORT).show();
                return;
            }

            // Realizar la búsqueda
            performSearch(genreId, year);
        });

        return root;
    }

    /**
     * Carga los géneros de películas desde la API de TMDB y los muestra en el spinner.
     */
    private void loadGenres() {
        new Thread(() -> {
            try {
                TMDBApiService apiService = new TMDBApiService();
                String genresJson = apiService.getGenres(); // Obtener géneros desde la API

                JSONObject jsonObject = new JSONObject(genresJson);
                JSONArray genresArray = jsonObject.getJSONArray("genres");

                List<String> genreNames = new ArrayList<>(); // Lista para los nombres de géneros
                for (int i = 0; i < genresArray.length(); i++) {
                    JSONObject genreObject = genresArray.getJSONObject(i);
                    int id = genreObject.getInt("id");
                    String name = genreObject.getString("name");

                    genresMap.put(name, id); // Guardar el nombre y el ID en el mapa
                    genreNames.add(name); // Agregar el nombre a la lista
                }

                // Actualizar el spinner en el hilo principal
                requireActivity().runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_spinner_item, genreNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerGenres.setAdapter(adapter);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error al cargar los géneros", e);
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Error al cargar los géneros", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Realiza una búsqueda de películas por género y año utilizando la API de TMDB.
     *
     * @param genreId ID del género seleccionado.
     * @param year Año en el que se lanzaron las películas.
     */
    private void performSearch(int genreId, String year) {
        new Thread(() -> {
            try {
                TMDBApiService apiService = new TMDBApiService();
                String moviesJson = apiService.getMoviesByGenreAndYear(genreId, Integer.parseInt(year)); // Buscar películas

                JSONObject jsonObject = new JSONObject(moviesJson);
                JSONArray results = jsonObject.getJSONArray("results");

                // Listas para almacenar los resultados
                ArrayList<String> posterUrls = new ArrayList<>();
                ArrayList<String> titles = new ArrayList<>();
                ArrayList<String> tconsts = new ArrayList<>();

                // Procesar los resultados de la búsqueda
                for (int i = 0; i < results.length(); i++) {
                    JSONObject movie = results.getJSONObject(i);

                    String id = String.valueOf(movie.getInt("id")); // ID de la película
                    String title = movie.optString("title", "Título no disponible"); // Título
                    String posterPath = movie.optString("poster_path", ""); // URL del póster

                    if (!posterPath.isEmpty()) {
                        posterUrls.add("https://image.tmdb.org/t/p/w500" + posterPath);
                        titles.add(title);
                        tconsts.add(id);
                    }
                }

                // Si no hay resultados, mostrar un mensaje
                if (posterUrls.isEmpty()) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "No se encontraron películas", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Enviar los resultados a la siguiente actividad
                Intent intent = new Intent(requireContext(), MovieListActivity.class);
                intent.putStringArrayListExtra("POSTER_URLS", posterUrls);
                intent.putStringArrayListExtra("TITLES", titles);
                intent.putStringArrayListExtra("TCONSTS", tconsts);
                startActivity(intent);

            } catch (Exception e) {
                Log.e(TAG, "Error al buscar películas", e);
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Error al buscar películas", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}