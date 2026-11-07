package eytlyr.com.utp_comercio_backend.emergency;

/** Recurso crítico en emergencia, con su nombre en español. */
public enum ResourceType {

	WATER("Agua"),
	FOOD("Alimentos"),
	MEDICINE("Medicinas"),
	ENERGY("Energía y carga"),
	SHELTER("Refugio"),
	TRANSPORT("Transporte");

	private final String label;

	ResourceType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
