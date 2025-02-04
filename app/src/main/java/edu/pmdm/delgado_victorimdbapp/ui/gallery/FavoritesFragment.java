package edu.pmdm.delgado_victorimdbapp.ui.gallery;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import api.IMDBApiService;
import api.TMDBApiService;
import database.FavoritesSync;
import database.Movie;
import database.SQLiteHelper;
import edu.pmdm.delgado_victorimdbapp.MovieDetailsActivity;
import edu.pmdm.delgado_victorimdbapp.R;

/**
 * Fragmento para mostrar y gestionar las películas favoritas.
 * Permite compartir datos en formato JSON y mostrar detalles de las películas.
 */
public class FavoritesFragment extends Fragment {

    private static final String TAG = "FavoritesFragment"; // Etiqueta para logs de depuración

    private GridLayout gridLayout;       // Contenedor para mostrar las imágenes de las películas
    private SQLiteHelper dbHelper;       // Helper para la gestión de la base de datos
    private BluetoothAdapter bluetoothAdapter; // Adaptador Bluetooth para compartir datos

    // Variable para guardar el userId actual
    private String currentUserId;
    // Instancia para sincronizar favoritos entre SQLite y la nube
    private FavoritesSync favoritesSync;

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

        gridLayout = root.findViewById(R.id.gridLayout);
        gridLayout.setColumnCount(1);  // Configura una sola columna

        Button shareButton = root.findViewById(R.id.shareButton);
        shareButton.setOnClickListener(v -> handleShareButtonClick());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Inicializa la base de datos y asigna currentUserId
        initializeDatabaseHelper();

        // Si hay usuario autenticado, inicializa la sincronización de favoritos en la nube
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            currentUserId = firebaseUser.getUid();
            favoritesSync = new FavoritesSync(requireContext(), currentUserId);
            // Registra el listener para que cada modificación local se sincronice en la nube
            SQLiteHelper.setOnFavoritesChangedListener(new SQLiteHelper.OnFavoritesChangedListener() {
                @Override
                public void onFavoriteAdded(Movie movie) {
                    // Aunque en FavoritesFragment la acción principal es eliminar, se deja para completitud
                    favoritesSync.addMovieToCloud(movie);
                    Log.d(TAG, "Sincronizando adición en la nube: " + movie.getMovie_id());
                }

                @Override
                public void onFavoriteRemoved(String movieId) {
                    favoritesSync.removeMovieFromCloud(movieId);
                    Log.d(TAG, "Sincronizando eliminación en la nube: " + movieId);
                }
            });
            // Sincroniza los datos al iniciar el fragmento
            favoritesSync.syncAtStartup();
        } else {
            Log.e(TAG, "Usuario no autenticado. No se pudo inicializar la sincronización.");
        }

        // Carga las películas favoritas en el GridLayout
        loadFavoriteMovies();

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dbHelper = null;
    }

    /**
     * Inicializa el helper de la base de datos y registra el usuario actual.
     */
    private void initializeDatabaseHelper() {
        dbHelper = SQLiteHelper.getInstance(requireContext());
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            currentUserId = null;
            Log.e(TAG, "Usuario no autenticado. No se encontró userId.");
            return;
        }
        currentUserId = firebaseUser.getUid();
        Log.d(TAG, "Base de datos lista para usar con el userId: " + currentUserId);
    }

    /**
     * Maneja el clic del botón de compartir.
     * Activa Bluetooth si no está activo y muestra las películas favoritas en JSON.
     */
    private void handleShareButtonClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
            Toast.makeText(getContext(), "Activando Bluetooth...", Toast.LENGTH_SHORT).show();
        }
        showFavoriteMoviesJson();
    }

    /**
     * Muestra las películas favoritas en formato JSON para el usuario actual.
     */
    private void showFavoriteMoviesJson() {
        if (currentUserId == null) {
            Toast.makeText(getContext(), "No hay usuario autenticado, no se puede compartir", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                List<Movie> favoriteMovies = dbHelper.getFavoriteMovies(currentUserId);
                if (favoriteMovies.isEmpty()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "No hay películas en favoritos", Toast.LENGTH_SHORT).show()
                    );
                    return;
                }
                JSONArray moviesJsonArray = buildMoviesJson(favoriteMovies);
                String jsonFormattedString = moviesJsonArray.toString(4);
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
     */
    private JSONArray buildMoviesJson(List<Movie> favoriteMovies) {
        IMDBApiService imdbApiService = new IMDBApiService();
        TMDBApiService tmdbApiService = new TMDBApiService();
        JSONArray moviesJsonArray = new JSONArray();
        for (Movie movie : favoriteMovies) {
            try {
                JSONObject movieJson = new JSONObject();
                if (movie.getMovie_id().startsWith("tt")) {
                    String jsonResponse = imdbApiService.getTitleDetails(movie.getMovie_id());
                    JSONObject movieDetails = new JSONObject(jsonResponse)
                            .getJSONObject("data")
                            .getJSONObject("title");
                    movieJson.put("id", movie.getMovie_id());
                    movieJson.put("title",
                            movieDetails.optJSONObject("titleText") != null
                                    ? movieDetails.optJSONObject("titleText").optString("text", "N/A")
                                    : "N/A"
                    );
                    JSONObject plotObject = movieDetails.optJSONObject("plot");
                    movieJson.put("overview", (plotObject != null
                            && plotObject.optJSONObject("plotText") != null)
                            ? plotObject.optJSONObject("plotText").optString("plainText", "N/A")
                            : "N/A"
                    );
                    JSONObject primaryImageObject = movieDetails.optJSONObject("primaryImage");
                    movieJson.put("posterURL", primaryImageObject != null
                            ? primaryImageObject.optString("url", "N/A")
                            : "N/A"
                    );
                    JSONObject ratingsSummaryObject = movieDetails.optJSONObject("ratingsSummary");
                    movieJson.put("rating", ratingsSummaryObject != null
                            ? ratingsSummaryObject.optDouble("aggregateRating", 0.0)
                            : 0.0
                    );
                    JSONObject releaseDateObject = movieDetails.optJSONObject("releaseDate");
                    movieJson.put("releaseDate", formatReleaseDate(releaseDateObject));
                } else {
                    String response = tmdbApiService.getMovieDetailsById(movie.getMovie_id());
                    JSONObject jsonObject = new JSONObject(response);
                    movieJson.put("id", movie.getMovie_id());
                    movieJson.put("title", jsonObject.getString("title"));
                    movieJson.put("overview", jsonObject.getString("overview"));
                    movieJson.put("posterURL", "https://image.tmdb.org/t/p/w500" + jsonObject.getString("poster_path"));
                    movieJson.put("rating", jsonObject.getDouble("vote_average"));
                    movieJson.put("releaseDate", jsonObject.getString("release_date"));
                }
                moviesJsonArray.put(movieJson);
            } catch (Exception e) {
                Log.e(TAG, "Error al procesar detalles de la película: " + movie.getMovie_id(), e);
            }
        }
        return moviesJsonArray;
    }

    /**
     * Formatea una fecha en formato JSON a un String legible.
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
     * Muestra un diálogo con los datos de las películas en formato JSON.
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
     * Carga las películas favoritas del usuario actual y las muestra en el GridLayout.
     */
    private void loadFavoriteMovies() {
        if (dbHelper == null) {
            Log.e(TAG, "Base de datos no inicializada");
            return;
        }
        if (currentUserId == null) {
            Toast.makeText(getContext(), "No se ha identificado al usuario", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Movie> favoriteMovies = dbHelper.getFavoriteMovies(currentUserId);
        requireActivity().runOnUiThread(() -> {
            gridLayout.removeAllViews();
            if (favoriteMovies.isEmpty()) {
                Toast.makeText(getContext(), "No tienes películas favoritas aún", Toast.LENGTH_SHORT).show();
                return;
            }
            for (Movie movie : favoriteMovies) {
                if (movie.getPoster() != null && !movie.getPoster().isEmpty()) {
                    addImageToGrid(movie.getPoster(), movie.getMovie_id(), movie.getTitle());
                }
            }
        });
    }

    /**
     * Agrega una imagen al GridLayout representando una película favorita.
     *
     * @param imageUrl URL de la imagen.
     * @param movieId  ID de la película (ej. "tt1234567").
     * @param title    Título de la película.
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

        // Descarga la imagen en un hilo secundario
        new Thread(() -> {
            Bitmap bitmap = getBitmapFromURL(imageUrl);
            if (bitmap != null) {
                requireActivity().runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            }
        }).start();

        // Al mantener presionada se elimina de favoritos (lo que disparará el listener y sincronizará la nube)
        imageView.setOnLongClickListener(v -> {
            if (dbHelper != null && currentUserId != null) {
                int rowsDeleted = dbHelper.removeMovieFromFavorites(currentUserId, movieId);
                if (rowsDeleted > 0) {
                    Toast.makeText(getContext(), title + " eliminado de favoritos", Toast.LENGTH_SHORT).show();
                    requireActivity().runOnUiThread(this::loadFavoriteMovies);
                } else {
                    Toast.makeText(getContext(), "Error al eliminar " + title, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });

        // Al hacer clic se abren los detalles de la película
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
     * Descarga una imagen desde una URL y devuelve un Bitmap.
     */
    private Bitmap getBitmapFromURL(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.e(TAG, "URL de imagen vacía o nula");
            return null;
        }
        InputStream inputStream = null;
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            inputStream = connection.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();
            connection.disconnect();
            int reqWidth = 1024;
            int reqHeight = 1024;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setDoInput(true);
            connection.connect();
            inputStream = connection.getInputStream();
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap scaledBitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();
            connection.disconnect();
            return scaledBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error al descargar/decodificar la imagen: " + imageUrl, e);
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {}
            }
            return null;
        }
    }

    /**
     * Calcula un inSampleSize adecuado para decodificar la imagen.
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}