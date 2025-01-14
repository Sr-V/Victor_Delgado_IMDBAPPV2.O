package database;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.Objects;

/**
 * Clase para gestionar la conexión a la base de datos de manera sincronizada.
 * Permite manejar múltiples usuarios mediante Google Sign-In y asegura que cada usuario
 * tenga su propia base de datos.
 */
public class DatabaseManager {

    @SuppressLint("StaticFieldLeak")
    private static SQLiteHelper dbHelper; // Instancia única de SQLiteHelper
    private static String currentUserId; // ID del usuario actual
    private static final String TAG = "DatabaseManager";

    /**
     * Obtiene la instancia única de SQLiteHelper asociada al usuario actual.
     * Si el usuario cambia, se reinicia la conexión a la base de datos.
     *
     * @param context Contexto de la aplicación.
     * @return Instancia de SQLiteHelper para el usuario autenticado.
     * @throws IllegalStateException Si no hay un usuario autenticado.
     */
    public static synchronized SQLiteHelper getInstance(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context); // Obtener el usuario autenticado
        if (account != null) {
            String userId = account.getId(); // Obtener el ID del usuario

            // Verificar si el usuario ha cambiado o si no hay una base de datos inicializada
            if (dbHelper == null || !Objects.equals(userId, currentUserId)) {
                closeDatabase(); // Cerrar la base de datos anterior, si existe
                dbHelper = new SQLiteHelper(context, userId); // Crear una nueva base de datos para el usuario
                currentUserId = userId; // Actualizar el usuario actual
                Log.d(TAG, "Base de datos inicializada para el usuario: " + userId);
            }
        } else {
            Log.e(TAG, "Usuario no autenticado. No se puede inicializar la base de datos.");
            throw new IllegalStateException("El usuario no está autenticado."); // Lanzar excepción si no hay usuario autenticado
        }
        return dbHelper; // Retornar la instancia de SQLiteHelper
    }

    /**
     * Cierra la base de datos actual si está abierta.
     * También limpia la referencia al usuario actual.
     */
    public static synchronized void closeDatabase() {
        if (dbHelper != null) {
            dbHelper.close(); // Cerrar la conexión a la base de datos
            dbHelper = null; // Limpiar la referencia de la base de datos
            currentUserId = null; // Limpiar el ID del usuario actual
            Log.d(TAG, "Base de datos cerrada.");
        }
    }
}