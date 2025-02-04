package edu.pmdm.delgado_victorimdbapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.FavoritesSync;
import database.SQLiteHelper;

/**
 * Actividad para mostrar una lista de películas en un RecyclerView con soporte para
 * agregar a favoritos y ver detalles de cada película.
 */
public class MovieListActivity extends AppCompatActivity {

    private static final String TAG = "MovieListActivity";

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private SQLiteHelper dbHelper;  // Helper para la base de datos local
    private String currentUserId;   // userId del usuario autenticado
    private FavoritesSync favoritesSync; // Instancia para sincronizar favoritos en la nube

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar el RecyclerView
        RecyclerView recyclerView = createRecyclerView();
        setContentView(recyclerView);

        // Inicializar SQLiteHelper y obtener el userId
        initializeDatabaseHelper();

        // Si el usuario está autenticado, inicializa la sincronización en la nube
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            currentUserId = firebaseUser.getUid();
            favoritesSync = new FavoritesSync(this, currentUserId);
            // Registra el listener para que, al agregar o eliminar un favorito, se sincronice la nube
            SQLiteHelper.setOnFavoritesChangedListener(new SQLiteHelper.OnFavoritesChangedListener() {
                @Override
                public void onFavoriteAdded(database.Movie movie) {
                    favoritesSync.addMovieToCloud(movie);
                    Log.d(TAG, "Synced addition in cloud: " + movie.getMovie_id());
                }

                @Override
                public void onFavoriteRemoved(String movieId) {
                    favoritesSync.removeMovieFromCloud(movieId);
                    Log.d(TAG, "Synced removal in cloud: " + movieId);
                }
            });
        } else {
            Log.e(TAG, "Usuario no autenticado.");
        }

        // Obtener datos de películas desde el Intent
        ArrayList<String> posterUrls = getIntent().getStringArrayListExtra("POSTER_URLS");
        ArrayList<String> titles = getIntent().getStringArrayListExtra("TITLES");
        ArrayList<String> tconsts = getIntent().getStringArrayListExtra("TCONSTS");

        if (posterUrls == null || posterUrls.isEmpty() ||
                titles == null || titles.isEmpty() ||
                tconsts == null || tconsts.isEmpty()) {
            Toast.makeText(this, "No se encontraron películas", Toast.LENGTH_SHORT).show();
            return;
        }

        // Configurar el adaptador del RecyclerView
        recyclerView.setAdapter(new MovieAdapter(posterUrls, titles, tconsts));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper = null;
        executorService.shutdown();
    }

    /**
     * Inicializa el helper de la base de datos y obtiene el userId del usuario autenticado.
     */
    private void initializeDatabaseHelper() {
        dbHelper = SQLiteHelper.getInstance(this);
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
     * Crea y configura un RecyclerView con un GridLayoutManager.
     */
    private RecyclerView createRecyclerView() {
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2)); // 2 columnas
        return recyclerView;
    }

    /**
     * Adaptador para mostrar la lista de películas en el RecyclerView.
     */
    private class MovieAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final ArrayList<String> posterUrls; // URLs de los pósters
        private final ArrayList<String> titles;     // Títulos de las películas
        private final ArrayList<String> tconsts;      // IDs de las películas (por ejemplo, "tt1234567")

        public MovieAdapter(ArrayList<String> posterUrls, ArrayList<String> titles, ArrayList<String> tconsts) {
            this.posterUrls = posterUrls;
            this.titles = titles;
            this.tconsts = tconsts;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Crear un ImageView para mostrar cada póster
            ImageView imageView = new ImageView(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    750 // Altura fija
            );
            params.setMargins(16, 16, 16, 16);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new RecyclerView.ViewHolder(imageView) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ImageView imageView = (ImageView) holder.itemView;
            String imageUrl = posterUrls.get(position);
            String title = titles.get(position);
            String tconst = tconsts.get(position);

            // Descargar la imagen en segundo plano y mostrarla
            executorService.execute(() -> {
                Bitmap bitmap = getBitmapFromURL(imageUrl);
                if (bitmap != null) {
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                } else {
                    runOnUiThread(() -> imageView.setImageResource(android.R.drawable.stat_notify_error));
                }
            });

            // Configurar listeners para clic y long click
            setupImageViewListeners(imageView, tconst, imageUrl, title);
        }

        @Override
        public int getItemCount() {
            return posterUrls.size();
        }

        /**
         * Configura los eventos de clic y long click para cada imagen.
         */
        private void setupImageViewListeners(ImageView imageView, String tconst, String imageUrl, String title) {
            // Al mantener presionado, se agrega a favoritos
            imageView.setOnLongClickListener(v -> {
                if (dbHelper != null && currentUserId != null) {
                    boolean isFav = dbHelper.isMovieFavorite(currentUserId, tconst);
                    if (isFav) {
                        Toast.makeText(imageView.getContext(),
                                title + " ya está en favoritos",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        dbHelper.addMovieToFavorites(currentUserId, tconst, imageUrl, title);
                        Toast.makeText(imageView.getContext(),
                                "Agregada a favoritos: " + title,
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "SQLiteHelper no inicializado o userId es null.");
                }
                return true;
            });

            // Al hacer clic, se abren los detalles de la película
            imageView.setOnClickListener(v -> {
                Intent intent = new Intent(MovieListActivity.this, MovieDetailsActivity.class);
                intent.putExtra("MOVIE_ID", tconst);
                intent.putExtra("IMAGE_URL", imageUrl);
                intent.putExtra("TITLE", title);
                startActivity(intent);
            });
        }
    }

    /**
     * Descarga una imagen desde una URL y la decodifica en un Bitmap, escalándola para optimizar memoria.
     *
     * @param imageUrl URL de la imagen.
     * @return El Bitmap decodificado o null en caso de error.
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

            int reqWidth = 1024;
            int reqHeight = 1024;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Segundo pase: decodificar imagen completa
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
     * Calcula un valor adecuado de inSampleSize para escalar la imagen.
     *
     * @param options  Opciones con las dimensiones originales de la imagen.
     * @param reqWidth Ancho deseado.
     * @param reqHeight Alto deseado.
     * @return El valor de inSampleSize calculado.
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