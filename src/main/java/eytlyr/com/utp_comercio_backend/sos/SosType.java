package eytlyr.com.utp_comercio_backend.sos;

/** Tipo de SOS (opcional). */
public enum SosType {

    HEALTH("Salud"),
    FIRE("Incendio"),
    ROBBERY("Robo"),
    EARTHQUAKE("Sismo"),
    VIOLENCE("Violencia"),
    OTHER("Otra emergencia");

    private final String label;

    SosType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

}
