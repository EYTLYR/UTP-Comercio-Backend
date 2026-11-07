package eytlyr.com.utp_comercio_backend.posts;

/** Categoría de la cadena de favores, con su nombre en español. */
public enum PostCategory {

	TOOL_LOAN("Préstamo de herramientas"),
	TEMPORARY_HELP("Ayuda temporal"),
	MATERIAL_DONATION("Donación de materiales"),
	SKILL_EXCHANGE("Intercambio de habilidades"),
	SOLIDARITY_RIDE("Movilidad solidaria");

	private final String label;

	PostCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
