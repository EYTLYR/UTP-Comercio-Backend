package eytlyr.com.utp_comercio_backend.prevention;

/** Tipo de reporte de riesgo (El Niño), con su nombre en español. */
public enum RiskCategory {

	ACEQUIA_OVERFLOW("Acequia desbordándose"),
	STREET_FLOODING("Calle acumulando agua"),
	SEEPAGE("Filtración"),
	RAVINE_ACTIVITY("Quebrada con lodo o piedras"),
	DRAIN_OVERFLOW("Desagüe rebalsando"),
	CRACK("Grieta en muro o cerro");

	private final String label;

	RiskCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
