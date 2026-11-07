package eytlyr.com.utp_comercio_backend.ai;

/** Capacidad del inventario de supervivencia (Protocolo Lázaro), con su nombre en español para la app. */
public enum CapabilityTag {

	HEIGHT_RESCUE("Rescate en altura"),
	RESCUE_TOOLS("Herramientas de rescate"),
	BACKUP_POWER("Energía de respaldo"),
	WATER_RESERVE("Reserva de agua"),
	EVAC_TRANSPORT("Transporte de evacuación"),
	PARAMEDIC("Soporte paramédico"),
	TECHNICIAN("Brigadista técnico"),
	ELDERLY_AT_HOME("Adulto mayor en casa"),
	REDUCED_MOBILITY("Movilidad reducida"),
	OXYGEN_DEPENDENT("Depende de oxígeno"),
	SANDBAGS("Sacos de arena"),
	PUMP("Motobomba"),
	ROOF_COVER("Calaminas y plásticos"),
	SHOVELS("Palas y picos");

	private final String label;

	CapabilityTag(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
