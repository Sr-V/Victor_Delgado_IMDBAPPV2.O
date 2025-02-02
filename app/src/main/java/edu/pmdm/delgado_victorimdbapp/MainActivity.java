package edu.pmdm.delgado_victorimdbapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
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
import database.FavoritesSync;  // Importar la clase de sincronización

/**
 * MainActivity que maneja el menú principal y la navegación en la aplicación.
 */
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration; // Configuración de la barra de acción
    private GoogleSignInClient mGoogleSignInClient;   // Cliente para Google Sign-In
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Servicio para ejecutar tareas en 2do plano

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
        )
                .setOpenableLayout(drawer)
                .build();

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

        // Mostrar los datos del usuario autenticado
        displayUserData(userNameTextView, userEmailTextView, userImageView);

        // Configuración del botón de cerrar sesión
        Button logoutButton = headerView.findViewById(R.id.logoutButton);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> logout());
        }

        // Si hay usuario autenticado, inicializar la sincronización de favoritos
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();
            // Crear la instancia de FavoritesSync
            // Instancia para sincronizar favoritos entre SQLite y la nube
            FavoritesSync favoritesSync = new FavoritesSync(this, userId);
            // Sincronizar los favoritos al iniciar la aplicación
            favoritesSync.syncAtStartup();
        }
    }

    /**
     * Muestra los datos del usuario autenticado (nombre, correo o mensaje "Conectado con Facebook",
     * foto de perfil o imagen por defecto), mostrando solamente lo correspondiente
     * a la sesión iniciada.
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
        String name = firebaseUser.getDisplayName();
        String email = firebaseUser.getEmail();
        String imageUrl = (firebaseUser.getPhotoUrl() != null) ? firebaseUser.getPhotoUrl().toString() : "";

        // Determinar el proveedor de autenticación
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
                name = "";
                email = (email != null && !email.isEmpty()) ? email : "Correo no disponible";
            } else {
                name = "Usuario sin proveedor reconocido";
            }
        }

        // Mostrar datos en UI
        nameTextView.setText(name);
        emailTextView.setText(email);
        if (!imageUrl.isEmpty()) {
            loadImageFromUrl(imageView, imageUrl);
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
        }

        // Agregar usuario a la base de datos SQLite
        addUserToDatabase(userId, name, email, imageUrl);
    }

    private void addUserToDatabase(String userId, String name, String email, String imageUrl) {
        SQLiteHelper dbHelper = DatabaseManager.getInstance(this);

        // Verificar si el usuario ya existe en la base de datos
        if (!dbHelper.doesUserExist(userId)) {
            User user = new User(userId, name, email, "", "", "", "", imageUrl);
            boolean success = dbHelper.addUser(user);

            if (success) {
                Log.d("MainActivity", "Usuario agregado a la base de datos: " + userId);
            } else {
                Log.e("MainActivity", "Error al agregar el usuario a la base de datos.");
            }
        } else {
            Log.d("MainActivity", "El usuario ya existe en la base de datos.");
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
                    // 1) Abrir el stream de la imagen
                    inputStream = new java.net.URL(url).openStream();

                    // 2) Primer pase: solo leer dimensiones (inJustDecodeBounds = true)
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(inputStream, null, options);

                    // Cerrar y reabrir el stream
                    inputStream.close();
                    inputStream = new java.net.URL(url).openStream();

                    // 3) Calcular inSampleSize deseado para limitar el tamaño
                    int maxWidth = 512;
                    int maxHeight = 512;
                    options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);

                    // Decodificar la imagen completa
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options);

                    // 4) Escalar la imagen a 150x150 si se decodificó correctamente
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

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Cierra la sesión del usuario actual (Firebase, Google y Facebook) y redirige a LoginActivity.
     */
    private void logout() {
        // 1) Cerrar sesión de Firebase (incluye email/contraseña)
        FirebaseAuth.getInstance().signOut();

        // 2) Cerrar sesión de Google (si estaba logueado)
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task ->
                Log.d("MainActivity", "Google Sign-Out completed (si estaba logueado)"));

        // 3) Cerrar sesión de Facebook (si estaba logueado)
        if (AccessToken.getCurrentAccessToken() != null) {
            LoginManager.getInstance().logOut();
            Log.d("MainActivity", "Facebook Sign-Out completed (si estaba logueado)");
        }

        // 4) Redirigir a LoginActivity
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflar el menú; agrega opciones a la barra de acción.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Manejar la navegación hacia arriba
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}