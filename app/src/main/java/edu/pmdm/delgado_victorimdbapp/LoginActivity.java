package edu.pmdm.delgado_victorimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.common.SignInButton;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Actividad de inicio de sesión que permite a los usuarios autenticarse con Google y Firebase.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity"; // Etiqueta para los logs de depuración
    private GoogleSignInClient mGoogleSignInClient; // Cliente de Google Sign-In
    private FirebaseAuth mAuth; // Instancia de Firebase Authentication

    // ActivityResultLauncher para manejar el resultado de Google Sign-In
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        // Obtener la cuenta de Google seleccionada
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        // Autenticar con Firebase usando el token de la cuenta de Google
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(this, R.string.google_sign_in_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Ajustar los márgenes para incluir las barras del sistema (como la barra de estado)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializa Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Verifica si el usuario ya está autenticado
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Si ya está autenticado, redirige a MainActivity
            navigateToMainActivity();
            return; // Termina la ejecución de onCreate
        }

        // Configuración de Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // Solicitar ID Token para Firebase
                .requestEmail() // Solicitar acceso al correo electrónico del usuario
                .build();

        // Crear el cliente de Google Sign-In
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Configuración del botón de inicio de sesión de Google
        SignInButton signInButton = findViewById(R.id.sign_in_button);
        // Cambiar el texto predeterminado del botón
        for (int i = 0; i < signInButton.getChildCount(); i++) {
            View child = signInButton.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                textView.setText(R.string.sign_in_with_google);
            }
        }

        // Configurar el listener para el botón de inicio de sesión
        signInButton.setOnClickListener(v -> signIn());
    }

    /**
     * Inicia el flujo de inicio de sesión con Google.
     */
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    /**
     * Autentica al usuario en Firebase usando el token de ID obtenido de Google.
     *
     * @param idToken El token de ID proporcionado por Google.
     */
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Inicio de sesión exitoso, redirige a MainActivity
                        navigateToMainActivity();
                    } else {
                        // Si falla el inicio de sesión, muestra un mensaje.
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(LoginActivity.this, R.string.authentication_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Redirige al usuario a MainActivity y finaliza la actividad actual.
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // Elimina la actividad de la pila para evitar regresar al login
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}