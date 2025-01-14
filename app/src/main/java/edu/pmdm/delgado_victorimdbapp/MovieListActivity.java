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
    private SQLiteHelper dbHelper; // Instancia de SQLiteHelper para manejar la base de datos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar el RecyclerView
        RecyclerView recyclerView = createRecyclerView();
        setContentView(recyclerView);

        // Inicializar SQLiteHelper
        initializeDatabaseHelper();

        // Obtener datos de películas desde el Intent
        ArrayList<String> posterUrls = getIntent().getStringArrayListExtra("POSTER_URLS");
        ArrayList<String> titles = getIntent().getStringArrayListExtra("TITLES");
        ArrayList<String> tconsts = getIntent().getStringArrayListExtra("TCONSTS");

        // Verificar que los datos no estén vacíos
        if (posterUrls == null || posterUrls.isEmpty() || titles == null || tconsts == null) {
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
     * Inicializa la base de datos SQLiteHelper.
     */
    private void initializeDatabaseHelper() {
        try {
            DatabaseManager.closeDatabase(); // Cerrar cualquier instancia previa
            dbHelper = DatabaseManager.getInstance(this); // Inicializar base de datos
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error al inicializar SQLiteHelper: " + e.getMessage());
        }
    }

    /**
     * Crea un RecyclerView configurado con un GridLayoutManager.
     *
     * @return RecyclerView configurado.
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
        private final ArrayList<String> titles; // Títulos de las películas
        private final ArrayList<String> tconsts; // IDs de las películas

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

            // Descargar y mostrar la imagen
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
                if (dbHelper != null) {
                    if (dbHelper.isMovieFavorite(title)) {
                        Toast.makeText(imageView.getContext(), title + " ya está en favoritos", Toast.LENGTH_SHORT).show();
                    } else {
                        dbHelper.addMovieToFavorites(tconst, imageUrl, title);
                        Toast.makeText(imageView.getContext(), "Agregada a favoritos: " + title, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "SQLiteHelper no inicializado.");
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
     * Descarga una imagen desde una URL y la devuelve como un Bitmap.
     *
     * @param imageUrl URL de la imagen.
     * @return Bitmap de la imagen o null en caso de error.
     */
    private Bitmap getBitmapFromURL(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.e(TAG, "URL de imagen vacía o nula");
            return null;
        }

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al descargar la imagen: " + imageUrl, e);
            return null;
        }
    }
}