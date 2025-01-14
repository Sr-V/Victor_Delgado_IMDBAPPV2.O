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

import database.DatabaseManager;
import database.SQLiteHelper;
import edu.pmdm.delgado_victorimdbapp.MovieDetailsActivity;
import edu.pmdm.delgado_victorimdbapp.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import api.IMDBApiService;

/**
 * Fragmento para mostrar las películas más populares desde IMDb.
 * Permite agregar películas a favoritos y acceder a detalles.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment"; // Etiqueta para logs de depuración
    private GridLayout gridLayout; // Contenedor para las imágenes de películas
    private IMDBApiService imdbApiService; // Servicio de API de IMDb
    private SQLiteHelper dbHelper; // Helper para la base de datos

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflar el layout del fragmento
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        gridLayout = root.findViewById(R.id.gridLayout);
        imdbApiService = new IMDBApiService(); // Inicializa el servicio IMDb

        // Inicializa la base de datos con el ID de usuario de Google
        initializeDatabaseHelper();

        // Carga las imágenes de las películas populares
        loadTopMeterImages();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dbHelper = null; // Limpia la referencia a la base de datos
    }

    /**
     * Inicializa el helper de la base de datos.
     * Cierra cualquier instancia previa antes de abrir una nueva.
     */
    private void initializeDatabaseHelper() {
        try {
            DatabaseManager.closeDatabase(); // Cierra la base de datos previa
            dbHelper = DatabaseManager.getInstance(requireContext()); // Abre una nueva base de datos
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error al inicializar SQLiteHelper: " + e.getMessage());
        }
    }

    /**
     * Carga las imágenes de las películas populares desde la API de IMDb.
     */
    private void loadTopMeterImages() {
        new Thread(() -> {
            try {
                // Obtiene los títulos populares desde la API
                String response = imdbApiService.getTopMeterTitles();
                List<String> tconstList = parseTconsts(response);

                for (String tconst : tconstList) {
                    // Obtiene los detalles de cada película
                    String detailsResponse = imdbApiService.getTitleDetails(tconst);
                    String imageUrl = parseImageUrl(detailsResponse); // Extrae la URL de la imagen
                    String title = parseTitle(detailsResponse); // Extrae el título de la película

                    if (imageUrl != null && title != null) {
                        // Agrega las imágenes al GridLayout en el hilo principal
                        requireActivity().runOnUiThread(() -> addImageToGrid(imageUrl, tconst, title));
                    }
                }
            } catch (Exception e) {
                Log.e("IMDB_ERROR", "Error al cargar imágenes", e);
            }
        }).start();
    }

    /**
     * Extrae los IDs (tconst) de las películas del JSON de respuesta.
     *
     * @param response Respuesta JSON de IMDb.
     * @return Lista de IDs de películas.
     */
    private List<String> parseTconsts(String response) {
        List<String> tconsts = new ArrayList<>();
        String regex = "tt\\d{7,8}"; // Expresión regular para IDs de películas
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(response);
        while (matcher.find() && tconsts.size() < 10) { // Limita a las primeras 10 películas
            tconsts.add(matcher.group());
        }
        return tconsts;
    }

    /**
     * Extrae la URL de la imagen desde el JSON de detalles.
     *
     * @param response Respuesta JSON de IMDb.
     * @return URL de la imagen o null si no se encuentra.
     */
    private String parseImageUrl(String response) {
        String regex = "https://.*?\\.jpg"; // Expresión regular para URLs de imágenes
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(response);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Extrae el título de la película desde el JSON de detalles.
     *
     * @param response Respuesta JSON de IMDb.
     * @return Título de la película o null si no se encuentra.
     */
    private String parseTitle(String response) {
        String regex = "\"titleText\":\\{\"text\":\"(.*?)\""; // Expresión regular para títulos
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(response);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Agrega una imagen al GridLayout.
     *
     * @param imageUrl URL de la imagen.
     * @param tconst ID de la película.
     * @param title Título de la película.
     */
    private void addImageToGrid(String imageUrl, String tconst, String title) {
        ImageView imageView = new ImageView(getContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 500; // Ancho de la imagen
        params.height = 750; // Altura de la imagen
        params.setMargins(16, 16, 16, 16); // Márgenes
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
            if (dbHelper != null) {
                if (dbHelper.isMovieFavorite(title)) {
                    Toast.makeText(getContext(), title + " ya está en favoritos", Toast.LENGTH_SHORT).show();
                } else {
                    dbHelper.addMovieToFavorites(tconst, imageUrl, title);
                    Toast.makeText(getContext(), "Agregada a favoritos: " + title, Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("HomeFragment", "SQLiteHelper no inicializado.");
            }
            return true;
        });

        // Listener para abrir la actividad de detalles al hacer clic
        imageView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MovieDetailsActivity.class);
            intent.putExtra("MOVIE_ID", tconst); // ID de la película
            intent.putExtra("IMAGE_URL", imageUrl); // URL de la imagen
            intent.putExtra("TITLE", title); // Título de la película
            startActivity(intent);
        });

        gridLayout.addView(imageView); // Agrega la imagen al GridLayout
    }

    /**
     * Descarga una imagen desde una URL y la convierte en un Bitmap.
     *
     * @param imageUrl URL de la imagen.
     * @return Imagen en formato Bitmap.
     */
    private Bitmap getBitmapFromURL(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.e("IMAGE_ERROR", "Error al descargar la imagen", e);
            return null;
        }
    }
}