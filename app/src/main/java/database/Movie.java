package database;

/**
 * Clase que representa una película en la tabla 'favorites'.
 */
public class Movie {
    private final String movie_id;  // ID único de la película
    private final String poster;    // URL de la carátula
    private final String title;     // Título de la película

    public Movie(String movie_id, String poster, String title) {
        this.movie_id = movie_id;
        this.poster = poster;
        this.title = title;
    }

    public String getMovie_id() {
        return movie_id;
    }

    public String getPoster() {
        return poster;
    }

    public String getTitle() {
        return title;
    }
}