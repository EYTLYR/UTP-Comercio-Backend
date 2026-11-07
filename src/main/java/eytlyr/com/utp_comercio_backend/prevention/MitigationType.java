package eytlyr.com.utp_comercio_backend.prevention;

/** Recurso de mitigación preventiva (El Niño), con su nombre en español. */
public enum MitigationType {

	SANDBAGS("Sacos de arena"),
	PUMP("Motobombas"),
	ROOF_COVER("Calaminas y plásticos"),
	SHOVELS("Palas y picos"),
	PLANKS("Tablones"),
	FLASHLIGHTS("Linternas");

	private final String label;

	MitigationType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
