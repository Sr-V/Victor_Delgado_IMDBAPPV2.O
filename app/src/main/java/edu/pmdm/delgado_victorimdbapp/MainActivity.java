package edu.pmdm.delgado_victorimdbapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;

import database.DatabaseManager;
import database.SQLiteHelper;
import database.User;
import edu.pmdm.delgado_victorimdbapp.databinding.ActivityMainBinding;
import database.FavoritesSync;
import database.UsersSync;
import utils.AppLifecycleManager;

/**
 * MainActivity que maneja el menú principal y la navegación en la aplicación.
 */
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration; // Configuración de la barra de acción
    private GoogleSignInClient mGoogleSignInClient;      // Cliente para Google Sign-In
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Tareas en segundo plano

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enlace con el layout de la actividad principal
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar); // Configurar la barra de herramientas

        DrawerLayout drawer = binding.drawerLayout;    // Configuración del menú lateral
        NavigationView navigationView = binding.navView; // Configuración de la barra de navegación

        // Configurar las opciones de navegación
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_top10,
                R.id.nav_gallery,
                R.id.nav_slideshow
        ).setOpenableLayout(drawer).build();

        // Controlador para manejar la navegación entre fragmentos
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Configuración de Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail() // Solicitar acceso al correo electrónico del usuario
                .build();
        mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso);

        // Configuración del encabezado del menú lateral
        View headerView = navigationView.getHeaderView(0);
        TextView userNameTextView = headerView.findViewById(R.id.userName);
        TextView userEmailTextView = headerView.findViewById(R.id.userEmail);
        ImageView userImageView = headerView.findViewById(R.id.imageView);

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();
            UsersSync usersSync = new UsersSync(this, userId);

            // 🔹 Sincronizar los datos del usuario desde Firestore a SQLite antes de mostrar la UI
            usersSync.syncFromCloudToLocal(() -> {
                // Verificar si ya existe el usuario en la base de datos local
                SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
                User localUser = dbHelper.getUser(userId);

                if (localUser == null) {
                    // Si no hay datos, crear usuario localmente con los datos obtenidos del proveedor
                    createUserIfNotExists(firebaseUser, userId, userNameTextView, userEmailTextView, userImageView);
                } else {
                    // Si ya existe, cargar los datos de la base de datos local
                    runOnUiThread(() -> {
                        displayUserData(localUser, userNameTextView, userEmailTextView, userImageView);
                    });
                }

                // 🔹 Inicializar la sincronización de favoritos
                FavoritesSync favoritesSync = new FavoritesSync(this, userId);
                favoritesSync.syncAtStartup();
            });
        }

        // Configuración del botón de cerrar sesión
        Button logoutButton = headerView.findViewById(R.id.logoutButton);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> logout());
        }
    }

    /**
     * Crea el usuario localmente si no existe y sincroniza con la nube.
     * Este método se ejecuta cuando no se encuentran datos locales ni en la nube.
     *
     * @param firebaseUser El usuario de Firebase para obtener sus datos.
     * @param userId El ID único del usuario.
     * @param userNameTextView TextView donde se mostrará el nombre.
     * @param userEmailTextView TextView donde se mostrará el correo.
     * @param userImageView ImageView donde se mostrará la imagen de perfil.
     */
    private void createUserIfNotExists(FirebaseUser firebaseUser, String userId,
                                       TextView userNameTextView, TextView userEmailTextView, ImageView userImageView) {
        String name = "Anónimo"; // Valor predeterminado para el nombre
        String email = firebaseUser.getEmail(); // El correo es siempre accesible
        String imageUrl = null; // No asignamos imagen por defecto

        // Obtener el proveedor de autenticación
        String provider = firebaseUser.getProviderId();

        switch (provider) {
            case "google.com":
                name = firebaseUser.getDisplayName(); // Nombre obtenido de Google
                imageUrl = (firebaseUser.getPhotoUrl() != null)
                        ? firebaseUser.getPhotoUrl().toString()
                        : null; // Imagen de Google
                break;
            case "facebook.com":
                name = firebaseUser.getDisplayName(); // Nombre obtenido de Facebook
                imageUrl = (firebaseUser.getPhotoUrl() != null)
                        ? firebaseUser.getPhotoUrl().toString()
                        : null; // Imagen de Facebook
                break;
            case "password":
                name = (firebaseUser.getDisplayName() != null)
                        ? firebaseUser.getDisplayName()
                        : "Anónimo"; // Si tiene nombre, usarlo, si no, usar "Anónimo"
                break;
        }

        // Si el correo está vacío, asignar un valor predeterminado (esto nunca debería suceder con email/password)
        if (email == null || email.isEmpty()) {
            email = "Correo no disponible";
        }

        // Crear el usuario localmente
        User newUser = new User(userId, name, email, getCurrentTime(), null, null, null, imageUrl);

        // Insertar el usuario en la base de datos local
        SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
        boolean success = dbHelper.addUser(newUser);  // Añadir usuario en base local

        // Verificar si la inserción fue exitosa y sincronizar con la nube solo si los datos obligatorios están completos
        if (success) {
            if (newUser.getUserId() != null && !newUser.getUserId().isEmpty()
                    && newUser.getName() != null && !newUser.getName().isEmpty()
                    && newUser.getEmail() != null && !newUser.getEmail().isEmpty()) {

                // Sincronizar los datos del usuario con Firestore después de la inserción exitosa
                new UsersSync(this, userId)
                        .syncSpecificFields() // Sincroniza los campos específicos con Firestore
                        .addOnSuccessListener(aVoid -> Log.d("MainActivity", "Sincronización con la nube exitosa."))
                        .addOnFailureListener(e -> Log.e("MainActivity", "Error al sincronizar con la nube.", e));

                // Actualizar la UI en el hilo principal
                runOnUiThread(() -> {
                    displayUserData(newUser, userNameTextView, userEmailTextView, userImageView);
                });
            } else {
                // Si faltan datos, registrar un error y NO sincronizar con la nube
                Log.e("MainActivity", "Datos insuficientes para sincronizar. Se requiere user_id, name y email completos.");
                runOnUiThread(() -> {
                    // Incluso si no se sincroniza, se actualiza la UI con lo que se tiene localmente.
                    displayUserData(newUser, userNameTextView, userEmailTextView, userImageView);
                });
            }
        } else {
            Log.e("MainActivity", "Error al agregar el usuario a la base de datos.");
        }
    }

    /**
     * Muestra los datos del usuario en la UI.
     * Se utiliza después de crear o actualizar los datos del usuario en la base de datos local.
     *
     * @param user El usuario con los datos a mostrar.
     * @param nameTextView El TextView para mostrar el nombre.
     * @param emailTextView El TextView para mostrar el correo electrónico.
     * @param imageView El ImageView para mostrar la imagen de perfil.
     */
    private void displayUserData(User user, TextView nameTextView, TextView emailTextView, ImageView imageView) {
        // Primero, mostrar los datos existentes en la base de datos local
        nameTextView.setText(user.getName());
        emailTextView.setText(user.getEmail());

        // Si el usuario tiene una imagen, mostrarla
        if (user.getImage() != null && !user.getImage().isEmpty()) {
            if (user.getImage().startsWith("http")) {
                loadImageFromUrl(imageView, user.getImage());
            } else {
                decodeBase64Image(imageView, user.getImage()); // Usar Base64 si es necesario
            }
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
        }

        // Verificar el proveedor para actualizar los campos si es necesario
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            boolean isFacebookSignedIn = AccessToken.getCurrentAccessToken() != null && !AccessToken.getCurrentAccessToken().isExpired();
            boolean isGoogleUser = false;
            boolean isEmailPasswordUser = false;

            for (UserInfo userInfo : firebaseUser.getProviderData()) {
                String providerId = userInfo.getProviderId();
                if (GoogleAuthProvider.PROVIDER_ID.equals(providerId)) {
                    isGoogleUser = true;
                }
                if (EmailAuthProvider.PROVIDER_ID.equals(providerId)) {
                    isEmailPasswordUser = true;
                }
            }

            // Si es un usuario de Google, actualizar el nombre y correo si están vacíos
            if (isGoogleUser && (user.getName().isEmpty() || user.getEmail().isEmpty())) {
                String name = firebaseUser.getDisplayName();
                String email = firebaseUser.getEmail();
                String imageUrl = (firebaseUser.getPhotoUrl() != null) ? firebaseUser.getPhotoUrl().toString() : "";

                user.setName((name != null && !name.isEmpty()) ? name : "Nombre no disponible");
                user.setEmail((email != null && !email.isEmpty()) ? email : "Correo no disponible");
                user.setImage(imageUrl);

                // Actualizar los datos en la base de datos local
                SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
                dbHelper.addUser(user);

                // Refrescar la UI con los nuevos datos
                nameTextView.setText(user.getName());
                emailTextView.setText(user.getEmail());
                if (user.getImage() != null && !user.getImage().isEmpty()) {
                    loadImageFromUrl(imageView, user.getImage());
                } else {
                    imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
                }
            }
            // Si es un usuario de Facebook, actualizar nombre y correo
            else if (isFacebookSignedIn && (user.getName().isEmpty() || user.getEmail().isEmpty())) {
                String name = firebaseUser.getDisplayName();
                user.setName(name != null ? name : "Usuario de Facebook");
                user.setEmail("Conectado con Facebook");

                // Actualizar los datos en la base de datos local
                SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
                dbHelper.addUser(user);

                // Refrescar la UI con los nuevos datos
                nameTextView.setText(user.getName());
                emailTextView.setText(user.getEmail());
                if (user.getImage() != null && !user.getImage().isEmpty()) {
                    loadImageFromUrl(imageView, user.getImage());
                } else {
                    imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
                }
            }
            // Si es un usuario con correo y contraseña, actualizar los campos si es necesario
            else if (isEmailPasswordUser && (user.getName().isEmpty() || user.getEmail().isEmpty())) {
                String name = firebaseUser.getDisplayName();
                String email = firebaseUser.getEmail();

                user.setName((name != null && !name.isEmpty()) ? name : "Nombre no disponible");
                user.setEmail((email != null && !email.isEmpty()) ? email : "Correo no disponible");

                // Actualizar los datos en la base de datos local
                SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
                dbHelper.addUser(user);

                // Refrescar la UI con los nuevos datos
                nameTextView.setText(user.getName());
                emailTextView.setText(user.getEmail());
                if (user.getImage() != null && !user.getImage().isEmpty()) {
                    loadImageFromUrl(imageView, user.getImage());
                } else {
                    imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
                }
            }
        }
    }

    /**
     * Decodifica una imagen en Base64 y la muestra en un ImageView.
     */
    private void decodeBase64Image(ImageView imageView, String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            imageView.setImageBitmap(bitmap);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Error decodificando imagen Base64", e);
            imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
        }
    }

    /**
     * Descarga y muestra una imagen desde una URL en un ImageView, usando un ExecutorService
     * para no bloquear la UI.
     *
     * @param imageView ImageView donde se mostrará la imagen.
     * @param url       URL de la imagen.
     */
    private void loadImageFromUrl(ImageView imageView, String url) {
        if (url != null && !url.isEmpty()) {
            executorService.execute(() -> {
                Bitmap bitmap = null;
                InputStream inputStream = null;
                try {
                    // Abrir el stream de la imagen
                    inputStream = new java.net.URL(url).openStream();

                    // Primer pase: solo leer dimensiones (inJustDecodeBounds = true)
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(inputStream, null, options);

                    // Cerrar y reabrir el stream
                    inputStream.close();
                    inputStream = new java.net.URL(url).openStream();

                    // Calcular inSampleSize para limitar el tamaño
                    int maxWidth = 512;
                    int maxHeight = 512;
                    options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);

                    // Decodificar la imagen completa
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options);

                    // Escalar la imagen a 150x150 si se decodificó correctamente
                    if (bitmap != null) {
                        int targetWidth = 150;
                        int targetHeight = 150;
                        bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error loading image from URL", e);
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (Exception ignored) {}
                    }
                }
                Bitmap finalBitmap = bitmap;
                runOnUiThread(() -> {
                    if (finalBitmap != null) {
                        imageView.setImageBitmap(finalBitmap);
                    } else {
                        imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
                    }
                });
            });
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
        }
    }

    /**
     * Calcula un inSampleSize adecuado para decodificar la imagen a un tamaño manejable.
     *
     * @param options   Opciones del BitmapFactory con outWidth y outHeight ya cargados.
     * @param reqWidth  Ancho máximo deseado.
     * @param reqHeight Alto máximo deseado.
     * @return Valor de inSampleSize.
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

    /**
     * Cierra la sesión del usuario actual (Firebase, Google y Facebook), actualiza el logout_time
     * local y sincroniza la información en la nube antes de redirigir a LoginActivity.
     */
    private void logout() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();
            String currentTime = getCurrentTime();
            SQLiteHelper dbHelper = DatabaseManager.getInstance(this);
            User user = dbHelper.getUser(userId);

            if (user != null) {
                user.setLogoutTime(currentTime);
                dbHelper.addUser(user);
                Log.d("MainActivity", "Usuario logout actualizado en la base local: " + userId);

                // 🔹 Sincronizar en la nube antes de proceder con el logout
                new UsersSync(this, userId)
                        .syncActivityLog()
                        .addOnSuccessListener(aVoid -> {
                            resetLoginStateAndSignOut();
                        })
                        .addOnFailureListener(e -> {
                            Log.e("MainActivity", "Error al sincronizar el activity_log", e);
                            // 🔹 Incluso si falla la sincronización, se procede al logout
                            resetLoginStateAndSignOut();
                        });
                return;
            }
        }
        resetLoginStateAndSignOut();
    }

    /**
     * Restablece el estado de login y cierra sesión en Firebase, Google y Facebook.
     */
    private void resetLoginStateAndSignOut() {
        // 🔹 Restablecer estado para permitir otro login
        AppLifecycleManager appLifecycleManager = (AppLifecycleManager) getApplication();
        appLifecycleManager.resetLoginState();

        // 🔹 Cerrar sesión en Firebase
        FirebaseAuth.getInstance().signOut();

        // 🔹 Cerrar sesión en Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task ->
                Log.d("MainActivity", "Google Sign-Out completed (si estaba logueado)"));

        // 🔹 Cerrar sesión en Facebook si está activo
        if (AccessToken.getCurrentAccessToken() != null) {
            LoginManager.getInstance().logOut();
            Log.d("MainActivity", "Facebook Sign-Out completed (si estaba logueado)");
        }

        // 🔹 Redirigir a la pantalla de Login
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Obtiene la fecha y hora actual en formato "yyyy-MM-dd HH:mm:ss".
     *
     * @return La fecha y hora formateada.
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflar el menú; agrega opciones a la barra de acción.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Maneja la selección de opciones del menú.
     * Cuando se pulsa el item 'action_edit_user', se abre la actividad EditUserActivity.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_edit_user) {
            // Abrir EditUserActivity
            Intent intent = new Intent(MainActivity.this, EditUserActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Manejar la navegación hacia arriba
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}