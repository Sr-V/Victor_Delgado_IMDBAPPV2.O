package database;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase encargada de sincronizar la lista de películas favoritas
 * entre la base de datos local (SQLite) y la nube (Firestore).
 * <p>
 * La estrategia de sincronización es la siguiente:
 *  - Al iniciar la aplicación (después del login en MainActivity), se
 *    invoca el método syncAtStartup():
 *      • Se consulta en Firestore si existe el documento de favoritos para
 *        el usuario actual.
 *      • Si el documento existe, se consulta la colección "movies":
 *          - Si hay datos en la nube pero no en la base de datos local, se
 *            insertan en SQLite.
 *          - Si hay datos en local pero la nube está vacía, se actualiza Firestore
 *            con los datos locales.
 *      • Si el documento no existe, se crea y se rellenan (si existen datos en local).
 * <p>
 *  - Al añadir o eliminar una película se invocan los métodos addMovieToFavorites()
 *    y removeMovieFromFavorites() que actualizan ambos lados (local y nube) dinámicamente.
 */
public class FavoritesSync {

    private static final String TAG = "FavoritesSync";
    private final FirebaseFirestore firestore;
    private final SQLiteHelper dbHelper;
    private final String userId; // ID del usuario actual

    /**
     * Constructor de la clase.
     *
     * @param context Contexto de la aplicación.
     * @param userId  ID del usuario autenticado.
     */
    public FavoritesSync(Context context, String userId) {
        this.firestore = FirebaseFirestore.getInstance();
        this.dbHelper = SQLiteHelper.getInstance(context);
        this.userId = userId;
    }

    /**
     * Sincroniza los datos de favoritos entre la base de datos local y la nube
     * al iniciar la aplicación.
     * <p>
     * Se realiza lo siguiente:
     *  - Se obtiene la lista de películas favoritas locales.
     *  - Se consulta en Firestore si existe el documento de favoritos para el usuario.
     *      • Si existe, se consulta la colección "movies":
     *            - Si la nube tiene datos y la base de datos local está vacía, se
     *              copian los datos de la nube a SQLite.
     *            - Si la nube está vacía y local tiene datos, se actualiza Firestore.
     *      • Si no existe, se crea el documento y se suben los datos locales (si existieran).
     */
    public void syncAtStartup() {
        // Referencia al documento de favoritos del usuario en Firestore
        DocumentReference userDocRef = firestore.collection("favorites").document(userId);
        // Obtener los favoritos almacenados localmente
        List<Movie> localFavorites = dbHelper.getFavoriteMovies(userId);

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    // El documento de favoritos existe en la nube
                    CollectionReference moviesCollection = userDocRef.collection("movies");
                    moviesCollection.get().addOnCompleteListener(taskMovies -> {
                        if (taskMovies.isSuccessful()) {
                            QuerySnapshot querySnapshot = taskMovies.getResult();
                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                // Existen datos en la nube
                                if (localFavorites.isEmpty()) {
                                    // Si no hay datos locales, se copian los datos de la nube a SQLite
                                    querySnapshot.getDocuments().forEach(doc -> {
                                        String movieId = doc.getString("movie_id");
                                        String poster = doc.getString("poster");
                                        String title = doc.getString("title");
                                        dbHelper.addMovieToFavorites(userId, movieId, poster, title);
                                    });
                                    Log.d(TAG, "Se sincronizaron los datos de la nube a la base de datos local.");
                                } else {
                                    // Opcionalmente, se pueden comparar ambas listas y hacer merge si es necesario.
                                    Log.d(TAG, "Los favoritos ya existen en ambos lados. Sincronización completada.");
                                }
                            } else {
                                // La colección "movies" en la nube está vacía
                                if (!localFavorites.isEmpty()) {
                                    // Si hay datos locales, se actualiza la nube
                                    for (Movie movie : localFavorites) {
                                        addMovieToCloud(movie);
                                    }
                                    Log.d(TAG, "La nube estaba vacía. Se subieron los datos locales a la nube.");
                                }
                            }
                        } else {
                            Log.e(TAG, "Error al obtener las películas de la nube", taskMovies.getException());
                        }
                    });
                } else {
                    // El documento de favoritos no existe en la nube: se crea y se suben datos locales si existen
                    userDocRef.set(new HashMap<>()).addOnCompleteListener(taskSet -> {
                        if (taskSet.isSuccessful()) {
                            Log.d(TAG, "Documento 'favorites' creado para el usuario: " + userId);
                            if (!localFavorites.isEmpty()) {
                                for (Movie movie : localFavorites) {
                                    addMovieToCloud(movie);
                                }
                                Log.d(TAG, "Se subieron los datos locales a la nube.");
                            }
                        } else {
                            Log.e(TAG, "Error al crear el documento 'favorites' para el usuario", taskSet.getException());
                        }
                    });
                }
            } else {
                Log.e(TAG, "Error al obtener el documento 'favorites'", task.getException());
            }
        });
    }

    /**
     * Añade una película a los favoritos, actualizando tanto la base de datos local
     * como la nube.
     *
     * @param movie Objeto Movie a añadir a favoritos.
     */
    public void addMovieToFavorites(Movie movie) {
        // Primero se añade localmente
        boolean localAdded = dbHelper.addMovieToFavorites(userId, movie.getMovie_id(), movie.getPoster(), movie.getTitle());
        if (localAdded) {
            // Si se añadió en SQLite, se actualiza la nube
            addMovieToCloud(movie);
        }
    }

    /**
     * Elimina una película de los favoritos, actualizando tanto la base de datos local
     * como la nube.
     *
     * @param movieId ID de la película a eliminar.
     */
    public void removeMovieFromFavorites(String movieId) {
        // Se elimina localmente
        int localRemoved = dbHelper.removeMovieFromFavorites(userId, movieId);
        if (localRemoved > 0) {
            // Si se eliminó de SQLite, se elimina de la nube
            removeMovieFromCloud(movieId);
        }
    }

    /**
     * Método auxiliar para añadir una película a la nube.
     * <p>
     * Se añade un documento en:
     * favorites/{userId}/movies/{movieId}
     *
     * @param movie Objeto Movie a añadir.
     */
    public void addMovieToCloud(Movie movie) {
        DocumentReference movieDocRef = firestore.collection("favorites")
                .document(userId)
                .collection("movies")
                .document(movie.getMovie_id());

        Map<String, Object> movieData = new HashMap<>();
        movieData.put("movie_id", movie.getMovie_id());
        movieData.put("poster", movie.getPoster());
        movieData.put("title", movie.getTitle());

        movieDocRef.set(movieData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Película añadida a la nube: " + movie.getMovie_id()))
                .addOnFailureListener(e -> Log.e(TAG, "Error al añadir la película a la nube: " + movie.getMovie_id(), e));
    }

    /**
     * Método auxiliar para eliminar una película de la nube.
     * <p>
     * Se elimina el documento:
     * favorites/{userId}/movies/{movieId}
     *
     * @param movieId ID de la película a eliminar.
     */
    public void removeMovieFromCloud(String movieId) {
        DocumentReference movieDocRef = firestore.collection("favorites")
                .document(userId)
                .collection("movies")
                .document(movieId);

        movieDocRef.delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Película eliminada de la nube: " + movieId))
                .addOnFailureListener(e -> Log.e(TAG, "Error al eliminar la película de la nube: " + movieId, e));
    }
}