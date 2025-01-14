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
 * Clase que gestiona las operaciones de la base de datos SQLite para almacenar películas favoritas.
 */
public class SQLiteHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1; // Versión de la base de datos
    private static final String TABLE_NAME = "favoritos"; // Nombre de la tabla
    private static final String COLUMN_ID = "id"; // Columna para almacenar el ID de la película
    private static final String COLUMN_CARATULA = "caratula"; // Columna para almacenar la URL de la carátula
    private static final String COLUMN_TITULO = "titulo"; // Columna para almacenar el título de la película

    /**
     * Constructor de SQLiteHelper.
     *
     * @param context Contexto de la aplicación.
     * @param userId  ID del usuario (se usa para nombrar la base de datos).
     */
    public SQLiteHelper(Context context, String userId) {
        super(context, userId + "_favoritos.db", null, DATABASE_VERSION);
    }

    /**
     * Crea la tabla de favoritos en la base de datos.
     *
     * @param db Instancia de la base de datos.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" +
                COLUMN_ID + " TEXT PRIMARY KEY," +
                COLUMN_CARATULA + " TEXT," +
                COLUMN_TITULO + " TEXT" + ")";
        db.execSQL(CREATE_TABLE);
    }

    /**
     * Actualiza la base de datos cuando se cambia la versión.
     *
     * @param db         Instancia de la base de datos.
     * @param oldVersion Versión antigua.
     * @param newVersion Versión nueva.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    /**
     * Verifica si una película está en la lista de favoritos.
     *
     * @param titulo Título de la película.
     * @return true si la película está en favoritos, false en caso contrario.
     */
    public boolean isMovieFavorite(String titulo) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_TITULO}, COLUMN_TITULO + "=?",
                new String[]{titulo}, null, null, null);

        boolean exists = cursor.getCount() > 0; // Verifica si hay resultados
        cursor.close();
        return exists;
    }

    /**
     * Agrega una película a la lista de favoritos.
     *
     * @param id       ID único de la película.
     * @param caratula URL de la carátula de la película.
     * @param titulo   Título de la película.
     */
    public void addMovieToFavorites(String id, String caratula, String titulo) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Verifica si la película ya existe en favoritos
        Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_TITULO}, COLUMN_TITULO + "=?",
                new String[]{titulo}, null, null, null);

        if (cursor.getCount() > 0) {
            cursor.close();
            Log.i("SQLiteHelper", "La película ya existe en favoritos: " + titulo);
            return; // No se agrega si ya existe
        }
        cursor.close();

        // Inserta la película en la base de datos
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, id);
        values.put(COLUMN_CARATULA, caratula);
        values.put(COLUMN_TITULO, titulo);

        db.insert(TABLE_NAME, null, values);
        Log.i("SQLiteHelper", "Película agregada a favoritos: " + titulo);
    }

    /**
     * Elimina una película de la lista de favoritos.
     *
     * @param titulo Título de la película a eliminar.
     */
    public void removeMovieFromFavorites(String titulo) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_TITULO + "=?", new String[]{titulo});
        Log.i("SQLiteHelper", "Película eliminada de favoritos: " + titulo);
    }

    /**
     * Obtiene todas las películas de la lista de favoritos.
     *
     * @return Lista de películas favoritas.
     */
    public List<Movie> getFavoriteMovies() {
        List<Movie> movies = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_ID, COLUMN_CARATULA, COLUMN_TITULO},
                null, null, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String id = cursor.getString(cursor.getColumnIndex(COLUMN_ID));
                @SuppressLint("Range") String caratula = cursor.getString(cursor.getColumnIndex(COLUMN_CARATULA));
                @SuppressLint("Range") String titulo = cursor.getString(cursor.getColumnIndex(COLUMN_TITULO));
                movies.add(new Movie(id, caratula, titulo)); // Agrega la película a la lista
            }
            cursor.close();
        }
        Log.d("SQLiteHelper", "Cargando películas desde la base de datos. Total encontradas: " + movies.size());
        return movies;
    }
}