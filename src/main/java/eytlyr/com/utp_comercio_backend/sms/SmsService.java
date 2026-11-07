package eytlyr.com.utp_comercio_backend.sms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.UnauthorizedException;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.emergency.Priority;
import eytlyr.com.utp_comercio_backend.emergency.ReportSource;
import eytlyr.com.utp_comercio_backend.emergency.ReportStatus;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReport;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReportRepository;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.sms.SosCodec.Decoded;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.DecodeResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.EncodeRequest;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.EncodeResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.InboundResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.InboundSms;
import eytlyr.com.utp_comercio_backend.sos.SosType;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.user.Role;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;
import eytlyr.com.utp_comercio_backend.zone.StreetCodeRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/** Códigos SOS comprimidos: codificar, decodificar y recibir por SMS cuando no hay internet. */
@Service
public class SmsService {

	public static final String SMS_CODE_INVALID = "SMS_CODE_INVALID";

	public static final String SMS_CODE_TOO_LONG = "SMS_CODE_TOO_LONG";

	private static final Logger log = LoggerFactory.getLogger(SmsService.class);

	private final SosCodec codec;

	private final StreetCodeRepository streets;

	private final ZoneRepository zones;

	private final AppUserRepository users;

	private final VulnerableReportRepository reports;

	private final ApplicationEventPublisher events;

	private final String inboundToken;

	public SmsService(SosCodec codec, StreetCodeRepository streets, ZoneRepository zones, AppUserRepository users,
			VulnerableReportRepository reports, ApplicationEventPublisher events, NearbyProperties props) {
		this.codec = codec;
		this.streets = streets;
		this.zones = zones;
		this.users = users;
		this.reports = reports;
		this.events = events;
		this.inboundToken = props.sms().inboundToken();
	}

	@Transactional(readOnly = true)
	public EncodeResult encode(UUID userId, EncodeRequest req) {
		List<StreetCode> zoneStreets = streetsFor(userId);
		char letter = req.category() != null ? SosCodec.letter(req.category()) : SosCodec.letter(req.sosType());
		String location = codec.locationFromAddress(zoneStreets, req.addressText())
				.orElseGet(() -> {
					if (req.lat() == null || req.lng() == null) {
						throw new BusinessRuleException(SMS_CODE_INVALID,
								"Indica una dirección conocida (por ejemplo, Jr. Los Pinos 340) o la ubicación.");
					}
					return codec.nearestLocation(zoneStreets, req.lat(), req.lng());
				});
		boolean high = req.priority() == null || req.priority() == Priority.HIGH;
		try {
			String code = codec.encode(letter, req.people() != null ? req.people() : 1, location, high);
			return new EncodeResult(code, code.length());
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessRuleException(SMS_CODE_TOO_LONG, "El código supera los 25 caracteres de un SMS corto.");
		}
	}

	@Transactional(readOnly = true)
	public DecodeResult decode(UUID userId, String code) {
		Decoded d = codec.decode(code, streetsFor(userId)).orElseThrow(SmsService::invalid);
		return toResult(d);
	}

	/**
	 * Webhook de la pasarela SMS. Con token válido responde 200 siempre; si el texto trae un código,
	 * crea un reporte (source SMS) y avisa a la brigada.
	 */
	@Transactional
	public InboundResult inbound(String token, InboundSms sms) {
		if (inboundToken == null || inboundToken.isBlank() || token == null
				|| !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), inboundToken.getBytes(StandardCharsets.UTF_8))) {
			throw new UnauthorizedException("SMS_TOKEN_INVALID", "Token del webhook inválido.");
		}
		Matcher m = SosCodec.CODE_IN_TEXT.matcher(sms.body() == null ? "" : sms.body().toUpperCase());
		if (!m.find()) {
			return new InboundResult(true, false, null, null);
		}
		String phone = normalizePhone(sms.from());
		AppUser sender = phone != null ? users.findByPhone(phone).orElse(null) : null;
		Zone zone = sender != null && sender.getZone() != null ? sender.getZone()
				: zones.findAll().stream().findFirst().orElse(null);
		if (zone == null) {
			return new InboundResult(true, false, null, null);
		}
		var decoded = codec.decode(m.group(), streets.findByZoneId(zone.getId()));
		if (decoded.isEmpty()) {
			return new InboundResult(true, false, null, null);
		}
		Decoded d = decoded.get();
		VulnerableCategory category = d.kind().category() != null ? d.kind().category()
				: d.kind().sosType() == SosType.EARTHQUAKE ? VulnerableCategory.TRAPPED : VulnerableCategory.INJURED;
		VulnerableReport r = reports.save(VulnerableReport.builder()
				.zone(zone)
				.reporter(sender)
				.category(category)
				.detail("SMS sin internet (" + d.code() + "): " + d.people() + " · " + d.kind().label()
						+ (sender != null ? " · enviado por " + sender.getName() : " · desde " + sms.from()))
				.priority(d.high() ? Priority.HIGH : Priority.MEDIUM)
				.status(ReportStatus.PENDING)
				.source(ReportSource.SMS)
				.addressText(d.addressText())
				.lat(d.lat() != null ? d.lat() : zone.getCenterLat())
				.lng(d.lng() != null ? d.lng() : zone.getCenterLng())
				.build());
		Dispatch.Builder push = Dispatch.builder();
		users.findByZoneIdAndRole(zone.getId(), Role.BRIGADIST).forEach(b -> push.push(b.getId(),
				"SOS por SMS: " + d.kind().label(), d.addressText(),
				Map.of("type", "vulnerable_report", "reportId", r.getId().toString())));
		events.publishEvent(push.build());
		log.warn("SOS por SMS recibido: {} → reporte {}", d.code(), r.getId());
		return new InboundResult(true, true, r.getId(), toResult(d));
	}

	private List<StreetCode> streetsFor(UUID userId) {
		AppUser u = users.findById(userId).orElse(null);
		Zone zone = u != null && u.getZone() != null ? u.getZone() : zones.findAll().stream().findFirst().orElse(null);
		return zone == null ? List.of() : streets.findByZoneId(zone.getId());
	}

	private static DecodeResult toResult(Decoded d) {
		String type = d.kind().category() != null ? d.kind().category().name() : d.kind().sosType().name();
		return new DecodeResult(d.code(), type, d.kind().label(), d.people(), d.addressText(), d.lat(), d.lng(),
				d.high() ? "HIGH" : "MEDIUM");
	}

	/** "+51 987 654 321" → "987654321". */
	static String normalizePhone(String from) {
		if (from == null) {
			return null;
		}
		String digits = from.replaceAll("\\D", "");
		return digits.length() >= 9 ? digits.substring(digits.length() - 9) : null;
	}

	private static BusinessRuleException invalid() {
		return new BusinessRuleException(SMS_CODE_INVALID, "El código SOS no es válido.");
	}
}
