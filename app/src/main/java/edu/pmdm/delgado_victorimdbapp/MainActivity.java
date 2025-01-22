package edu.pmdm.delgado_victorimdbapp;

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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
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

import edu.pmdm.delgado_victorimdbapp.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * MainActivity que maneja el menú principal y la navegación en la aplicación.
 */
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration; // Configuración de la barra de acción
    private GoogleSignInClient mGoogleSignInClient; // Cliente para Google Sign-In
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // Servicio para ejecutar tareas en segundo plano

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enlace con el layout de la actividad principal
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar); // Configurar la barra de herramientas

        DrawerLayout drawer = binding.drawerLayout; // Configuración del menú lateral
        NavigationView navigationView = binding.navView; // Configuración de la barra de navegación

        // Configurar las opciones de navegación
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_top10, R.id.nav_gallery, R.id.nav_slideshow) // Fragmentos del menú
                .setOpenableLayout(drawer) // Habilitar el DrawerLayout
                .build();

        // Controlador para manejar la navegación entre fragmentos
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Configuración de Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail() // Solicitar acceso al correo electrónico del usuario
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

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
    }

    /**
     * Muestra los datos del usuario autenticado (nombre, correo e imagen).
     *
     * @param nameTextView TextView para el nombre del usuario.
     * @param emailTextView TextView para el correo del usuario.
     * @param imageView ImageView para mostrar la foto de perfil del usuario.
     */
    private void displayUserData(TextView nameTextView, TextView emailTextView, ImageView imageView) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser(); // Obtener usuario de Firebase
        if (firebaseUser != null) {
            nameTextView.setText(firebaseUser.getDisplayName()); // Mostrar nombre
            emailTextView.setText(firebaseUser.getEmail()); // Mostrar correo
            loadImageFromUrl(imageView, firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null); // Cargar imagen
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this); // Obtener usuario de Google
            if (account != null) {
                nameTextView.setText(account.getDisplayName());
                emailTextView.setText(account.getEmail());
                loadImageFromUrl(imageView, account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);
            }
        }
    }

    /**
     * Descarga y muestra una imagen desde una URL en un ImageView.
     *
     * @param imageView ImageView donde se mostrará la imagen.
     * @param url URL de la imagen.
     */
    private void loadImageFromUrl(ImageView imageView, String url) {
        if (url != null && !url.isEmpty()) {
            executorService.execute(() -> {
                Bitmap bitmap = null;
                try {
                    // Descargar la imagen
                    InputStream inputStream = new java.net.URL(url).openStream();

                    // Escalar la imagen durante la decodificación
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4; // Escala la imagen a 1/4 del tamaño original
                    options.inPreferredConfig = Bitmap.Config.RGB_565;

                    bitmap = BitmapFactory.decodeStream(inputStream, null, options);

                    // Redimensionar la imagen a un tamaño fijo
                    if (bitmap != null) {
                        int targetWidth = 150; // Ancho deseado
                        int targetHeight = 150; // Altura deseada
                        bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error loading image from URL", e);
                }

                Bitmap finalBitmap = bitmap;
                runOnUiThread(() -> {
                    if (finalBitmap != null) {
                        imageView.setImageBitmap(finalBitmap);
                    } else {
                        imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen predeterminada
                    }
                });
            });
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen predeterminada
        }
    }

    /**
     * Cierra la sesión del usuario actual y redirige a LoginActivity.
     */
    private void logout() {
        FirebaseAuth.getInstance().signOut(); // Cerrar sesión de Firebase

        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Eliminar MainActivity de la pila
            startActivity(intent);
            finish();
        });
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
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }
}