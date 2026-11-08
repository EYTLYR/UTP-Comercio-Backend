package eytlyr.com.utp_comercio_backend.municipality;

import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/**
 * Qué zonas administra cada quien:
 * <ul>
 * <li>ADMIN (plataforma): todas.</li>
 * <li>MUNICIPALITY: las zonas de su distrito, si su Municipalidad está aprobada.</li>
 * <li>DELEGATE: solo su propia zona.</li>
 * </ul>
 */
public final class Jurisdiction {

	private Jurisdiction() {
	}

	/** "San Juan de Lurigancho " → "san juan de lurigancho". */
	public static String key(String district) {
		return TextUtils.normalize(district).replaceAll("\\s+", " ");
	}

	/** Debe llamarse dentro de una transacción (municipalidad y zona son LAZY). */
	public static boolean covers(AppUser actor, Zone zone) {
		if (actor == null) {
			return false;
		}
		return switch (actor.getRole()) {
			case ADMIN -> true;
			case MUNICIPALITY -> zone != null && actor.getMunicipality() != null
					&& actor.getMunicipality().isApproved()
					&& actor.getMunicipality().getDistrictKey().equals(key(zone.getDistrict()));
			case DELEGATE -> zone != null && actor.getZone() != null && actor.getZone().getId().equals(zone.getId());
			default -> false;
		};
	}
}
