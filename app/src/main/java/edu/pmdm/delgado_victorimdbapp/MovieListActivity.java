package edu.pmdm.delgado_victorimdbapp;

import static androidx.core.content.ContentProviderCompat.requireContext;

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

import com.facebook.AccessToken;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.DatabaseManager;
import database.SQLiteHelper;

/**
 * Actividad para mostrar una lista de películas en un RecyclerView con soporte para
 * agregar a favoritos y ver detalles de cada película.
 */
public class MovieListActivity extends AppCompatActivity {

    private static final String TAG = "MovieListActivity";

    private final ExecutorService executorService = Executors.newFixedThreadPool(4); // Ejecutores para tareas de fondo
    private SQLiteHelper dbHelper;  // Instancia de SQLiteHelper para manejar la base de datos

    // *** Añadimos una variable para el userId ***
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar el RecyclerView
        RecyclerView recyclerView = createRecyclerView();
        setContentView(recyclerView);

        // Inicializar SQLiteHelper (una sola base de datos)
        initializeDatabaseHelper();

        // Obtener datos de películas desde el Intent
        ArrayList<String> posterUrls = getIntent().getStringArrayListExtra("POSTER_URLS");
        ArrayList<String> titles = getIntent().getStringArrayListExtra("TITLES");
        ArrayList<String> tconsts = getIntent().getStringArrayListExtra("TCONSTS");

        // Verificar que los datos no estén vacíos
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
        // Limpiar referencias y cerrar ejecutores
        dbHelper = null;
        executorService.shutdown();
    }

    /**
     * Inicializa el helper de la base de datos y registra el usuario actual.
     */
    private void initializeDatabaseHelper() {
        // Inicializa la base de datos sin registrar al usuario
        dbHelper = SQLiteHelper.getInstance(this);

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
     * Crea un RecyclerView configurado con un GridLayoutManager.
     */
    private RecyclerView createRecyclerView() {
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
        ));
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2)); // Grid de 2 columnas
        return recyclerView;
    }

    /**
     * Adaptador para manejar la lista de películas en el RecyclerView.
     */
    private class MovieAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final ArrayList<String> posterUrls; // URLs de los pósters
        private final ArrayList<String> titles;     // Títulos de las películas
        private final ArrayList<String> tconsts;    // IDs de las películas (e.g. "tt1234567")

        public MovieAdapter(ArrayList<String> posterUrls,
                            ArrayList<String> titles,
                            ArrayList<String> tconsts) {
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
                    ViewGroup.LayoutParams.MATCH_PARENT, // Ancho completo
                    750 // Altura fija
            );
            params.setMargins(16, 16, 16, 16); // Márgenes alrededor del póster
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP); // Ajustar la imagen al centro

            return new RecyclerView.ViewHolder(imageView) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ImageView imageView = (ImageView) holder.itemView;
            String imageUrl = posterUrls.get(position);
            String title = titles.get(position);
            String tconst = tconsts.get(position);

            // Descargar y mostrar la imagen en segundo plano
            executorService.execute(() -> {
                Bitmap bitmap = getBitmapFromURL(imageUrl);
                if (bitmap != null) {
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                } else {
                    runOnUiThread(() -> imageView.setImageResource(android.R.drawable.stat_notify_error));
                }
            });

            // Configurar eventos de clic y mantener presionado
            setupImageViewListeners(imageView, tconst, imageUrl, title);
        }

        @Override
        public int getItemCount() {
            return posterUrls.size(); // Número de elementos en la lista
        }

        /**
         * Configura los eventos para cada póster en la lista.
         */
        private void setupImageViewListeners(ImageView imageView, String tconst, String imageUrl, String title) {
            // Agregar a favoritos al mantener presionado
            imageView.setOnLongClickListener(v -> {
                if (dbHelper != null && currentUserId != null) {
                    // *** Usamos el método que requiere userId y movieId ***
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

            // Abrir detalles de la película al hacer clic
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
     * Descarga una imagen desde una URL y devuelve un Bitmap, escalando dinámicamente
     * su tamaño para no usar demasiada memoria, pero manteniendo mejor calidad.
     *
     * @param imageUrl URL de la imagen a descargar.
     * @return El Bitmap decodificado o null si ocurrió un error.
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

            // ========== PRIMER PASE: LECTURA DE DIMENSIONES ==========
            inputStream = connection.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            // Cerrar el InputStream y desconectar
            inputStream.close();
            connection.disconnect();

            // ========== CALCULAR inSampleSize ==========
            int reqWidth = 1024;
            int reqHeight = 1024;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // ========== SEGUNDO PASE: DECODIFICAR ==========
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
     * Calcula un inSampleSize adecuado para decodificar la imagen a un tamaño manejable,
     * basándose en las dimensiones deseadas (reqWidth, reqHeight) y las dimensiones
     * originales (options.outWidth, options.outHeight).
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