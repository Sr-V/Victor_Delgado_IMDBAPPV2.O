package edu.pmdm.delgado_victorimdbapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;  // Importar para manejar items de menú
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
import database.UsersSync;  // Importar la clase para sincronización de logs
import utils.AppLifecycleManager;

/**
 * MainActivity que maneja el menú principal y la navegación en la aplicación.
 */
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration; // Configuración de la barra de acción
    private GoogleSignInClient mGoogleSignInClient;      // Cliente para Google Sign-In
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Tareas en segundo plano

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
                // 🔹 Una vez completada la sincronización, cargar los datos del usuario en la UI
                runOnUiThread(() -> displayUserData(userNameTextView, userEmailTextView, userImageView));

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
     * Muestra los datos del usuario autenticado (nombre, correo o mensaje "Conectado con Facebook",
     * foto de perfil o imagen por defecto) y agrega o actualiza el usuario en la base de datos local.
     *
     * @param nameTextView  TextView para el nombre del usuario.
     * @param emailTextView TextView para el correo o estado del usuario.
     * @param imageView     ImageView para mostrar la foto de perfil.
     */
    @SuppressLint("SetTextI18n")
    private void displayUserData(TextView nameTextView, TextView emailTextView, ImageView imageView) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            // No hay usuario autenticado
            nameTextView.setText("Usuario no autenticado");
            emailTextView.setText("");
            imageView.setImageResource(R.mipmap.ic_launcher_round);
            return;
        }

        String userId = firebaseUser.getUid();
        SQLiteHelper dbHelper = DatabaseManager.getInstance(this);
        User localUser = dbHelper.getUser(userId);

        String name = null;
        String email = null;
        String imageUrl = null;

        if (localUser != null) {
            // 🔹 Prioriza datos locales si están disponibles
            name = localUser.getName();
            email = localUser.getEmail();
            imageUrl = localUser.getImage();

            Log.d("MainActivity", "Cargando datos locales del usuario: " + userId);
        }

        if (name == null || name.isEmpty() || email == null || email.isEmpty()) {
            // 🔹 Si no hay datos locales, usa los del proveedor de autenticación
            Log.d("MainActivity", "No se encontraron datos locales. Usando datos del proveedor para: " + userId);

            name = firebaseUser.getDisplayName();
            email = firebaseUser.getEmail();
            imageUrl = (firebaseUser.getPhotoUrl() != null) ? firebaseUser.getPhotoUrl().toString() : "";

            // 🔹 Determinar el proveedor de autenticación
            boolean isFacebookSignedIn = AccessToken.getCurrentAccessToken() != null
                    && !AccessToken.getCurrentAccessToken().isExpired();

            if (isFacebookSignedIn) {
                com.facebook.Profile profile = com.facebook.Profile.getCurrentProfile();
                if (profile != null) {
                    name = profile.getName();
                    email = "Conectado con Facebook";
                    imageUrl = profile.getProfilePictureUri(150, 150).toString();
                } else {
                    name = "Usuario de Facebook";
                    email = "Conectado con Facebook";
                }
            } else {
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

                if (isGoogleUser) {
                    name = (name != null && !name.isEmpty()) ? name : "Nombre no disponible";
                    email = (email != null && !email.isEmpty()) ? email : "Correo no disponible";
                } else if (isEmailPasswordUser) {
                    name = (name != null && !name.isEmpty()) ? name : "";
                    email = (email != null && !email.isEmpty()) ? email : "Correo no disponible";
                } else {
                    name = "Usuario sin proveedor reconocido";
                }
            }
        }

        // 🔹 Mostrar datos en la interfaz
        nameTextView.setText(name);
        emailTextView.setText(email);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (imageUrl.startsWith("http")) {
                loadImageFromUrl(imageView, imageUrl);
            } else {
                decodeBase64Image(imageView, imageUrl);
            }
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
        }

        // 🔹 Agregar o actualizar usuario en la base de datos local y sincronizar en la nube
        addOrUpdateUserInDatabase(userId, name, email, imageUrl);
    }

    private void decodeBase64Image(ImageView imageView, String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            imageView.setImageBitmap(bitmap);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Error decodificando imagen Base64", e);
            imageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }

    /**
     * Agrega el usuario a la base de datos local si no existe; si ya existe, actualiza su login_time.
     * Luego sincroniza la información en la nube.
     *
     * @param userId   ID del usuario.
     * @param name     Nombre del usuario.
     * @param email    Correo o estado del usuario.
     * @param imageUrl URL de la imagen de perfil.
     */
    private void addOrUpdateUserInDatabase(String userId, String name, String email, String imageUrl) {
        SQLiteHelper dbHelper = DatabaseManager.getInstance(this);
        String currentTime = getCurrentTime();

        User user = dbHelper.getUser(userId);

        if (user == null) {
            // 🔹 Insertar usuario nuevo con login_time
            user = new User(userId, name, email, currentTime, "", "", "", imageUrl);
            boolean success = dbHelper.addUser(user);
            if (success) {
                Log.d("MainActivity", "Usuario agregado a la base de datos: " + userId);
            } else {
                Log.e("MainActivity", "Error al agregar el usuario a la base de datos.");
            }
        } else if (user.getLoginTime() == null || user.getLoginTime().isEmpty()) {
            // 🔹 Solo actualizar login_time si está vacío
            user.setLoginTime(currentTime);
            dbHelper.addUser(user);
            Log.d("MainActivity", "Usuario existente actualizado con login_time: " + userId);
        } else {
            Log.d("MainActivity", "El login_time ya estaba registrado. No se actualiza.");
        }

        // 🔹 Sincronizar solo si login_time se modificó
        if (user.getLoginTime().equals(currentTime)) {
            new UsersSync(this, userId).syncActivityLog();
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