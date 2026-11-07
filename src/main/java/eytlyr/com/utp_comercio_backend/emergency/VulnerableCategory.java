package eytlyr.com.utp_comercio_backend.emergency;

/** Categoría del tablero humanitario, con su nombre en español. */
public enum VulnerableCategory {

	ELDERLY_ALONE("Adulto mayor solo"),
	DISABILITY("Persona con discapacidad"),
	CHILDREN_ALONE("Niños solos"),
	INJURED("Herido"),
	PREGNANT("Gestante"),
	TRAPPED("Atrapado"),
	OXYGEN_DEPENDENT("Depende de oxígeno");

	private final String label;

	VulnerableCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
