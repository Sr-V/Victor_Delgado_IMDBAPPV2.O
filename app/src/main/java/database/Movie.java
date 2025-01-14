package database;

/**
 * Clase que representa una película almacenada en la base de datos.
 */
public class Movie {
    private final String id;       // ID único de la película (puede ser de IMDB o TMDB)
    private final String caratula; // URL de la carátula de la película
    private final String titulo;   // Título de la película

    /**
     * Constructor para crear una instancia de la película.
     *
     * @param id       ID único de la película.
     * @param caratula URL de la carátula de la película.
     * @param titulo   Título de la película.
     */
    public Movie(String id, String caratula, String titulo) {
        this.id = id;
        this.caratula = caratula;
        this.titulo = titulo;
    }

    /**
     * Obtiene el ID de la película.
     *
     * @return ID único de la película.
     */
    public String getId() {
        return id;
    }

    /**
     * Obtiene la URL de la carátula de la película.
     *
     * @return URL de la carátula de la película.
     */
    public String getCaratula() {
        return caratula;
    }

    /**
     * Obtiene el título de la película.
     *
     * @return Título de la película.
     */
    public String getTitulo() {
        return titulo;
    }
}