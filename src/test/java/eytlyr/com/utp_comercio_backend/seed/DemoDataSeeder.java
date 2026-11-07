package eytlyr.com.utp_comercio_backend.seed;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.CapabilityIndexer;
import eytlyr.com.utp_comercio_backend.prevention.RiskClusteringService;
import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.Priority;
import eytlyr.com.utp_comercio_backend.emergency.ReportStatus;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListing;
import eytlyr.com.utp_comercio_backend.emergency.ResourceType;
import eytlyr.com.utp_comercio_backend.emergency.SafePoint;
import eytlyr.com.utp_comercio_backend.emergency.SafePointType;
import eytlyr.com.utp_comercio_backend.emergency.SupportGroup;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReport;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.posts.PostCategory;
import eytlyr.com.utp_comercio_backend.posts.PostStatus;
import eytlyr.com.utp_comercio_backend.posts.PostType;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.prevention.RiskCategory;
import eytlyr.com.utp_comercio_backend.prevention.RiskReport;
import eytlyr.com.utp_comercio_backend.prevention.RuleBasedPhotoRiskAnalyzer;
import eytlyr.com.utp_comercio_backend.services.NeighborService;
import eytlyr.com.utp_comercio_backend.services.Review;
import eytlyr.com.utp_comercio_backend.services.ServiceCategory;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.MedicalCard;
import eytlyr.com.utp_comercio_backend.user.Role;
import eytlyr.com.utp_comercio_backend.user.TrustedContact;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/**
 * Datos de la demo (ANEXO A de docs/BACKEND_PROMPTS.md): Zona 3 · Las Flores, San Juan de Lurigancho.
 * Solo en los perfiles dev y demo (prod,demo para la presentación). Es idempotente: si la zona ya existe no hace nada.
 */
/**
 * Solo pruebas (y ./mvnw spring-boot:test-run): carga los datos de ejemplo de la Zona 3. La app real
 * arranca vacía y solo con los datos base de BaseDataInitializer. Corre antes que BaseDataInitializer.
 */
@Component
@Profile({ "dev", "demo" })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoDataSeeder implements CommandLineRunner {

	public static final String ZONE_NAME = "Zona 3 · Las Flores";

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private static final String JUNTA = "Otros vecinos de la zona (registro de la junta vecinal)";

	private final ZoneRepository zones;

	private final EntityManager em;

	private final Clock clock;

	private final CapabilityIndexer indexer;

	private final RiskClusteringService radar;

	private final Map<String, AppUser> users = new HashMap<>();

	private Zone zone;

	private OffsetDateTime now;

	public DemoDataSeeder(ZoneRepository zones, EntityManager em, Clock clock, CapabilityIndexer indexer,
			RiskClusteringService radar) {
		this.zones = zones;
		this.em = em;
		this.clock = clock;
		this.indexer = indexer;
		this.radar = radar;
	}

	/** Cuadrícula del prototipo (x,y de 0 a 100, unos 12 m por unidad) → latitud. */
	public static double lat(double y) {
		return -11.9790 + (50 - y) * 0.000108;
	}

	/** Cuadrícula del prototipo → longitud. */
	public static double lng(double x) {
		return -77.0030 + (x - 50) * 0.000110;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (zones.existsByName(ZONE_NAME)) {
			log.info("Datos demo ya cargados ({}).", ZONE_NAME);
			return;
		}
		now = OffsetDateTime.now(clock);
		seedZone();
		seedUsers();
		seedServices();
		seedPosts();
		seedVulnerableReports();
		seedResources();
		seedGroupsAndSafePoints();
		seedRiskReports();
		seedMitigation();
		// Protocolo Lázaro: la IA aprende del historial cargado (con consentimiento de los vecinos demo).
		em.flush();
		int learned = indexer.reindexZone(zone.getId());
		log.info("Inventario de supervivencia: {} capacidades aprendidas.", learned);
		// Radar El Niño: Quebrada Canto Grande en rojo (3 reportes) y Jr. Los Pinos en ámbar (2 reportes).
		var zonesFound = radar.recompute(zone.getId()).zones();
		log.info("Radar preventivo: {} zonas de riesgo.", zonesFound.size());
		log.info("Datos demo cargados: {} con {} vecinos.", ZONE_NAME, users.size());
	}

	// ---------------------------------------------------------------- zona

	private void seedZone() {
		zone = save(Zone.builder()
				.name(ZONE_NAME)
				.district("San Juan de Lurigancho")
				.centerLat(lat(50))
				.centerLng(lng(50))
				.radiusM(1500)
				.mode(ZoneMode.NORMAL)
				.build());
		street("LP", "Jr. Los Pinos", 64, 61);
		street("LF", "Jr. Las Flores", 50, 50);
		street("LR", "Pje. Las Rosas", 40, 74);
		street("AP", "Av. Principal", 80, 36);
		street("QC", "Quebrada Canto Grande", 18, 20);
	}

	private void street(String code, String name, double x, double y) {
		save(StreetCode.builder().zone(zone).code(code).name(name).lat(lat(y)).lng(lng(x)).build());
	}

	// ---------------------------------------------------------------- usuarios

	private void seedUsers() {
		AppUser maria = user("María Quispe", "987654321", Role.NEIGHBOR, 50, 50);
		user("Brigada Z3 (Coordinador)", "999000111", Role.BRIGADIST, 16, 26);
		user("Municipalidad SJL", "999000000", Role.ADMIN, 50, 50);
		user("Julio Quispe", "911000001", Role.NEIGHBOR, 28, 30);
		user("Luis Mendoza", "911000002", Role.NEIGHBOR, 72, 24);
		user("Rosa Flores", "911000003", Role.NEIGHBOR, 60, 66);
		user("Pedro Ramos", "911000004", Role.NEIGHBOR, 28, 42);
		user("Miguel Ccori", "911000005", Role.NEIGHBOR, 16, 46);
		user("Carmen Huamán", "911000006", Role.NEIGHBOR, 55, 44);
		user("Ana Torres", "911000007", Role.NEIGHBOR, 48, 46);
		user("Elena Vargas", "911000008", Role.NEIGHBOR, 46, 56);
		user("Jorge Paredes", "911000009", Role.NEIGHBOR, 37, 30);
		user("Familia Ríos", "911000010", Role.NEIGHBOR, 76, 40);
		user("Lucía Mamani", "911000011", Role.NEIGHBOR, 44, 50);
		user("Óscar Díaz", "911000012", Role.NEIGHBOR, 20, 60);
		user("Carla Soto", "911000013", Role.NEIGHBOR, 54, 27);
		user("Familia Soto", "911000014", Role.NEIGHBOR, 57, 21);
		user("Taller Mendoza", "911000015", Role.NEIGHBOR, 61, 16);
		user("Teresa Rojas", "911000016", Role.NEIGHBOR, 34, 34);

		save(TrustedContact.builder().user(maria).name("Mamá").phone("987000120").position(1).build());
		save(TrustedContact.builder().user(maria).name("Carlos").phone("956000447").position(2).build());
		save(TrustedContact.builder().user(maria).name("Sra. Rosa").phone("944000908").position(3).build());
		save(MedicalCard.builder()
				.user(maria)
				.bloodType("O+")
				.allergies("Penicilina")
				.livesWith("ELDERLY")
				.build());
	}

	private AppUser user(String name, String phone, Role role, double x, double y) {
		AppUser u = save(AppUser.builder()
				.name(name)
				.phone(phone)
				.role(role)
				.verified(true)
				.aiConsent(true)
				.zone(zone)
				.lat(lat(y))
				.lng(lng(x))
				.build());
		users.put(name, u);
		return u;
	}

	private AppUser u(String name) {
		AppUser u = users.get(name);
		if (u == null) {
			throw new IllegalStateException("Usuario demo inexistente: " + name);
		}
		return u;
	}

	// ---------------------------------------------------------------- servicios

	private void seedServices() {
		NeighborService julio = service("Julio Quispe", "Gasfitería", ServiceCategory.PLUMBING, 48, "Desde S/ 35",
				"Lun–Sáb 7–19", true, null);
		service("Luis Mendoza", "Electricidad", ServiceCategory.ELECTRICITY, 41, "Desde S/ 40", null, true, null);
		NeighborService rosa = service("Rosa Flores", "Clases de matemática", ServiceCategory.TUTORING, 37,
				"S/ 20 la hora", null, false, null);
		service("Pedro Ramos", "Carpintería", ServiceCategory.CARPENTRY, 33, "Según el trabajo", null, true,
				"Carpintero con herramientas de palanca y gata hidráulica");
		service("Miguel Ccori", "Cerrajería", ServiceCategory.LOCKSMITH, 29, "Desde S/ 25", "24 h", true, null);
		service("Carmen Huamán", "Costura y arreglos", ServiceCategory.SEWING, 26, null, null, false, null);
		service("Ana Torres", "Paseo de mascotas", ServiceCategory.PET_CARE, 22, null, null, false, null);

		review(julio, "Elena Vargas", "Llegó a la hora, explicó todo y cobró lo justo.");
		review(julio, "Jorge Paredes", "Vecino de confianza. Volvió al día siguiente a revisar.");
		review(julio, "Teresa Rojas", "Me atendió un domingo por una urgencia.");
		review(rosa, "Teresa Rojas", "Gracias Rosa por inyectarme cuando me enfermé");
	}

	private NeighborService service(String owner, String title, ServiceCategory category, int endorsements,
			String price, String schedule, boolean emergency, String description) {
		AppUser o = u(owner);
		return save(NeighborService.builder()
				.owner(o)
				.title(title)
				.category(category)
				.description(description)
				.priceText(price)
				.scheduleText(schedule)
				.lat(o.getLat())
				.lng(o.getLng())
				.availableInEmergency(emergency)
				.endorsementCount(endorsements)
				.build());
	}

	private void review(NeighborService service, String author, String text) {
		save(Review.builder().service(service).author(u(author)).text(text).build());
	}

	// ---------------------------------------------------------------- muro e historial

	private void seedPosts() {
		// Recientes (abiertas).
		post("Elena Vargas", PostType.NEED, PostCategory.TOOL_LOAN,
				"Necesito un taladro por 2 horas para colgar una repisa", null, null, Duration.ofMinutes(10));
		post("Jorge Paredes", PostType.OFFER, PostCategory.SOLIDARITY_RIDE,
				"Voy al mercado en mi mototaxi a las 4 pm, tengo 2 asientos libres", null, null, Duration.ofMinutes(25));
		post("Familia Ríos", PostType.OFFER, PostCategory.MATERIAL_DONATION,
				"Regalo 10 ladrillos y medio saco de cemento", null, null, Duration.ofMinutes(45));
		post("Lucía Mamani", PostType.NEED, PostCategory.TEMPORARY_HELP,
				"¿Alguien puede acompañar a mi abuela 1 hora? No puede caminar sola", null, null, Duration.ofHours(1));
		post("Óscar Díaz", PostType.OFFER, PostCategory.SKILL_EXCHANGE,
				"Enseño a usar el celular a cambio de ayuda con mi jardín", null, null, Duration.ofHours(2));
		post("Carla Soto", PostType.NEED, PostCategory.SOLIDARITY_RIDE,
				"Necesito que lleven a mi papá a su cita médica, usa silla de ruedas", null, null, Duration.ofHours(3));

		// Historial más antiguo (alimenta el inventario de la IA en la FASE 9).
		history("Jorge Paredes", PostType.OFFER, PostCategory.TOOL_LOAN,
				"Presto escalera grande de 6 metros y taladro", 37, 30, 21);
		history("Taller Mendoza", PostType.OFFER, PostCategory.TOOL_LOAN,
				"Presto mi generador eléctrico para la fiesta del barrio", 61, 16, 60);
		history("Familia Soto", PostType.NEED, PostCategory.TEMPORARY_HELP,
				"Busco quien me ayude a cargar el balón de oxígeno de mi papá", 57, 21, 35);
		history("Óscar Díaz", PostType.OFFER, PostCategory.MATERIAL_DONATION,
				"Regalo botiquín completo; soy técnico en enfermería jubilado", 20, 60, 7);
		history("Familia Ríos", PostType.OFFER, PostCategory.MATERIAL_DONATION,
				"Tenemos tanque de agua de 1100 litros, pueden sacar si se corta", 76, 40, 1);
		history("Familia Ríos", PostType.OFFER, PostCategory.TOOL_LOAN,
				"Guardamos 20 sacos de arena y una motobomba del último invierno; si alguien necesita, avisen", 72, 52, 120);
		history("Pedro Ramos", PostType.OFFER, PostCategory.MATERIAL_DONATION,
				"Me sobraron calaminas y plástico grueso de la obra, los regalo", 28, 42, 60);
		history("Óscar Díaz", PostType.OFFER, PostCategory.TOOL_LOAN,
				"Presto palas y picos para limpiar la acequia", 20, 60, 21);
		history("María Quispe", PostType.OFFER, PostCategory.SKILL_EXCHANGE,
				"Ofrezco ayuda con primeros auxilios, hice el curso de la brigada", 50, 50, 60);
	}

	private void history(String author, PostType type, PostCategory category, String text, double x, double y,
			int daysAgo) {
		Post p = post(author, type, category, text, x, y, Duration.ofDays(daysAgo));
		p.setStatus(PostStatus.DONE);
	}

	private Post post(String author, PostType type, PostCategory category, String text, Double x, Double y,
			Duration ago) {
		AppUser a = u(author);
		Post p = Post.builder()
				.author(a)
				.zone(zone)
				.type(type)
				.category(category)
				.text(text)
				.lat(y != null ? lat(y) : a.getLat())
				.lng(x != null ? lng(x) : a.getLng())
				.build();
		return saveAt(p, ago);
	}

	// ---------------------------------------------------------------- tablero humanitario

	private void seedVulnerableReports() {
		vulnerable(VulnerableCategory.ELDERLY_ALONE, "Mz. C lote 12",
				"La Sra. Teresa (82) vive sola en un 2do piso y no responde. La escalera de su casa se cayó.",
				Priority.HIGH, ReportStatus.PENDING, null, null, 34, 34);
		vulnerable(VulnerableCategory.INJURED, "Jr. Los Pinos 340", "Corte en el brazo por vidrios. Está consciente.",
				Priority.HIGH, ReportStatus.ON_THE_WAY, "Brigada Z3 (Coordinador)", "Brigada Z3", 66, 62);
		vulnerable(VulnerableCategory.DISABILITY, "Mz. F lote 3", "Usa silla de ruedas y vive en un 2do piso.",
				Priority.MEDIUM, ReportStatus.ON_THE_WAY, "Luis Mendoza", "Luis M.", 55, 28);
		vulnerable(VulnerableCategory.CHILDREN_ALONE, "Pje. Las Rosas 18", "Dos niños solos; la mamá trabaja en el centro.",
				Priority.HIGH, ReportStatus.ATTENDED, "Rosa Flores", "Rosa F.", 40, 72);
	}

	private void vulnerable(VulnerableCategory category, String address, String detail, Priority priority,
			ReportStatus status, String assignedTo, String assignedLabel, double x, double y) {
		save(VulnerableReport.builder()
				.zone(zone)
				.category(category)
				.addressText(address)
				.detail(detail)
				.priority(priority)
				.status(status)
				.assignedTo(assignedTo != null ? u(assignedTo) : null)
				.assignedLabel(assignedLabel)
				.lat(lat(y))
				.lng(lng(x))
				.build());
	}

	// ---------------------------------------------------------------- recursos críticos (emergencia)

	private void seedResources() {
		// Resumen objetivo tengo/necesito: WATER 14/9, FOOD 8/12, MEDICINE 5/7, ENERGY 6/15, SHELTER 3/4, TRANSPORT 4/2.
		emergencyListing("Familia Ríos", ResourceType.WATER, ListingKind.HAVE, "20 litros de agua potable", 1, null);
		emergencyListing("Carla Soto", ResourceType.WATER, ListingKind.NEED, "Agua para 4 personas (1 bebé)", 1, null);
		emergencyListing("Taller Mendoza", ResourceType.ENERGY, ListingKind.HAVE, "Generador: carga tu celular de 4 a 6 pm",
				1, null);
		emergencyListing("Municipalidad SJL", ResourceType.SHELTER, ListingKind.HAVE, "Local comunal abierto · 60 cupos", 1,
				"Junta vecinal · Local comunal");

		emergencyTotals(ResourceType.WATER, 14 - 1, 9 - 1);
		emergencyTotals(ResourceType.FOOD, 8, 12);
		emergencyTotals(ResourceType.MEDICINE, 5, 7);
		emergencyTotals(ResourceType.ENERGY, 6 - 1, 15);
		emergencyTotals(ResourceType.SHELTER, 3 - 1, 4);
		emergencyTotals(ResourceType.TRANSPORT, 4, 2);
	}

	private void emergencyListing(String owner, ResourceType type, ListingKind kind, String text, int quantity,
			String location) {
		AppUser o = u(owner);
		save(ResourceListing.builder()
				.user(o)
				.zone(zone)
				.resourceType(type)
				.kind(kind)
				.text(text)
				.quantity(quantity)
				.locationText(location)
				.lat(o.getLat())
				.lng(o.getLng())
				.build());
	}

	/** Completa el resumen del anexo con un listado agregado de la junta vecinal. */
	private void emergencyTotals(ResourceType type, int have, int need) {
		if (have > 0) {
			emergencyListing("Municipalidad SJL", type, ListingKind.HAVE, JUNTA, have, null);
		}
		if (need > 0) {
			emergencyListing("Municipalidad SJL", type, ListingKind.NEED, JUNTA, need, null);
		}
	}

	// ---------------------------------------------------------------- grupos y puntos seguros

	private void seedGroupsAndSafePoints() {
		group("Brigada de primeros auxilios Z3", "medical_services", 12, true, null, "999000111");
		group("Electricistas de guardia", "bolt", 4, true, null, null);
		group("Evaluación de viviendas", "home_work", 5, true, null, null);
		group("Olla común Las Flores", "soup_kitchen", 20, true, "Comida caliente 12:00 y 18:00", null);
		group("Apoyo psicológico", "psychology", 6, false, "Desde las 14:00", null);

		save(SafePoint.builder().zone(zone).name("Punto de reunión · Parque Las Flores").type(SafePointType.MEETING_POINT)
				.lat(lat(52)).lng(lng(24)).build());
		save(SafePoint.builder().zone(zone).name("Refugio · Local comunal").type(SafePointType.SHELTER)
				.capacity(60).occupied(22).lat(lat(76)).lng(lng(80)).build());
	}

	private void group(String name, String icon, int members, boolean activeNow, String schedule, String phone) {
		save(SupportGroup.builder()
				.zone(zone)
				.name(name)
				.icon(icon)
				.membersCount(members)
				.activeNow(activeNow)
				.scheduleText(schedule)
				.contactPhone(phone)
				.build());
	}

	// ---------------------------------------------------------------- radar El Niño

	private void seedRiskReports() {
		risk("Carla Soto", RiskCategory.SEEPAGE, "Jr. Los Pinos 318", "Está saliendo agua del suelo de mi patio", true,
				62, 60, Duration.ofMinutes(40));
		risk("Jorge Paredes", RiskCategory.ACEQUIA_OVERFLOW, "Jr. Los Pinos 352",
				"La acequia está al ras, el agua ya pasa la vereda", true, 67, 64, Duration.ofHours(1));
		risk("Lucía Mamani", RiskCategory.RAVINE_ACTIVITY, "Quebrada Canto Grande (parte alta)",
				"Baja lodo con piedras pequeñas por la quebrada", true, 16, 16, Duration.ofMinutes(25));
		risk("Óscar Díaz", RiskCategory.CRACK, "Quebrada Canto Grande (parte alta)",
				"Se abrió una grieta en el muro de contención", true, 22, 20, Duration.ofHours(1));
		risk("Teresa Rojas", RiskCategory.STREET_FLOODING, "Quebrada Canto Grande (parte baja)",
				"El agua ya entra a las casas de la parte baja", false, 19, 25, Duration.ofMinutes(50));
		risk("Elena Vargas", RiskCategory.STREET_FLOODING, "Av. Principal 410",
				"Se forma una laguna en la esquina del paradero", true, 82, 36, Duration.ofHours(2));
		risk("Jorge Paredes", RiskCategory.DRAIN_OVERFLOW, "Pje. Las Rosas 12", "El buzón está botando agua", false, 40,
				76, Duration.ofHours(3));
	}

	private void risk(String reporter, RiskCategory category, String address, String text, boolean photo, double x,
			double y, Duration ago) {
		RiskReport r = RiskReport.builder()
				.zone(zone)
				.reporter(u(reporter))
				.category(category)
				.addressText(address)
				.text(text)
				.lat(lat(y))
				.lng(lng(x))
				.build();
		if (photo) {
			r.setPhotoUrl("/demo/risk/" + category.name().toLowerCase() + ".jpg");
			r.setPhotoAnalysis(RuleBasedPhotoRiskAnalyzer.summary(category));
			r.setSeverityHint(RuleBasedPhotoRiskAnalyzer.severity(category));
		}
		saveAt(r, ago);
	}

	// ---------------------------------------------------------------- mitigación (El Niño)

	private void seedMitigation() {
		// Resumen objetivo tengo/necesito: SANDBAGS 40/120, PUMP 2/5, ROOF_COVER 15/30, SHOVELS 9/6, PLANKS 6/10,
		// FLASHLIGHTS 12/20.
		mitigation("Familia Ríos", MitigationType.SANDBAGS, ListingKind.HAVE, "20 sacos de arena y una motobomba guardados",
				20, "Cochera Mz. B", 72, 52);
		mitigation("Carla Soto", MitigationType.SANDBAGS, ListingKind.NEED, "Sacos para la puerta: ya entra agua por la vereda",
				10, null, null, null);
		mitigation("Pedro Ramos", MitigationType.ROOF_COVER, ListingKind.HAVE, "Calaminas y plástico grueso que sobraron de la obra",
				8, null, null, null);
		mitigation("Lucía Mamani", MitigationType.ROOF_COVER, ListingKind.NEED, "Plástico para cubrir mi techo de estera",
				1, null, null, null);
		mitigation("Óscar Díaz", MitigationType.SHOVELS, ListingKind.HAVE, "Palas y picos para limpiar la acequia",
				4, null, null, null);

		mitigationTotals(MitigationType.SANDBAGS, 40 - 20, 120 - 10);
		mitigationTotals(MitigationType.PUMP, 2, 5);
		mitigationTotals(MitigationType.ROOF_COVER, 15 - 8, 30 - 1);
		mitigationTotals(MitigationType.SHOVELS, 9 - 4, 6);
		mitigationTotals(MitigationType.PLANKS, 6, 10);
		mitigationTotals(MitigationType.FLASHLIGHTS, 12, 20);
	}

	private void mitigation(String owner, MitigationType type, ListingKind kind, String text, int quantity,
			String location, Integer x, Integer y) {
		AppUser o = u(owner);
		save(ResourceListing.builder()
				.user(o)
				.zone(zone)
				.mitigationType(type)
				.kind(kind)
				.text(text)
				.quantity(quantity)
				.locationText(location)
				.lat(y != null ? lat(y) : o.getLat())
				.lng(x != null ? lng(x) : o.getLng())
				.build());
	}

	private void mitigationTotals(MitigationType type, int have, int need) {
		if (have > 0) {
			mitigation("Municipalidad SJL", type, ListingKind.HAVE, JUNTA, have, null, null, null);
		}
		if (need > 0) {
			mitigation("Municipalidad SJL", type, ListingKind.NEED, JUNTA, need, null, null, null);
		}
	}

	// ---------------------------------------------------------------- utilidades

	private <T extends BaseEntity> T save(T entity) {
		em.persist(entity);
		return entity;
	}

	private <T extends BaseEntity> T saveAt(T entity, Duration ago) {
		entity.setCreatedAt(now.minus(ago));
		return save(entity);
	}
}
