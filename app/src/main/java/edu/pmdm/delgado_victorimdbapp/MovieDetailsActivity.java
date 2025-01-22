package edu.pmdm.delgado_victorimdbapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import api.IMDBApiService;
import api.TMDBApiService;

/**
 * Clase para mostrar los detalles de una película y permitir el envío de su información por SMS.
 */
public class MovieDetailsActivity extends AppCompatActivity {

    private static final int SOLICITUD_PERMISO_SMS = 1; // Código de solicitud para permisos de SMS
    private IMDBApiService imdbApiService; // Servicio para obtener datos de IMDb
    private TMDBApiService tmdbApiService; // Servicio para obtener datos de TMDB
    private int contadorRechazosPermiso = 0; // Contador para rastrear rechazos de permisos

    private String movieTitle = "Título Desconocido"; // Título de la película
    private double calificacionPelicula = 0.0; // Calificación de la película

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_details);

        // Referencias a las vistas de la interfaz de usuario
        ImageView imageViewMovie = findViewById(R.id.imageViewMovie);
        TextView textViewTitle = findViewById(R.id.textViewTitle);
        TextView textViewDescription = findViewById(R.id.textViewDescription);
        TextView textViewReleaseDate = findViewById(R.id.textViewReleaseDate);
        TextView textViewRating = findViewById(R.id.textViewRating);
        Button buttonSendSMS = findViewById(R.id.buttonSendSMS);

        // Inicializar servicios de API
        imdbApiService = new IMDBApiService();
        tmdbApiService = new TMDBApiService();

        // Obtener datos enviados desde otra actividad
        String movieId = getIntent().getStringExtra("MOVIE_ID");
        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        movieTitle = getIntent().getStringExtra("TITLE"); // Actualizar título

        // Determinar si se utiliza IMDb o TMDB según el formato del ID de la película
        if (movieId != null && movieId.startsWith("tt")) {
            // Consultar datos de IMDb
            fetchIMDBData(movieId, imageUrl, imageViewMovie, textViewTitle, textViewDescription, textViewReleaseDate, textViewRating, buttonSendSMS);
        } else {
            // Consultar datos de TMDB
            fetchTMDBData(movieId, imageViewMovie, textViewTitle, textViewDescription, textViewReleaseDate, textViewRating, buttonSendSMS);
        }
    }

    /**
     * Consume la API de IMDb para obtener detalles de la película.
     */
    @SuppressLint("SetTextI18n")
    private void fetchIMDBData(String movieId, String imageUrl, ImageView imageViewMovie, TextView textViewTitle,
                               TextView textViewDescription, TextView textViewReleaseDate, TextView textViewRating,
                               Button buttonSendSMS) {
        new Thread(() -> {
            try {
                // Realizar la solicitud a la API de IMDb
                String response = imdbApiService.getTitleDetails(movieId);
                JSONObject jsonObject = new JSONObject(response);
                JSONObject data = jsonObject.getJSONObject("data").getJSONObject("title");

                // Extraer datos relevantes
                String descripcion = data.getJSONObject("plot").getJSONObject("plotText").getString("plainText");
                String fechaEstreno = data.getJSONObject("releaseDate").getInt("year") + "-" +
                        data.getJSONObject("releaseDate").getInt("month") + "-" +
                        data.getJSONObject("releaseDate").getInt("day");
                calificacionPelicula = data.getJSONObject("ratingsSummary").getDouble("aggregateRating");

                // Actualizar interfaz de usuario en el hilo principal
                runOnUiThread(() -> {
                    textViewTitle.setText(movieTitle);
                    textViewDescription.setText(descripcion);
                    textViewReleaseDate.setText("Release Date: " + fechaEstreno);
                    textViewRating.setText("Rating: " + calificacionPelicula);
                    setupSendSMSButton(buttonSendSMS);
                });
            } catch (Exception e) {
                Log.e("MovieDetailsActivity", "Error al obtener los detalles de la película", e);
            }
        }).start();

        // Descargar y mostrar la imagen
        new Thread(() -> {
            Bitmap bitmap = getBitmapFromURL(imageUrl);
            if (bitmap != null) {
                runOnUiThread(() -> imageViewMovie.setImageBitmap(bitmap));
            }
        }).start();
    }

    /**
     * Consume la API de TMDB para obtener detalles de la película.
     */
    @SuppressLint("SetTextI18n")
    private void fetchTMDBData(String movieId, ImageView imageViewMovie, TextView textViewTitle, TextView textViewDescription,
                               TextView textViewReleaseDate, TextView textViewRating, Button buttonSendSMS) {
        new Thread(() -> {
            try {
                // Realizar la solicitud a la API de TMDB
                String response = tmdbApiService.getMovieDetailsById(movieId);
                JSONObject jsonObject = new JSONObject(response);

                // Extraer datos relevantes
                movieTitle = jsonObject.getString("title");
                String descripcion = jsonObject.getString("overview");
                String fechaEstreno = jsonObject.getString("release_date");
                calificacionPelicula = jsonObject.getDouble("vote_average");
                String posterPath = "https://image.tmdb.org/t/p/w500" + jsonObject.getString("poster_path");

                // Actualizar interfaz de usuario en el hilo principal
                runOnUiThread(() -> {
                    textViewTitle.setText(movieTitle);
                    textViewDescription.setText(descripcion);
                    textViewReleaseDate.setText("Release Date: " + fechaEstreno);
                    textViewRating.setText("Rating: " + calificacionPelicula);

                    // Descargar y mostrar la imagen
                    new Thread(() -> {
                        Bitmap bitmap = getBitmapFromURL(posterPath);
                        if (bitmap != null) {
                            runOnUiThread(() -> imageViewMovie.setImageBitmap(bitmap));
                        }
                    }).start();

                    setupSendSMSButton(buttonSendSMS);
                });
            } catch (Exception e) {
                Log.e("MovieDetailsActivity", "Error al obtener los detalles de la película desde TMDB", e);
            }
        }).start();
    }

    /**
     * Configura el botón para enviar un SMS con la información de la película.
     */
    private void setupSendSMSButton(Button button) {
        button.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                // Solicitar permisos si no están otorgados
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.SEND_SMS)) {
                    Toast.makeText(this, "Es necesario el permiso para enviar SMS.", Toast.LENGTH_SHORT).show();
                }
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.SEND_SMS}, SOLICITUD_PERMISO_SMS);
            } else {
                sendSMS(movieTitle, calificacionPelicula);
            }
        });
    }

    /**
     * Envía un SMS con información sobre la película.
     */
    private void sendSMS(String titulo, double calificacion) {
        String cuerpoSMS = "Esta película te gustará: " + titulo + "\n" + "Rating: " + calificacion;
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(Uri.parse("smsto:")); // No especifica destinatario
        smsIntent.putExtra("sms_body", cuerpoSMS);
        startActivity(smsIntent);
    }

    /**
     * Descarga una imagen desde una URL y devuelve un Bitmap.
     */
    private Bitmap getBitmapFromURL(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.e("MovieDetailsActivity", "URL de imagen vacía o nula");
            return null;
        }

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            // Configuración para reducir el tamaño de la imagen
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; // Escala la imagen a 1/4 del tamaño original
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Usa menos memoria por pixel

            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception e) {
            Log.e("MovieDetailsActivity", "Error al descargar la imagen: " + imageUrl, e);
            return null;
        }
    }

    /**
     * Muestra un diálogo para llevar al usuario a la configuración si rechaza varias veces el permiso.
     */
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso Denegado")
                .setMessage("Has denegado el permiso varias veces. ¿Te gustaría ir a la configuración para habilitarlo?")
                .setPositiveButton("Ir a Configuración", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SOLICITUD_PERMISO_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendSMS(movieTitle, calificacionPelicula);
            } else {
                contadorRechazosPermiso++;
                if (contadorRechazosPermiso >= 3) {
                    showPermissionDeniedDialog();
                } else {
                    Toast.makeText(this, "Permiso denegado. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}