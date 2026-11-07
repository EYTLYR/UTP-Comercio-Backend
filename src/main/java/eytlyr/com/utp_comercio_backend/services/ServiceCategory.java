package eytlyr.com.utp_comercio_backend.services;

/** Oficio del directorio, con su nombre en español. */
public enum ServiceCategory {

	PLUMBING("Gasfitería"),
	ELECTRICITY("Electricidad"),
	CARPENTRY("Carpintería"),
	LOCKSMITH("Cerrajería"),
	TUTORING("Clases y tutorías"),
	PET_CARE("Cuidado de mascotas"),
	SEWING("Costura"),
	OTHER("Otros");

	private final String label;

	ServiceCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
