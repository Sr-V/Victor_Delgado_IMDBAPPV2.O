package database;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

/**
 * Clase opcional para gestionar la conexión a la base de datos de manera sincronizada.
 * Aquí ya no creamos una DB por cada usuario, sino que usamos una única base de datos
 * (singleton) para toda la app.
 */
public class DatabaseManager {

    @SuppressLint("StaticFieldLeak")
    private static SQLiteHelper dbHelper; // Instancia única de SQLiteHelper
    private static final String TAG = "DatabaseManager";

    /**
     * Obtiene la instancia única de SQLiteHelper.
     *
     * @param context Contexto de la aplicación.
     * @return Instancia de SQLiteHelper.
     */
    public static synchronized SQLiteHelper getInstance(Context context) {
        if (dbHelper == null) {
            dbHelper = SQLiteHelper.getInstance(context);
            Log.d(TAG, "Base de datos inicializada (singleton).");
        }
        return dbHelper;
    }

    /**
     * Cierra la base de datos si está abierta.
     * No siempre es necesario, pues Android la cierra al terminar la app,
     * pero puedes hacerlo si gustas.
     */
    public static synchronized void closeDatabase() {
        if (dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
            Log.d(TAG, "Base de datos cerrada.");
        }
    }
}