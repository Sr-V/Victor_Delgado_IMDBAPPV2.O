package edu.pmdm.delgado_victorimdbapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

    private static final int SOLICITUD_PERMISOS_SMS_CONTACTOS = 1; // Código de solicitud de permisos
    private IMDBApiService imdbApiService; // Servicio para obtener datos de IMDb
    private TMDBApiService tmdbApiService; // Servicio para obtener datos de TMDB
    private int contadorRechazosPermiso = 0; // Contador para rastrear rechazos de permisos

    private String movieTitle = "Título Desconocido"; // Título de la película
    private double calificacionPelicula = 0.0; // Calificación de la película

    /**
     * Método que se ejecuta al crear la actividad.
     */
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
        movieTitle = getIntent().getStringExtra("TITLE");

        // Determinar si se utiliza IMDb o TMDB según el formato del ID de la película
        if (movieId != null && movieId.startsWith("tt")) {
            fetchIMDBData(movieId, imageUrl, imageViewMovie, textViewTitle, textViewDescription, textViewReleaseDate, textViewRating, buttonSendSMS);
        } else {
            fetchTMDBData(movieId, imageViewMovie, textViewTitle, textViewDescription, textViewReleaseDate, textViewRating, buttonSendSMS);
        }
    }

    /**
     * Configura el botón para enviar un SMS con la información de la película.
     */
    private void setupSendSMSButton(Button button) {
        button.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS},
                        SOLICITUD_PERMISOS_SMS_CONTACTOS);
            } else {
                seleccionarContacto();
            }
        });
    }

    /**
     * Lanzador para seleccionar un contacto de la agenda.
     */
    private final ActivityResultLauncher<Intent> seleccionarContactoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactoUri = result.getData().getData();
                    if (contactoUri != null) {
                        obtenerNumeroTelefono(contactoUri);
                    }
                }
            });

    /**
     * Método para abrir la lista de contactos y seleccionar uno.
     */
    private void seleccionarContacto() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        seleccionarContactoLauncher.launch(intent);
    }

    /**
     * Método para obtener el número de teléfono del contacto seleccionado.
     */
    private void obtenerNumeroTelefono(Uri contactoUri) {
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor cursor = getContentResolver().query(contactoUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (columnIndex != -1) {
                    String numeroTelefono = cursor.getString(columnIndex);
                    enviarSMS(numeroTelefono);
                }
            }
        } catch (Exception e) {
            Log.e("MovieDetailsActivity", "Error al obtener el número de teléfono", e);
        }
    }

    /**
     * Método para enviar un SMS con la información de la película.
     */
    private void enviarSMS(String numeroTelefono) {
        String cuerpoSMS = "Esta película te gustará: " + movieTitle + "\n" + "Rating: " + calificacionPelicula;
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(Uri.parse("smsto:" + numeroTelefono));
        smsIntent.putExtra("sms_body", cuerpoSMS);
        startActivity(smsIntent);
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
            Log.e("MovieDetailsActivity", "URL de imagen vacía o nula");
            return null;
        }

        InputStream inputStream = null;
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            // ========== PRIMER PASE: LECTURA DE DIMENSIONES ==========
            // (inJustDecodeBounds=true para solo obtener width/height)
            inputStream = connection.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            // Cerrar el InputStream para reabrirlo en el segundo pase
            inputStream.close();
            connection.disconnect();

            // ========== CALCULAR inSampleSize ==========
            // Por ejemplo, queremos que la imagen a decodificar no exceda 1024x1024
            // antes de un escalado final.
            int reqWidth = 1024;
            int reqHeight = 1024;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // ========== SEGUNDO PASE: DECODIFICAR ==========
            // Volvemos a abrir la conexión/stream
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            inputStream = connection.getInputStream();

            // Decodificar con inSampleSize calculado
            options.inJustDecodeBounds = false;
            // Usamos ARGB_8888 para conservar calidad (24 bits + alpha)
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap scaledBitmap = BitmapFactory.decodeStream(inputStream, null, options);

            // Cerrar
            inputStream.close();
            connection.disconnect();

            return scaledBitmap;
        } catch (Exception e) {
            Log.e("MovieDetailsActivity", "Error al descargar/decodificar la imagen: " + imageUrl, e);
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {}
            }
            return null;
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
     * Calcula un inSampleSize adecuado para decodificar la imagen a un tamaño manejable,
     * basándose en las dimensiones deseadas (reqWidth, reqHeight) y las dimensiones
     * originales (options.outWidth, options.outHeight).
     *
     * @param options   Opciones del BitmapFactory con outWidth y outHeight ya cargados.
     * @param reqWidth  Ancho máximo deseado.
     * @param reqHeight Alto máximo deseado.
     * @return Un valor de inSampleSize (1,2,4,...) para decodificar la imagen.
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Dimensiones originales de la imagen
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        // Si la imagen es más grande que el tamaño requerido, calculamos la reducción necesaria
        if (height > reqHeight || width > reqWidth) {
            // Mitades
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Aumentar inSampleSize mientras la mitad de la altura y anchura sigan
            // siendo mayores que reqWidth/reqHeight
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
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

        if (requestCode == SOLICITUD_PERMISOS_SMS_CONTACTOS) {
            boolean smsGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean contactsGranted = grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED;

            if (smsGranted && contactsGranted) {
                seleccionarContacto();
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
