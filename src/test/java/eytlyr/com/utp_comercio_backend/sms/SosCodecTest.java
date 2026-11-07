package eytlyr.com.utp_comercio_backend.sms;

import java.util.List;

import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.zone.StreetCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ANEXO C: ida y vuelta para todos los tipos, manzanas, límite de 25 caracteres y códigos inválidos. */
class SosCodecTest {

	final SosCodec codec = new SosCodec();

	final List<StreetCode> streets = List.of(
			StreetCode.builder().code("LP").name("Jr. Los Pinos").lat(-11.9802).lng(-77.0015).build(),
			StreetCode.builder().code("LF").name("Jr. Las Flores").lat(-11.9790).lng(-77.0030).build(),
			StreetCode.builder().code("AP").name("Av. Principal").lat(-11.9775).lng(-76.9997).build());

	@Test
	void roundTripForEveryType() {
		for (char letter : SosCodec.KINDS.keySet()) {
			String code = codec.encode(letter, 2, "LP340", true);
			var d = codec.decode(code, streets).orElseThrow();
			assertThat(d.kind().letter()).isEqualTo(letter);
			assertThat(d.people()).isEqualTo(2);
			assertThat(d.addressText()).isEqualTo("Jr. Los Pinos 340");
			assertThat(d.high()).isTrue();
			assertThat(code.length()).isLessThanOrEqualTo(SosCodec.MAX_LENGTH);
		}
	}

	@Test
	void annexExamples() {
		assertThat(codec.decode("NB#SOS!H1@LP340$P:ALTA", streets).orElseThrow().kind().label()).isEqualTo("Herido");
		var block = codec.decode("NB#SOS!A2@MC12$P:ALTA", streets).orElseThrow();
		assertThat(block.addressText()).isEqualTo("Mz. C lote 12");
		assertThat(block.lat()).isNull();
		var oxygen = codec.decode("NB#SOS!O1@MF1$P:MEDIA", streets).orElseThrow();
		assertThat(oxygen.addressText()).isEqualTo("Mz. F lote 1");
		assertThat(oxygen.high()).isFalse();
	}

	@Test
	void addressesAndNearestStreet() {
		assertThat(codec.locationFromAddress(streets, "Jr. Los Pinos 340")).contains("LP340");
		assertThat(codec.locationFromAddress(streets, "Mz. C lote 12")).contains("MC12");
		assertThat(codec.nearestLocation(streets, -11.9790, -77.0030)).isEqualTo("LF0");
		assertThat(codec.decode("NB#SOS!X1@LF0$P:ALTA", streets).orElseThrow().addressText()).isEqualTo("Jr. Las Flores");
	}

	@Test
	void tooLongOrInvalidCodes() {
		assertThatThrownBy(() -> codec.encode('H', 1, "LP340000000", true)).isInstanceOf(IllegalArgumentException.class);
		assertThat(codec.decode("NB#SOS!H1@LP3400000000$P:ALTA", streets)).isEmpty();
		assertThat(codec.decode("NB#SOS!Z1@LP340$P:ALTA", streets)).isEmpty();
		assertThat(codec.decode("NB#SOS!H1@ZZ340$P:ALTA", streets)).isEmpty();
		assertThat(codec.decode("hola", streets)).isEmpty();
	}
}
