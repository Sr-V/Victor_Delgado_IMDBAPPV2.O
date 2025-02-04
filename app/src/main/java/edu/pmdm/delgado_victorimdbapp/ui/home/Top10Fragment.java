package edu.pmdm.delgado_victorimdbapp.ui.home;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import api.IMDBApiService;
import database.FavoritesSync; // Asegúrate de tener la clase FavoritesSync en el paquete correcto.
import database.Movie;
import database.SQLiteHelper;
import edu.pmdm.delgado_victorimdbapp.MovieDetailsActivity;
import edu.pmdm.delgado_victorimdbapp.R;

/**
 * Fragmento para mostrar las películas más populares desde IMDb.
 * Permite agregar películas a favoritos y acceder a detalles.
 */
public class Top10Fragment extends Fragment {

    private static final String TAG = "Top10Fragment";
    private GridLayout gridLayout;              // Contenedor para las imágenes de películas
    private IMDBApiService imdbApiService;      // Servicio de API de IMDb
    private SQLiteHelper dbHelper;              // Helper para la base de datos

    private String currentUserId;
    // Instancia para sincronizar favoritos entre SQLite y la nube
    private FavoritesSync favoritesSync;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        gridLayout = root.findViewById(R.id.gridLayout);
        imdbApiService = new IMDBApiService(); // Inicializa el servicio IMDb

        // Inicializa la base de datos y registra el usuario actual
        initializeDatabaseHelper();

        // Si hay usuario autenticado, inicializa la sincronización
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            currentUserId = firebaseUser.getUid();
            // Crea la instancia de FavoritesSync
            favoritesSync = new FavoritesSync(requireContext(), currentUserId);
            // Registra el listener para que cada vez que se agregue o elimine un favorito se sincronice en la nube
            SQLiteHelper.setOnFavoritesChangedListener(new SQLiteHelper.OnFavoritesChangedListener() {
                @Override
                public void onFavoriteAdded(Movie movie) {
                    favoritesSync.addMovieToCloud(movie);
                    Log.d(TAG, "Sincronizando adición en la nube: " + movie.getMovie_id());
                }

                @Override
                public void onFavoriteRemoved(String movieId) {
                    favoritesSync.removeMovieFromCloud(movieId);
                    Log.d(TAG, "Sincronizando eliminación en la nube: " + movieId);
                }
            });
            // Sincroniza al inicio
            favoritesSync.syncAtStartup();
        } else {
            Log.e(TAG, "Usuario no autenticado. No se pudo inicializar la sincronización.");
        }

        // Carga las películas populares
        loadTopMeterImages();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dbHelper = null; // Limpia la referencia
    }

    /**
     * Inicializa el helper de la base de datos y registra el usuario actual.
     */
    private void initializeDatabaseHelper() {
        // Inicializa la base de datos
        dbHelper = SQLiteHelper.getInstance(requireContext());

        // Obtener el usuario actual de FirebaseAuth
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            currentUserId = null;
            Log.e(TAG, "Usuario no autenticado. No se encontró userId.");
            return;
        }

        // Obtener el userId del usuario autenticado
        currentUserId = firebaseUser.getUid();
        Log.d(TAG, "Base de datos lista para usar con el userId: " + currentUserId);
    }

    /**
     * Carga las imágenes de las películas populares desde la API de IMDb.
     */
    private void loadTopMeterImages() {
        new Thread(() -> {
            try {
                // Obtén los títulos más populares
                String response = imdbApiService.getTopMeterTitles();

                // Procesa los datos JSON
                List<Movie> movies = parseMovieData(response);

                // Agrega cada movie al grid en el hilo principal
                requireActivity().runOnUiThread(() -> {
                    for (Movie movie : movies) {
                        addImageToGrid(movie.getPoster(), movie.getMovie_id(), movie.getTitle());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error al cargar imágenes", e);
            }
        }).start();
    }

    private List<Movie> parseMovieData(String response) {
        List<Movie> movies = new ArrayList<>();
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONObject data = jsonResponse.optJSONObject("data");
            if (data == null) return movies;

            JSONObject topMeterTitles = data.optJSONObject("topMeterTitles");
            if (topMeterTitles == null) return movies;

            JSONArray edges = topMeterTitles.optJSONArray("edges");
            if (edges == null) return movies;

            for (int i = 0; i < edges.length(); i++) {
                JSONObject nodeWrapper = edges.optJSONObject(i);
                if (nodeWrapper == null) continue;

                JSONObject node = nodeWrapper.optJSONObject("node");
                if (node == null) continue;

                String id = node.optString("id", "Sin ID");

                JSONObject titleText = node.optJSONObject("titleText");
                String title = (titleText != null) ? titleText.optString("text", "Título desconocido") : "Título desconocido";

                JSONObject primaryImage = node.optJSONObject("primaryImage");
                String imageUrl = (primaryImage != null) ? primaryImage.optString("url", "") : "";

                if (!imageUrl.isEmpty()) {
                    movies.add(new Movie(id, imageUrl, title));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al analizar datos de películas", e);
        }
        return movies;
    }

    /**
     * Agrega la imagen de una película al GridLayout.
     *
     * @param imageUrl URL de la imagen
     * @param movieId  ID de la película (e.g. "tt1234567")
     * @param title    Título de la película
     */
    private void addImageToGrid(String imageUrl, String movieId, String title) {
        ImageView imageView = new ImageView(getContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 500;
        params.height = 750;
        params.setMargins(16, 16, 16, 16);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // Descarga la imagen en un hilo secundario
        new Thread(() -> {
            Bitmap bitmap = getBitmapFromURL(imageUrl);
            if (bitmap != null) {
                requireActivity().runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            }
        }).start();

        // Listener para agregar a favoritos al mantener presionado
        imageView.setOnLongClickListener(v -> {
            if (dbHelper != null && currentUserId != null) {
                if (dbHelper.isMovieFavorite(currentUserId, movieId)) {
                    Toast.makeText(getContext(), title + " ya está en favoritos", Toast.LENGTH_SHORT).show();
                } else {
                    dbHelper.addMovieToFavorites(currentUserId, movieId, imageUrl, title);
                    Toast.makeText(getContext(), "Agregada a favoritos: " + title, Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "SQLiteHelper no inicializado o userId es null.");
            }
            return true;
        });

        // Listener para abrir la actividad de detalles al hacer clic
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
     * Descarga y decodifica la imagen desde la URL.
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

            // Primer pase: obtener dimensiones
            inputStream = connection.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();
            connection.disconnect();

            // Calcular inSampleSize
            int reqWidth = 1024;
            int reqHeight = 1024;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Segundo pase: decodificar
            connection = (HttpURLConnection) url.openConnection();
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
            Log.e(TAG, "Error al descargar/decodificar imagen: " + imageUrl, e);
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
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}