package edu.pmdm.delgado_victorimdbapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Objects;

/**
 * Clase que representa la pantalla de Login, con opciones de:
 * <ul>
 *     <li>Registro con email y contraseña</li>
 *     <li>Login con email y contraseña</li>
 *     <li>Login con Google</li>
 *     <li>Login con Facebook</li>
 * </ul>
 */
public class LoginActivity extends AppCompatActivity {

    // Tag para logs
    private static final String TAG = "LoginActivity";

    // Instancia de FirebaseAuth
    private FirebaseAuth mAuth;

    // Cliente de Google Sign-In
    private GoogleSignInClient googleSignInClient;

    // Lanzadores de actividades para Google Sign-In
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<Intent> googleSignInLinkingLauncher;

    // CallbackManager para Facebook
    private CallbackManager callbackManager;

    // Credencial de Facebook (para vinculación)
    private AuthCredential facebookCredential;

    // Credencial de Email/Contraseña (para vinculación)
    private AuthCredential emailCredential;

    // Botón de SignIn con Google
    private SignInButton signInGoogleButton;

    // Campos de texto para login con email y contraseña
    private EditText emailField, passwordField;

    // Botones para registrar y loguear con email/contraseña
    private Button registerButton, loginButton;

    /**
     * Se llama cuando la actividad es creada.
     * Aquí se inicializan Firebase, Facebook, GoogleSignIn y se configuran los listeners.
     *
     * @param savedInstanceState Estado de la instancia guardada.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuración de Facebook antes de setContentView
        FacebookSdk.setApplicationId(getString(R.string.facebook_app_id));
        FacebookSdk.setClientToken(getString(R.string.facebook_client_token));
        FacebookSdk.sdkInitialize(getApplicationContext());

        setContentView(R.layout.activity_login);

        // Inicializar Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        // Inicializar CallbackManager de Facebook
        callbackManager = CallbackManager.Factory.create();

        // Inicializar opciones de Google Sign-In
        GoogleSignInOptions gOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // default_web_client_id
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gOptions);

        // Verificar si ya existe un usuario autenticado en Firebase
        if (mAuth.getCurrentUser() != null) {
            goToMain();
        }

        // Referencias a vistas
        emailField = findViewById(R.id.email_field);
        passwordField = findViewById(R.id.password_field);
        registerButton = findViewById(R.id.register_button);
        loginButton = findViewById(R.id.login_button);
        signInGoogleButton = findViewById(R.id.sign_in_button);
        LoginButton loginFacebookButton = findViewById(R.id.facebook_login_button);

        // Personalizar botón de Google
        customizeGoogleButton(signInGoogleButton);

        // Listener para registrar con email y contraseña
        registerButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show();
                return;
            }

            registerWithEmail(email, password);
        });

        // Listener para login con email y contraseña
        loginButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show();
                return;
            }

            loginWithEmail(email, password);
        });

        // Listener para login con Google
        signInGoogleButton.setOnClickListener(v -> {
            signInGoogleButton.setEnabled(false);
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        // Configurar botón de Facebook
        loginFacebookButton.setReadPermissions("email", "public_profile");
        loginFacebookButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "Facebook Login Success: " + loginResult);
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "Facebook Login Canceled");
                signInGoogleButton.setEnabled(true); // Re-habilitar botón de Google
            }

            @Override
            public void onError(@NonNull FacebookException e) {
                Log.e(TAG, "Facebook Login Error", e);
                Toast.makeText(LoginActivity.this, "Error al iniciar sesión con Facebook", Toast.LENGTH_SHORT).show();
                signInGoogleButton.setEnabled(true); // Re-habilitar botón de Google
            }
        });

        // Configurar lanzador de Google Sign-In
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    signInGoogleButton.setEnabled(true);
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken());
                            }
                        } catch (ApiException e) {
                            Log.e(TAG, "Google Sign-In Error", e);
                            Toast.makeText(LoginActivity.this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Configurar lanzador para la vinculación de cuentas (Facebook ↔ Google o Email ↔ Google)
        googleSignInLinkingLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) {
                                // CASO 1: Vincular cuenta de Google con la credencial de Facebook
                                if (facebookCredential != null) {
                                    linkGoogleWithFacebook(account);
                                }
                                // CASO 2: Vincular cuenta de Google con la credencial de Email/Contraseña
                                else if (emailCredential != null) {
                                    linkGoogleWithEmail(account);
                                }
                            }
                        } catch (ApiException e) {
                            Log.e(TAG, "Google Sign-In Error for linking", e);
                            Toast.makeText(LoginActivity.this, "Error al vincular con Google.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Vinculación cancelada.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Registra un usuario usando email y contraseña.
     * Si ya existe una cuenta con ese correo (FirebaseAuthUserCollisionException),
     * se solicita vincular con Google (o el método que tú desees).
     *
     * @param email    Email del usuario.
     * @param password Contraseña del usuario.
     */
    private void registerWithEmail(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Registro exitoso, ya tenemos al usuario autenticado
                        goToMain();
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            // Existe una cuenta con ese correo en otro proveedor (Google, Facebook, etc.)
                            Log.w(TAG, "Collision al registrar con Email/Contraseña", task.getException());

                            // Guardar la credencial de Email/Contraseña
                            emailCredential = EmailAuthProvider.getCredential(email, password);

                            // Mostrar diálogo para vincular con Google (mismo flujo que con Facebook)
                            showLinkingDialogForEmail(email);
                        } else {
                            // Otro tipo de error
                            Log.e(TAG, "Error al registrar con Email/Contraseña", task.getException());
                            Toast.makeText(this, "Error al registrar: "
                                            + Objects.requireNonNull(task.getException()).getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Inicia sesión con email y contraseña.
     *
     * @param email    Email del usuario.
     * @param password Contraseña del usuario.
     */
    private void loginWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Login exitoso
                        goToMain();
                    } else {
                        Log.e(TAG, "Error al iniciar sesión con Email/Contraseña", task.getException());
                        Toast.makeText(this, "Error al iniciar sesión: "
                                        + Objects.requireNonNull(task.getException()).getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Muestra un diálogo que pregunta si queremos vincular la cuenta de Email/Contraseña
     * con Google (similar a la vinculación que se hace con Facebook).
     *
     * @param email Correo con el cual hay colisión.
     */
    private void showLinkingDialogForEmail(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Cuenta existente")
                .setMessage("Ya existe una cuenta con el correo " + email
                        + ". ¿Deseas vincular tu Email/Contraseña con Google?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    // Lanzar flujo de Google SignIn para vincular
                    Intent linkIntent = googleSignInClient.getSignInIntent();
                    googleSignInLinkingLauncher.launch(linkIntent);
                })
                .setNegativeButton("No", (dialog, which) -> {
                    Toast.makeText(LoginActivity.this, "No se han vinculado las cuentas.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Realiza la autenticación con Google en Firebase usando el ID token obtenido.
     *
     * @param idToken Token de la cuenta de Google.
     */
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                goToMain();
            } else {
                Log.e(TAG, "Google Sign-In Failed", task.getException());
                Toast.makeText(LoginActivity.this, "Error autenticando con Google", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Maneja el AccessToken obtenido de Facebook, e intenta iniciar sesión con Firebase.
     *
     * @param token Token de acceso de Facebook.
     */
    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Login exitoso
                        goToMain();
                    } else {
                        // Si hay colisión de cuentas (mismo email en otra autenticación)
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Log.w(TAG, "Facebook Sign-In Collision", task.getException());
                            FirebaseAuthUserCollisionException collisionEx =
                                    (FirebaseAuthUserCollisionException) task.getException();
                            String email = collisionEx.getEmail();

                            // Guardar credencial de Facebook y ofrecer vinculación con Google
                            facebookCredential = credential;
                            showLinkingDialog(email);
                        } else {
                            // Otro tipo de error
                            Log.e(TAG, "Facebook Sign-In Failed", task.getException());
                            Toast.makeText(LoginActivity.this, "Error autenticando con Facebook", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Muestra un diálogo que pregunta al usuario si quiere vincular la cuenta de Facebook con Google.
     *
     * @param email Email con el que ocurre la colisión.
     */
    private void showLinkingDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Cuenta existente")
                .setMessage("Ya existe una cuenta con el correo " + email
                        + ". ¿Deseas vincular tu cuenta de Facebook con Google?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    // Lanzar Google SignIn flow para vincular
                    Intent linkIntent = googleSignInClient.getSignInIntent();
                    googleSignInLinkingLauncher.launch(linkIntent);
                })
                .setNegativeButton("No", (dialog, which) -> {
                    Toast.makeText(LoginActivity.this, "Las cuentas no fueron vinculadas.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Vincula la cuenta de Google con la credencial de Facebook previamente guardada.
     *
     * @param account Cuenta de Google.
     */
    private void linkGoogleWithFacebook(GoogleSignInAccount account) {
        AuthCredential googleCredential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        // Primero, iniciar sesión con la credencial de Google
        mAuth.signInWithCredential(googleCredential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                // Luego, vincular la credencial de Facebook
                Objects.requireNonNull(mAuth.getCurrentUser())
                        .linkWithCredential(facebookCredential)
                        .addOnCompleteListener(this, linkTask -> {
                            if (linkTask.isSuccessful()) {
                                Log.d(TAG, "Vinculación Google-Facebook exitosa.");
                                Toast.makeText(LoginActivity.this, "Cuentas vinculadas exitosamente.", Toast.LENGTH_SHORT).show();
                                goToMain();
                            } else {
                                Log.e(TAG, "Error al vincular credenciales.", linkTask.getException());
                                Toast.makeText(LoginActivity.this, "Error al vincular cuentas.", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Log.e(TAG, "Error al iniciar sesión con Google para vinculación", task.getException());
                Toast.makeText(LoginActivity.this, "Error autenticando Google para vinculación.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Vincula la cuenta de Google con la credencial de Email/Contraseña.
     *
     * @param account Cuenta de Google.
     */
    private void linkGoogleWithEmail(GoogleSignInAccount account) {
        AuthCredential googleCredential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        // Primero, iniciar sesión con la credencial de Google
        mAuth.signInWithCredential(googleCredential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                // Luego, vincular la credencial de Email/Contraseña
                Objects.requireNonNull(mAuth.getCurrentUser())
                        .linkWithCredential(emailCredential)
                        .addOnCompleteListener(this, linkTask -> {
                            if (linkTask.isSuccessful()) {
                                Log.d(TAG, "Vinculación Google-Email exitosa.");
                                Toast.makeText(LoginActivity.this, "Cuentas vinculadas exitosamente.", Toast.LENGTH_SHORT).show();
                                goToMain();
                            } else {
                                Log.e(TAG, "Error al vincular Email/Contraseña", linkTask.getException());
                                Toast.makeText(LoginActivity.this, "Error al vincular cuentas.", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Log.e(TAG, "Error al iniciar sesión con Google para vinculación", task.getException());
                Toast.makeText(LoginActivity.this, "Error autenticando Google para vinculación.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Lanza la actividad principal y finaliza la actual.
     */
    private void goToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Personaliza el texto del botón de Google.
     *
     * @param button Botón de Google a personalizar.
     */
    @SuppressLint("SetTextI18n")
    private void customizeGoogleButton(SignInButton button) {
        for (int i = 0; i < button.getChildCount(); i++) {
            View view = button.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setText("Sign in with Google");
                break;
            }
        }
    }

    /**
     * Método que maneja los resultados de las actividades llamadas (Facebook, Google).
     *
     * @param requestCode Código de solicitud.
     * @param resultCode  Código de resultado.
     * @param data        Datos de la intención.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Delegar resultado al callback de Facebook
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }
}