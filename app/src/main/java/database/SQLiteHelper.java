package database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLiteHelper maneja la base de datos local de la aplicación.
 * Contiene dos tablas: 'users' y 'favorites'.
 * <p>
 * Se han agregado modificaciones para notificar cuando se añade o elimina
 * una película, facilitando la sincronización entre la base de datos local y la nube.
 */
public class SQLiteHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "app_database.db"; // Nombre de la base de datos
    private static final int DATABASE_VERSION = 1; // Versión de la base de datos
    private final Context context;

    // Nombres de las tablas
    private static final String TABLE_USERS = "users";
    private static final String TABLE_FAVORITES = "favorites";

    // Columnas de la tabla 'users'
    private static final String COLUMN_USER_ID = "user_id"; // PRIMARY KEY
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_LOGIN_TIME = "login_time";
    private static final String COLUMN_LOGOUT_TIME = "logout_time";
    private static final String COLUMN_ADDRESS = "address";
    private static final String COLUMN_PHONE = "phone";
    private static final String COLUMN_IMAGE = "image";

    // Columnas de la tabla 'favorites'
    private static final String COLUMN_MOVIE_ID = "movie_id"; // PRIMARY KEY parte 2
    private static final String COLUMN_POSTER = "poster";
    private static final String COLUMN_TITLE = "title";

    // Instancia singleton
    @SuppressLint("StaticFieldLeak")
    private static SQLiteHelper instance;

    // Interfaz para notificar cambios en los favoritos
    public interface OnFavoritesChangedListener {
        void onFavoriteAdded(Movie movie);
        void onFavoriteRemoved(String movieId);
    }

    // Variable para almacenar el listener (puede ser nulo si no se ha registrado)
    private static OnFavoritesChangedListener favoritesChangedListener;

    /**
     * Establece el listener para cambios en los favoritos.
     *
     * @param listener Listener que recibirá las notificaciones.
     */
    public static void setOnFavoritesChangedListener(OnFavoritesChangedListener listener) {
        favoritesChangedListener = listener;
    }

    /**
     * Obtiene la instancia única (Singleton) de SQLiteHelper.
     *
     * @param context Contexto de la aplicación
     * @return Instancia de SQLiteHelper
     */
    public static synchronized SQLiteHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SQLiteHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Constructor privado para forzar el uso de getInstance().
     *
     * @param context Contexto de la aplicación
     */
    private SQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    /**
     * Se llama cuando se crea la base de datos por primera vez.
     *
     * @param db La base de datos
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // SQL para crear la tabla 'users'
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + " ("
                + COLUMN_USER_ID + " TEXT PRIMARY KEY, "
                + COLUMN_NAME + " TEXT, "
                + COLUMN_EMAIL + " TEXT, "
                + COLUMN_LOGIN_TIME + " TEXT, "
                + COLUMN_LOGOUT_TIME + " TEXT, "
                + COLUMN_ADDRESS + " TEXT, "
                + COLUMN_PHONE + " TEXT, "
                + COLUMN_IMAGE + " TEXT"
                + ")";

        // SQL para crear la tabla 'favorites' con restricción de clave foránea
        String CREATE_FAVORITES_TABLE = "CREATE TABLE " + TABLE_FAVORITES + " ("
                + COLUMN_USER_ID + " TEXT NOT NULL, "
                + COLUMN_MOVIE_ID + " TEXT NOT NULL, "
                + COLUMN_POSTER + " TEXT, "
                + COLUMN_TITLE + " TEXT, "
                + "PRIMARY KEY(" + COLUMN_USER_ID + ", " + COLUMN_MOVIE_ID + "), "
                + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_USER_ID + ") ON DELETE CASCADE"
                + ")";

        // Ejecutar los comandos SQL para crear las tablas
        db.execSQL(CREATE_USERS_TABLE);
        db.execSQL(CREATE_FAVORITES_TABLE);
    }

    /**
     * Se llama cuando se actualiza la versión de la base de datos.
     *
     * @param db         La base de datos
     * @param oldVersion Versión anterior
     * @param newVersion Nueva versión
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Eliminar tablas existentes
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        // Recrear las tablas
        onCreate(db);
    }

    /**
     * Configuración para habilitar restricciones de claves foráneas.
     *
     * @param db La base de datos
     */
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Verifica si un usuario existe en la tabla 'users'.
     *
     * @param userId ID del usuario
     * @return True si existe, False en caso contrario
     */
    public boolean doesUserExist(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_USERS,
                new String[]{COLUMN_USER_ID},
                COLUMN_USER_ID + "=?",
                new String[]{userId},
                null,
                null,
                null
        )) {
            return (cursor != null && cursor.moveToFirst());
        } catch (Exception e) {
            Log.e("SQLiteHelper", "Error al verificar existencia del usuario: " + userId, e);
            return false;
        }
    }

    /**
     * Actualiza solo los campos específicos (name, email, address, image, phone) de un usuario en la tabla 'users'.
     * Además, sincroniza los cambios con Firestore si la actualización local es exitosa.
     *
     * @param userId        ID del usuario a actualizar
     * @param newName       Nuevo nombre del usuario (puede ser null si no se desea actualizar)
     * @param newEmail      Nuevo correo electrónico del usuario (puede ser null si no se desea actualizar)
     * @param newAddress    Nueva dirección del usuario (puede ser null si no se desea actualizar)
     * @param newImage      Nueva imagen del usuario (puede ser null si no se desea actualizar)
     * @param newPhone      Nuevo teléfono del usuario (puede ser null si no se desea actualizar)
     * @return True si la actualización fue exitosa, False en caso contrario
     */
    public boolean updateUserSpecificFields(String userId, String newName, String newEmail,
                                            String newAddress, String newImage, String newPhone) {
        if (userId == null || userId.isEmpty()) {
            Log.e("SQLiteHelper", "No se puede actualizar: El ID de usuario es nulo o vacío.");
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // Agregar solo los campos que no son nulos
        if (newName != null) {
            values.put(COLUMN_NAME, newName);
        }
        if (newEmail != null) {
            values.put(COLUMN_EMAIL, newEmail);
        }
        if (newAddress != null) {
            values.put(COLUMN_ADDRESS, newAddress);
        }
        if (newImage != null) {
            values.put(COLUMN_IMAGE, newImage);
        }
        if (newPhone != null) {
            values.put(COLUMN_PHONE, newPhone);
        }

        // Verificar si hay algo que actualizar (compatible con API < 30)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (values.isEmpty()) { // Usamos size() en lugar de isEmpty()
                Log.i("SQLiteHelper", "No hay campos para actualizar para el usuario: " + userId);
                return false;
            }
        }

        // Realizar la actualización en la base de datos local
        int rowsUpdated = db.update(TABLE_USERS, values, COLUMN_USER_ID + "=?", new String[]{userId});
        if (rowsUpdated > 0) {
            Log.d("SQLiteHelper", "Campos actualizados exitosamente para el usuario: " + userId);

            // Sincronizar los cambios con Firestore
            UsersSync usersSync = new UsersSync(context, userId); // Usa el contexto almacenado
            usersSync.syncSpecificFields().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d("SQLiteHelper", "Sincronización exitosa con Firestore para el usuario: " + userId);
                } else {
                    Log.e("SQLiteHelper", "Error al sincronizar con Firestore para el usuario: " + userId, task.getException());
                }
            });

            return true;
        } else {
            Log.e("SQLiteHelper", "Error al actualizar campos para el usuario: " + userId);
            return false;
        }
    }

    /**
     * Agrega o actualiza un usuario en la tabla 'users'.
     *
     * @param user Objeto User con los datos del usuario
     * @return True si la inserción fue exitosa, False en caso contrario
     */
    public boolean addUser(User user) {
        if (user == null || user.getUserId() == null) {
            Log.e("SQLiteHelper", "No se puede agregar un usuario nulo o con ID nulo.");
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, user.getUserId());
        values.put(COLUMN_NAME, user.getName());
        values.put(COLUMN_EMAIL, user.getEmail());
        values.put(COLUMN_LOGIN_TIME, user.getLoginTime());
        values.put(COLUMN_LOGOUT_TIME, user.getLogoutTime());
        values.put(COLUMN_ADDRESS, user.getAddress());
        values.put(COLUMN_PHONE, user.getPhone());
        values.put(COLUMN_IMAGE, user.getImage());

        long result = db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (result == -1) {
            Log.e("SQLiteHelper", "Error al insertar/actualizar usuario: " + user.getUserId());
            return false;
        } else {
            Log.d("SQLiteHelper", "Usuario insertado/actualizado exitosamente: " + user.getUserId());
            return true;
        }
    }

    /**
     * Obtiene un usuario de la tabla 'users' por su user_id.
     *
     * @param userId ID del usuario
     * @return Objeto User si se encuentra, null en caso contrario
     */
    public User getUser(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        User user = null;
        try {
            cursor = db.query(
                    TABLE_USERS,
                    null, // Todas las columnas
                    COLUMN_USER_ID + "=?",
                    new String[]{userId},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
                String email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL));
                String loginTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOGIN_TIME));
                String logoutTime = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOGOUT_TIME));
                String address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS));
                String phone = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE));
                String image = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE));

                user = new User(id, name, email, loginTime, logoutTime, address, phone, image);
            }
        } catch (Exception e) {
            Log.e("SQLiteHelper", "Error al obtener usuario: " + userId, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return user;
    }

    /**
     * Verifica si una película está en los favoritos de un usuario.
     *
     * @param userId  ID del usuario
     * @param movieId ID de la película
     * @return True si ya está en favoritos, False en caso contrario
     */
    public boolean isMovieFavorite(String userId, String movieId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FAVORITES,
                new String[]{COLUMN_MOVIE_ID},
                COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                new String[]{userId, movieId},
                null,
                null,
                null
        )) {

            return (cursor != null && cursor.moveToFirst());
        } catch (Exception e) {
            Log.e("SQLiteHelper", "Error al verificar si la película es favorita: " + movieId + " para el usuario: " + userId, e);
            return false;
        }
    }

    /**
     * Agrega una película a los favoritos de un usuario.
     * Además de insertarla en la base de datos local, notifica el cambio para la sincronización.
     *
     * @param userId  ID del usuario
     * @param movieId ID de la película
     * @param poster  URL del póster de la película
     * @param title   Título de la película
     */
    public void addMovieToFavorites(String userId, String movieId, String poster, String title) {
        if (!doesUserExist(userId)) {
            Log.e("SQLiteHelper", "No se puede agregar a favoritos: El usuario " + userId + " no existe.");
            return;
        }

        if (isMovieFavorite(userId, movieId)) {
            Log.i("SQLiteHelper", "La película " + movieId + " ya es favorita para el usuario " + userId);
            return;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, userId);
        values.put(COLUMN_MOVIE_ID, movieId);
        values.put(COLUMN_POSTER, poster);
        values.put(COLUMN_TITLE, title);

        long result = db.insert(TABLE_FAVORITES, null, values);
        if (result == -1) {
            Log.e("SQLiteHelper", "Error al agregar película a favoritos: " + movieId + " para el usuario " + userId);
        } else {
            Log.d("SQLiteHelper", "Película agregada a favoritos: " + movieId + " para el usuario " + userId);
            // Notificar que se ha añadido una película (para sincronización)
            if (favoritesChangedListener != null) {
                favoritesChangedListener.onFavoriteAdded(new Movie(movieId, poster, title));
            }
        }
    }

    /**
     * Elimina una película de los favoritos de un usuario.
     * Además de eliminarla de la base de datos local, notifica el cambio para la sincronización.
     *
     * @param userId  ID del usuario
     * @param movieId ID de la película
     * @return Número de filas eliminadas
     */
    public int removeMovieFromFavorites(String userId, String movieId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(
                TABLE_FAVORITES,
                COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                new String[]{userId, movieId}
        );
        Log.d("SQLiteHelper", "Número de favoritos eliminados: " + rowsDeleted + " para el usuario " + userId);
        // Notificar que se ha eliminado una película (para sincronización)
        if (rowsDeleted > 0 && favoritesChangedListener != null) {
            favoritesChangedListener.onFavoriteRemoved(movieId);
        }
        return rowsDeleted;
    }

    /**
     * Obtiene todas las películas favoritas de un usuario.
     *
     * @param userId ID del usuario
     * @return Lista de objetos Movie representando los favoritos
     */
    public List<Movie> getFavoriteMovies(String userId) {
        List<Movie> favoriteMovies = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FAVORITES,
                new String[]{COLUMN_MOVIE_ID, COLUMN_POSTER, COLUMN_TITLE},
                COLUMN_USER_ID + "=?",
                new String[]{userId},
                null,
                null,
                null
        )) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String movieId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MOVIE_ID));
                    String poster = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_POSTER));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));

                    Movie movie = new Movie(movieId, poster, title);
                    favoriteMovies.add(movie);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("SQLiteHelper", "Error al obtener películas favoritas para el usuario: " + userId, e);
        }

        Log.d("SQLiteHelper", "Número de películas favoritas obtenidas para el usuario " + userId + ": " + favoriteMovies.size());
        return favoriteMovies;
    }
}