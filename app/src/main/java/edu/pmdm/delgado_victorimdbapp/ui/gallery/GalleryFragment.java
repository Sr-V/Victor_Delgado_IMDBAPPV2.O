package edu.pmdm.delgado_victorimdbapp.ui.gallery;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import api.IMDBApiService;
import api.TMDBApiService;
import database.DatabaseManager;
import database.Movie;
import database.SQLiteHelper;
import edu.pmdm.delgado_victorimdbapp.MovieDetailsActivity;
import edu.pmdm.delgado_victorimdbapp.R;

/**
 * Fragmento para mostrar y gestionar las películas favoritas.
 * Permite compartir datos en formato JSON y mostrar detalles de las películas.
 */
public class GalleryFragment extends Fragment {

    private static final String TAG = "GalleryFragment"; // Etiqueta para logs de depuración

    private GridLayout gridLayout; // Contenedor para mostrar las imágenes de las películas
    private SQLiteHelper dbHelper; // Helper para la gestión de la base de datos
    private BluetoothAdapter bluetoothAdapter; // Adaptador Bluetooth para compartir datos

    // Manejador para solicitar permisos de Bluetooth
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    handleShareButtonClick();
                } else {
                    Toast.makeText(getContext(), "Permisos de Bluetooth denegados", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Infla el layout del fragmento
        View root = inflater.inflate(R.layout.fragment_gallery, container, false);

        gridLayout = root.findViewById(R.id.gridLayout); // Inicializa el GridLayout
        gridLayout.setColumnCount(1); // Configura una sola columna

        Button shareButton = root.findViewById(R.id.shareButton); // Botón para compartir datos
        shareButton.setOnClickListener(v -> handleShareButtonClick());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // Obtiene el adaptador Bluetooth
        initializeDatabaseHelper(); // Inicializa el helper de la base de datos

        loadFavoriteMovies(); // Carga las películas favoritas en el GridLayout
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dbHelper = null; // Limpia la referencia a la base de datos
    }

    /**
     * Inicializa el helper de la base de datos.
     * Cierra cualquier instancia previa de la base de datos.
     */
    private void initializeDatabaseHelper() {
        try {
            DatabaseManager.closeDatabase(); // Cierra la base de datos previa
            dbHelper = DatabaseManager.getInstance(requireContext()); // Inicializa la nueva instancia
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error al inicializar SQLiteHelper: " + e.getMessage());
        }
    }

    /**
     * Maneja el clic del botón de compartir.
     * Activa Bluetooth si no está activo y muestra las películas favoritas en JSON.
     */
    private void handleShareButtonClick() {
        // Verifica permisos de Bluetooth en dispositivos con Android S o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable(); // Activa Bluetooth si no está activo
            Toast.makeText(getContext(), "Activando Bluetooth...", Toast.LENGTH_SHORT).show();
        }

        showFavoriteMoviesJson(); // Muestra las películas favoritas en formato JSON
    }

    /**
     * Muestra las películas favoritas en formato JSON.
     * Si no hay películas, muestra un mensaje de error.
     */
    private void showFavoriteMoviesJson() {
        new Thread(() -> {
            try {
                List<Movie> favoriteMovies = dbHelper.getFavoriteMovies();

                if (favoriteMovies.isEmpty()) {
                    // Si no hay películas, muestra un mensaje en la UI
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "No hay películas en favoritos", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }

                // Construye el JSON con las películas favoritas
                JSONArray moviesJsonArray = buildMoviesJson(favoriteMovies);
                String jsonFormattedString = moviesJsonArray.toString(4); // Formatea el JSON

                // Muestra el JSON en un diálogo
                requireActivity().runOnUiThread(() -> displayJsonDialog(jsonFormattedString));
            } catch (Exception e) {
                Log.e(TAG, "Error al generar el JSON de películas favoritas", e);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Error al generar el JSON", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    /**
     * Construye un JSONArray con los datos de las películas favoritas.
     *
     * @param favoriteMovies Lista de películas favoritas.
     * @return JSONArray con los datos en formato JSON.
     */
    private JSONArray buildMoviesJson(List<Movie> favoriteMovies) {
        IMDBApiService imdbApiService = new IMDBApiService(); // Servicio IMDb
        TMDBApiService tmdbApiService = new TMDBApiService(); // Servicio TMDb
        JSONArray moviesJsonArray = new JSONArray();

        for (Movie movie : favoriteMovies) {
            try {
                JSONObject movieJson = new JSONObject();

                if (movie.getId().startsWith("tt")) {
                    // Datos desde IMDb
                    String jsonResponse = imdbApiService.getTitleDetails(movie.getId());
                    JSONObject movieDetails = new JSONObject(jsonResponse).getJSONObject("data").getJSONObject("title");

                    movieJson.put("id", movie.getId());
                    movieJson.put("title", movieDetails.optJSONObject("titleText") != null
                            ? movieDetails.optJSONObject("titleText").optString("text", "N/A")
                            : "N/A");

                    JSONObject plotObject = movieDetails.optJSONObject("plot");
                    movieJson.put("overview", plotObject != null && plotObject.optJSONObject("plotText") != null
                            ? plotObject.optJSONObject("plotText").optString("plainText", "N/A")
                            : "N/A");

                    JSONObject primaryImageObject = movieDetails.optJSONObject("primaryImage");
                    movieJson.put("posterURL", primaryImageObject != null
                            ? primaryImageObject.optString("url", "N/A")
                            : "N/A");

                    JSONObject ratingsSummaryObject = movieDetails.optJSONObject("ratingsSummary");
                    movieJson.put("rating", ratingsSummaryObject != null
                            ? ratingsSummaryObject.optDouble("aggregateRating", 0.0)
                            : 0.0);

                    JSONObject releaseDateObject = movieDetails.optJSONObject("releaseDate");
                    movieJson.put("releaseDate", formatReleaseDate(releaseDateObject));
                } else {
                    // Datos desde TMDb
                    String response = tmdbApiService.getMovieDetailsById(movie.getId());
                    JSONObject jsonObject = new JSONObject(response);

                    movieJson.put("id", movie.getId());
                    movieJson.put("title", jsonObject.getString("title"));
                    movieJson.put("overview", jsonObject.getString("overview"));
                    movieJson.put("posterURL", "https://image.tmdb.org/t/p/w500" + jsonObject.getString("poster_path"));
                    movieJson.put("rating", jsonObject.getDouble("vote_average"));
                    movieJson.put("releaseDate", jsonObject.getString("release_date"));
                }

                moviesJsonArray.put(movieJson);
            } catch (Exception e) {
                Log.e(TAG, "Error al procesar detalles de la película: " + movie.getId(), e);
            }
        }

        return moviesJsonArray;
    }

    /**
     * Muestra un diálogo con los datos de las películas en formato JSON.
     *
     * @param jsonFormattedString String en formato JSON.
     */
    private void displayJsonDialog(String jsonFormattedString) {
        ScrollView scrollView = new ScrollView(getContext());
        TextView jsonTextView = new TextView(getContext());
        jsonTextView.setText(jsonFormattedString);
        jsonTextView.setPadding(16, 16, 16, 16);
        scrollView.addView(jsonTextView);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Películas Favoritas en JSON")
                .setView(scrollView)
                .setPositiveButton("CERRAR", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    /**
     * Formatea una fecha en formato JSON a un String legible.
     *
     * @param releaseDate Objeto JSON con los datos de la fecha.
     * @return Fecha formateada como String.
     */
    @SuppressLint("DefaultLocale")
    private String formatReleaseDate(JSONObject releaseDate) {
        if (releaseDate == null) return "N/A";
        int year = releaseDate.optInt("year", 0);
        int month = releaseDate.optInt("month", 0);
        int day = releaseDate.optInt("day", 0);
        return year == 0 ? "N/A" : String.format("%d-%02d-%02d", year, month, day);
    }

    /**
     * Carga las películas favoritas desde la base de datos y las muestra en el GridLayout.
     */
    private void loadFavoriteMovies() {
        if (dbHelper == null) {
            Log.e(TAG, "Base de datos no inicializada");
            return;
        }

        List<Movie> favoriteMovies = dbHelper.getFavoriteMovies();
        gridLayout.removeAllViews(); // Limpia el GridLayout

        for (Movie movie : favoriteMovies) {
            addImageToGrid(movie.getCaratula(), movie.getId(), movie.getTitulo());
        }
    }

    /**
     * Agrega una imagen al GridLayout.
     *
     * @param imageUrl URL de la imagen.
     * @param movieId ID de la película.
     * @param title Título de la película.
     */
    private void addImageToGrid(String imageUrl, String movieId, String title) {
        ImageView imageView = new ImageView(getContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 500;
        params.height = 750;
        params.setMargins(0, 16, 0, 16);
        params.setGravity(Gravity.CENTER);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        new Thread(() -> {
            Bitmap bitmap = getBitmapFromURL(imageUrl);
            if (bitmap != null) {
                requireActivity().runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            }
        }).start();

        imageView.setOnLongClickListener(v -> {
            if (dbHelper != null) {
                dbHelper.removeMovieFromFavorites(title);
                Toast.makeText(getContext(), title + " eliminado de favoritos", Toast.LENGTH_SHORT).show();
                loadFavoriteMovies();
            }
            return true;
        });

        imageView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MovieDetailsActivity.class);
            intent.putExtra("MOVIE_ID", movieId);
            intent.putExtra("IMAGE_URL", imageUrl);
            intent.putExtra("TITLE", title);
            startActivity(intent);
        });

        gridLayout.addView(imageView);
    }

    /**
     * Descarga una imagen desde una URL y la convierte en un Bitmap.
     *
     * @param imageUrl URL de la imagen.
     * @return Imagen como Bitmap.
     */
    private Bitmap getBitmapFromURL(String imageUrl) {
        try {
            java.net.URL url = new java.net.URL(imageUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            try (java.io.InputStream input = connection.getInputStream()) {
                return android.graphics.BitmapFactory.decodeStream(input);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener el Bitmap de la URL: " + imageUrl, e);
            return null;
        }
    }
}