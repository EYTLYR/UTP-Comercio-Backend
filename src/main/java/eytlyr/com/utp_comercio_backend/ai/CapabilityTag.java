package eytlyr.com.utp_comercio_backend.ai;

/** Capacidad del inventario de supervivencia (Protocolo Lázaro), con su nombre en español y su tipo. */
public enum CapabilityTag {

	HEIGHT_RESCUE("Rescate en altura", CapabilityKind.RESOURCE),
	RESCUE_TOOLS("Herramientas de rescate", CapabilityKind.RESOURCE),
	BACKUP_POWER("Energía de respaldo", CapabilityKind.RESOURCE),
	WATER_RESERVE("Reserva de agua", CapabilityKind.RESOURCE),
	EVAC_TRANSPORT("Transporte de evacuación", CapabilityKind.RESOURCE),
	PARAMEDIC("Soporte paramédico", CapabilityKind.SKILL),
	TECHNICIAN("Brigadista técnico", CapabilityKind.SKILL),
	ELDERLY_AT_HOME("Adulto mayor en casa", CapabilityKind.VULNERABILITY),
	REDUCED_MOBILITY("Movilidad reducida", CapabilityKind.VULNERABILITY),
	OXYGEN_DEPENDENT("Depende de oxígeno", CapabilityKind.VULNERABILITY),
	SANDBAGS("Sacos de arena", CapabilityKind.RESOURCE),
	PUMP("Motobomba", CapabilityKind.RESOURCE),
	ROOF_COVER("Calaminas y plásticos", CapabilityKind.RESOURCE),
	SHOVELS("Palas y picos", CapabilityKind.RESOURCE);

	private final String label;

	private final CapabilityKind kind;

	CapabilityTag(String label, CapabilityKind kind) {
		this.label = label;
		this.kind = kind;
	}

	public String label() {
		return label;
	}

	public CapabilityKind kind() {
		return kind;
	}
}
