package edu.pmdm.delgado_victorimdbapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.hbb20.CountryCodePicker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import database.SQLiteHelper;
import database.User;
import database.UsersSync;
import security.KeyStoreManager;

/**
 * Actividad para editar los datos de un usuario, como nombre, correo, dirección, teléfono e imagen.
 * Permite la actualización tanto en una base de datos local como en la nube (Firebase Firestore).
 */
public class EditUserActivity extends AppCompatActivity {

    private static final String TAG = "EditUserActivity";
    private static final String PERMISSION_IMAGE_MESSAGE =
            "Los permisos de cámara y almacenamiento son necesarios para seleccionar una imagen. Por favor, actívalos en Ajustes.";

    private Uri cameraImageUri;
    private String externalPhotoUrl = "";
    private EditText edtName, edtEmail, edtAddress, edtPhone;
    private ImageView userImageView;
    private CountryCodePicker countryCodePicker;
    private FirebaseAuth mAuth;
    private SQLiteHelper dbHelper;
    private KeyStoreManager keystoreManager;
    private String currentUserId;

    // ExecutorService para cargar imágenes en background
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // ActivityResultLaunchers para las distintas acciones
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> selectAddressLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_user);

        keystoreManager = new KeyStoreManager();

        // Establecer márgenes para el contenido en la pantalla (márgenes de sistema)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializar FirebaseAuth y la base de datos local
        mAuth = FirebaseAuth.getInstance();
        dbHelper = SQLiteHelper.getInstance(this);
        initializeDatabaseHelper();

        // Inicializar vistas de la interfaz de usuario
        edtName = findViewById(R.id.etName);
        edtEmail = findViewById(R.id.etEmail);
        edtAddress = findViewById(R.id.etAddress);
        edtPhone = findViewById(R.id.etPhone);
        userImageView = findViewById(R.id.ivUserImage);
        Button btnSelectAddress = findViewById(R.id.btnSelectAddress);
        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        Button btnSave = findViewById(R.id.btnSave);
        countryCodePicker = findViewById(R.id.countryCodePicker);

        // Cargar el código de país previamente seleccionado desde SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        int lastCode = prefs.getInt("LAST_COUNTRY_CODE", -1);
        if (lastCode != -1) {
            countryCodePicker.setCountryForPhoneCode(lastCode);
        } else {
            countryCodePicker.setCountryForPhoneCode(Integer.parseInt(countryCodePicker.getDefaultCountryCode()));
        }
        countryCodePicker.setOnCountryChangeListener(() -> {
            int selectedCode = countryCodePicker.getSelectedCountryCodeAsInt();
            prefs.edit().putInt("LAST_COUNTRY_CODE", selectedCode).apply();
        });

        // Configuración de los ActivityResultLaunchers para cámara, galería y permisos
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (result && cameraImageUri != null) {
                loadImageIntoView(cameraImageUri);
                externalPhotoUrl = "";
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                loadImageIntoView(uri);
                externalPhotoUrl = "";
            }
        });

        selectAddressLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String selectedAddress = result.getData().getStringExtra("SELECTED_ADDRESS");
                        if (selectedAddress != null) {
                            edtAddress.setText(selectedAddress);
                        }
                    }
                });

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    boolean shouldShowSettings = false;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allGranted = false;
                            if (!shouldShowRequestPermissionRationale(entry.getKey())) {
                                shouldShowSettings = true;
                            }
                        }
                    }
                    if (allGranted) {
                        showImageOptionsDialog();
                    } else if (shouldShowSettings) {
                        showSettingsDialog();
                    } else {
                        Toast.makeText(this, "Permisos necesarios para seleccionar imagen.", Toast.LENGTH_SHORT).show();
                    }
                });

        // Cargar imagen y datos del usuario si existen en el Intent
        String photoUriString = getIntent().getStringExtra("EXTRA_PROFILE_PICTURE_URI");
        if (photoUriString != null && !photoUriString.isEmpty()) {
            Uri photoUri = Uri.parse(photoUriString);
            loadImageIntoView(photoUri);
            if (photoUriString.startsWith("http")) {
                externalPhotoUrl = photoUriString;
            } else {
                cameraImageUri = photoUri;
            }
        }

        // Cargar datos del usuario desde la base de datos local
        loadUserData();

        // Configurar acciones para los botones
        btnSelectAddress.setOnClickListener(v -> abrirSelectAddressActivity());
        btnSelectImage.setOnClickListener(v -> checkImagePermissionsAndShowOptions());
        btnSave.setOnClickListener(v -> saveUserData());
    }

    /**
     * Método para abrir la actividad de selección de dirección.
     */
    private void abrirSelectAddressActivity() {
        Intent intent = new Intent(this, SelectAddressActivity.class);
        selectAddressLauncher.launch(intent); // Lanzar la actividad usando el launcher
    }

    /**
     * Método para cargar la imagen desde una URI en el ImageView.
     * @param uri La URI de la imagen a cargar.
     */
    private void loadImageIntoView(Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            loadImageFromUrl(userImageView, uri.toString()); // Cargar imagen desde URL
            return;
        }
        executorService.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream != null) {
                    bitmap = BitmapFactory.decodeStream(inputStream); // Decodificar imagen de URI
                }
            } catch (Exception e) {
                Log.e("EditUserActivity", "Error cargando imagen desde URI", e);
            }
            final Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (finalBitmap != null) {
                    userImageView.setImageBitmap(finalBitmap); // Mostrar la imagen cargada
                } else {
                    userImageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto
                }
            });
        });
    }

    /**
     * Método para mostrar un diálogo con opciones de configuración cuando faltan permisos.
     */
    private void showSettingsDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Permisos necesarios")
                .setMessage(PERMISSION_IMAGE_MESSAGE) // Mensaje para solicitar permisos
                .setPositiveButton("Ir a Ajustes", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName())); // Abrir la pantalla de configuración de la app
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e("EditUserActivity", "No se pudo abrir la configuración", e);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Método para mostrar un diálogo con opciones de selección de imagen (Cámara, Galería o URL).
     */
    private void showImageOptionsDialog() {
        String[] items = {"Cámara", "Galería", "URL externa"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Seleccionar imagen")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openCamera(); // Abrir cámara
                            break;
                        case 1:
                            openGallery(); // Abrir galería
                            break;
                        case 2:
                            showUrlDialog(); // Introducir URL de imagen
                            break;
                    }
                })
                .create()
                .show();
    }

    /**
     * Abre la cámara para capturar una imagen.
     */
    private void openCamera() {
        launchCameraIntent();
    }

    /**
     * Lanza un intento de cámara para tomar una foto.
     */
    private void launchCameraIntent() {
        File photoFile;
        try {
            photoFile = createTempImageFile();  // Crea un archivo temporal para la foto
        } catch (IOException e) {
            Log.e("EditUserActivity", "Error creando archivo para la cámara", e);
            Toast.makeText(this, "Error creando archivo para la cámara", Toast.LENGTH_SHORT).show();
            return;
        }

        // Obtiene la URI del archivo y lanza la cámara
        cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        cameraLauncher.launch(cameraImageUri);  // Usa el launcher de cámara para abrirla
    }

    /**
     * Abre la galería para que el usuario seleccione una imagen.
     */
    private void openGallery() {
        galleryLauncher.launch("image/*");  // Lanza la galería para seleccionar una imagen
    }

    /**
     * Muestra un cuadro de diálogo para que el usuario ingrese una URL de una imagen.
     */
    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://ejemplo.com/foto.png");

        // Crear un diálogo con la opción de ingresar una URL
        new android.app.AlertDialog.Builder(this)
                .setTitle("Introduce la URL de la imagen")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String url = input.getText().toString().trim();  // Obtiene la URL ingresada
                    if (!url.isEmpty()) {
                        externalPhotoUrl = url;
                        cameraImageUri = null;  // Reinicia la URI de la cámara
                        loadImageFromUrl(userImageView, url);  // Carga la imagen desde la URL
                    } else {
                        Toast.makeText(this, "URL vacía", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)  // Botón para cancelar
                .show();
    }

    /**
     * Crea un archivo temporal para almacenar la imagen tomada desde la cámara.
     * @return El archivo temporal para la imagen.
     * @throws IOException Si ocurre un error al crear el archivo.
     */
    private File createTempImageFile() throws IOException {
        // Generar un nombre único para la imagen utilizando la fecha y hora actuales
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";

        // Obtener el directorio para guardar las imágenes
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        // Crear el archivo temporal
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    /**
     * Guarda los datos del usuario en la base de datos local y Firebase Firestore.
     */
    private void saveUserData() {
        String phoneNumber = edtPhone.getText().toString().trim();
        String countryCode = countryCodePicker.getSelectedCountryCode();
        String selectedCountryNameCode = countryCodePicker.getSelectedCountryNameCode();
        String fullPhone = "+" + countryCode + phoneNumber;

        // Verificar si el número de teléfono es válido
        if (!isValidPhoneNumber(fullPhone, selectedCountryNameCode)) {
            Toast.makeText(this, "Número de teléfono inválido para el país seleccionado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verificar si el nombre no está vacío y es razonablemente corto
        String newName = edtName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newName.length() > 50) {
            Toast.makeText(this, "El nombre es demasiado largo", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convertir la imagen a Base64
        String base64Image = convertImageToBase64(userImageView);

        // Cifrar los datos sensibles
        String encryptedPhone = keystoreManager.encrypt(phoneNumber);
        String encryptedAddress = keystoreManager.encrypt(edtAddress.getText().toString().trim());
        if (encryptedPhone == null || encryptedAddress == null) {
            Toast.makeText(this, "Error al cifrar los datos. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Actualizar los datos en la base de datos local
            updateLocalUserData(user.getUid(), newName, edtEmail.getText().toString().trim(),
                    encryptedPhone, encryptedAddress, base64Image);

            // Sincronizar los datos con Firebase Firestore
            UsersSync usersSync = new UsersSync(this, currentUserId);
            usersSync.syncActivityLog().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Actualizar Firestore con los nuevos datos del usuario
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", newName);
                    userData.put("email", edtEmail.getText().toString().trim());
                    userData.put("phone", encryptedPhone);
                    userData.put("address", encryptedAddress);
                    userData.put("image", base64Image);

                    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                    firestore.collection("users").document(user.getUid())
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Datos sincronizados y guardados correctamente.", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error al sincronizar los datos en la nube", Toast.LENGTH_SHORT).show();
                                Log.e("EditUserActivity", "Error al guardar datos en Firestore", e);
                            });
                } else {
                    Toast.makeText(this, "Error al sincronizar los datos", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Convierte la imagen del ImageView en una cadena Base64.
     * @param imageView El ImageView que contiene la imagen.
     * @return La cadena Base64 que representa la imagen.
     */
    private String convertImageToBase64(ImageView imageView) {
        // Obtener el bitmap de la imagen en el ImageView
        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

        // Comprimir la imagen antes de convertirla a Base64
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);

        byte[] byteArray = byteArrayOutputStream.toByteArray();

        // Convertir la imagen comprimida a una cadena Base64
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    /**
     * Verifica si el número de teléfono es válido utilizando la librería libphonenumber.
     * @param fullPhone El número de teléfono completo con el código de país.
     * @param countryCode El código del país.
     * @return true si el número es válido, false en caso contrario.
     */
    private boolean isValidPhoneNumber(String fullPhone, String countryCode) {
        if (TextUtils.isEmpty(fullPhone) || TextUtils.isEmpty(countryCode)) {
            return false;
        }

        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(fullPhone, countryCode);
            return phoneNumberUtil.isValidNumber(phoneNumber);  // Verifica si el número es válido
        } catch (Exception e) {
            return false;  // Si no se puede analizar o no es válido, devuelve false
        }
    }

    /**
     * Carga la imagen desde una URL en el ImageView proporcionado.
     * @param imageView El ImageView donde se debe mostrar la imagen.
     * @param url La URL de la imagen.
     */
    private void loadImageFromUrl(ImageView imageView, String url) {
        if (url != null && !url.isEmpty()) {
            executorService.execute(() -> {
                Bitmap bitmap = null;
                InputStream inputStream = null;
                try {
                    inputStream = new java.net.URL(url).openStream();

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(inputStream, null, options);

                    inputStream.close();
                    inputStream = new java.net.URL(url).openStream();

                    // Reducción de tamaño de la imagen si es necesario
                    options.inSampleSize = calculateInSampleSize(options);

                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options);

                    if (bitmap != null) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true);
                    }
                } catch (Exception e) {
                    Log.e("EditUserActivity", "Error loading image from URL", e);
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
                        imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto en caso de error
                    }
                });
            });
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round); // Imagen por defecto si la URL está vacía
        }
    }

    /**
     * Calcula el tamaño de muestra de la imagen para reducir la memoria utilizada.
     * @param options Las opciones de la imagen que contienen sus dimensiones.
     * @return El tamaño de muestra calculado.
     */
    private int calculateInSampleSize(BitmapFactory.Options options) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > 512 || width > 512) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= 512 && (halfWidth / inSampleSize) >= 512) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Actualiza los datos del usuario en la base de datos local.
     * @param userId El ID del usuario.
     * @param newName El nuevo nombre del usuario.
     * @param newEmail El nuevo correo electrónico del usuario.
     * @param encryptedPhone El nuevo teléfono cifrado del usuario.
     * @param encryptedAddress La nueva dirección cifrada del usuario.
     * @param newPhotoUrl La nueva URL de la foto del usuario.
     */
    private void updateLocalUserData(String userId,
                                     String newName,
                                     String newEmail,
                                     String encryptedPhone,
                                     String encryptedAddress,
                                     String newPhotoUrl) {
        // 1. Verificar si el usuario existe en la base de datos
        User existingUser = dbHelper.getUser(userId);

        // 2. Conservar datos que no se modifican (login_time y logout_time)
        if (existingUser == null) {
            Log.e("EditUserActivity", "El usuario no existe en la base de datos local: " + userId);
            return;
        }

        // 3. Actualizar solo los campos específicos usando el nuevo método
        boolean success = dbHelper.updateUserSpecificFields(
                userId,
                newName,          // Nuevo nombre (puede ser null si no se actualiza)
                newEmail,         // Nuevo correo electrónico (puede ser null si no se actualiza)
                encryptedAddress, // Dirección cifrada (puede ser null si no se actualiza)
                newPhotoUrl,      // Nueva imagen (puede ser null si no se actualiza)
                encryptedPhone    // Teléfono cifrado (puede ser null si no se actualiza)
        );

        // 4. Manejar el resultado de la actualización
        if (!success) {
            Log.e("EditUserActivity", "Error al actualizar los campos específicos del usuario: " + userId);
        } else {
            Log.d("EditUserActivity", "Campos específicos actualizados correctamente para el usuario: " + userId);
        }
    }

    /**
     * Verifica si el usuario tiene los permisos necesarios para acceder a la cámara y la galería.
     * Si los permisos están concedidos, muestra las opciones para seleccionar una imagen.
     */
    private void checkImagePermissionsAndShowOptions() {
        ArrayList<String> permissionsList = new ArrayList<>();
        permissionsList.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        boolean permissionsGranted = true;
        for (String permission : permissionsList) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }
        if (permissionsGranted) {
            showImageOptionsDialog();
        } else {
            permissionLauncher.launch(permissionsList.toArray(new String[0]));
        }
    }

    /**
     * Inicializa el helper de la base de datos y obtiene el userId del usuario autenticado.
     */
    private void initializeDatabaseHelper() {
        dbHelper = SQLiteHelper.getInstance(this);
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            currentUserId = null;
            Log.e(TAG, "Usuario no autenticado. No se encontró userId.");
            return;
        }
        currentUserId = firebaseUser.getUid();
        Log.d(TAG, "Base de datos lista para usar con el userId: " + currentUserId);
    }

    /**
     * Carga los datos del usuario desde la base de datos local o Firebase.
     */
    private void loadUserData() {
        if (currentUserId == null) {
            Log.e(TAG, "ID de usuario no disponible");
            return;
        }

        // 1. Intentar cargar datos desde la base de datos local
        User localUser = dbHelper.getUser(currentUserId);

        if (localUser != null) {
            // Cargar datos locales en la UI
            edtName.setText(localUser.getName());
            edtEmail.setText(localUser.getEmail());
            edtPhone.setText(keystoreManager.decrypt(localUser.getPhone()));
            edtAddress.setText(keystoreManager.decrypt(localUser.getAddress()));

            // Manejar la imagen (Base64 o URL)
            if (localUser.getImage() != null && !localUser.getImage().isEmpty()) {
                if (localUser.getImage().startsWith("http")) {
                    loadImageFromUrl(userImageView, localUser.getImage());
                } else {
                    decodeBase64Image(localUser.getImage());
                }
            }
        } else {
            // 2. Si no hay datos locales, obtener del proveedor de autenticación
            FirebaseUser firebaseUser = mAuth.getCurrentUser();

            if (firebaseUser != null) {
                // Obtener datos según el proveedor
                String providerData = getProviderData(firebaseUser);

                edtName.setText(firebaseUser.getDisplayName());
                edtEmail.setText(firebaseUser.getEmail());
                edtPhone.setText(firebaseUser.getPhoneNumber());

                // Cargar imagen del proveedor
                if (firebaseUser.getPhotoUrl() != null) {
                    loadImageFromUrl(userImageView, firebaseUser.getPhotoUrl().toString());
                }

                Toast.makeText(this,
                        "Datos cargados desde: " + providerData,
                        Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Usuario no autenticado en Firebase");
                finish();
            }
        }
    }

    /**
     * Método auxiliar para obtener el proveedor de autenticación.
     * @param user El usuario actual de Firebase.
     * @return El nombre del proveedor de autenticación.
     */
    private String getProviderData(FirebaseUser user) {
        for (UserInfo profile : user.getProviderData()) {
            String providerId = profile.getProviderId();
            switch (providerId) {
                case "google.com":
                    return "Google";
                case "facebook.com":
                    return "Facebook";
                case "password":
                    return "Email/Contraseña";
            }
        }
        return "Proveedor desconocido";
    }

    /**
     * Método para decodificar imágenes Base64 y mostrarlas en el ImageView.
     * @param base64Image La cadena Base64 que representa la imagen.
     */
    private void decodeBase64Image(String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            userImageView.setImageBitmap(bitmap);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decodificando imagen Base64", e);
            userImageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }
}