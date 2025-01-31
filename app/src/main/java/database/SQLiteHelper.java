package database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLiteHelper maneja la base de datos local de la aplicación.
 * Contiene dos tablas: 'users' y 'favorites'.
 */
public class SQLiteHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "app_database.db"; // Nombre de la base de datos
    private static final int DATABASE_VERSION = 1; // Versión de la base de datos

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
        db.setForeignKeyConstraintsEnabled(true); // 🔥 Habilitar claves foráneas
    }

    // -----------------------------------------------------------
    //                MÉTODOS PARA LA TABLA 'users'
    // -----------------------------------------------------------

    /**
     * Verifica si un usuario existe en la tabla 'users'.
     *
     * @param userId ID del usuario
     * @return True si existe, False en caso contrario
     */
    public boolean doesUserExist(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_USER_ID},
                    COLUMN_USER_ID + "=?",
                    new String[]{userId},
                    null,
                    null,
                    null
            );
            boolean exists = (cursor != null && cursor.moveToFirst());
            return exists;
        } catch (Exception e) {
            Log.e("SQLiteHelper", "Error al verificar existencia del usuario: " + userId, e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
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
     * Elimina un usuario de la tabla 'users' por su user_id.
     *
     * @param userId ID del usuario
     * @return Número de filas eliminadas
     */
    public int deleteUser(String userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_USERS, COLUMN_USER_ID + "=?", new String[]{userId});
        Log.d("SQLiteHelper", "Número de usuarios eliminados: " + rowsDeleted);
        return rowsDeleted;
    }

    // -----------------------------------------------------------
    //              MÉTODOS PARA LA TABLA 'favorites'
    // -----------------------------------------------------------

    /**
     * Verifica si una película está en los favoritos de un usuario.
     *
     * @param userId  ID del usuario
     * @param movieId ID de la película
     * @return True si ya está en favoritos, False en caso contrario
     */
    public boolean isMovieFavorite(String userId, String movieId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_FAVORITES,
                    new String[]{COLUMN_MOVIE_ID},
                    COLUMN_USER_ID + "=? AND " + COLUMN_MOVIE_ID + "=?",
                    new String[]{userId, movieId},
                    null,
                    null,
                    null
            );

            boolean exists = (cursor != null && cursor.moveToFirst());
            return exists;
        } catch (Exception e) {
            Log.e("SQLiteHelper", "Error al verificar si la película es favorita: " + movieId + " para el usuario: " + userId, e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Agrega una película a los favoritos de un usuario.
     *
     * @param userId  ID del usuario
     * @param movieId ID de la película
     * @param poster  URL del póster de la película
     * @param title   Título de la película
     * @return True si la inserción fue exitosa, False en caso contrario
     */
    public boolean addMovieToFavorites(String userId, String movieId, String poster, String title) {
        if (!doesUserExist(userId)) {
            Log.e("SQLiteHelper", "No se puede agregar a favoritos: El usuario " + userId + " no existe.");
            return false;
        }

        if (isMovieFavorite(userId, movieId)) {
            Log.i("SQLiteHelper", "La película " + movieId + " ya es favorita para el usuario " + userId);
            return false;
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
            return false;
        } else {
            Log.d("SQLiteHelper", "Película agregada a favoritos: " + movieId + " para el usuario " + userId);
            return true;
        }
    }

    /**
     * Elimina una película de los favoritos de un usuario.
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
