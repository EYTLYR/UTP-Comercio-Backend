package eytlyr.com.utp_comercio_backend.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

	// Centro de la Zona 3 · Las Flores (SJL), ver ANEXO A.
	static final double LAT = -11.9790;
	static final double LNG = -77.0030;

	@Test
	void haversineOfSamePointIsZero() {
		assertThat(GeoUtils.haversineMeters(LAT, LNG, LAT, LNG)).isZero();
	}

	@Test
	void oneThousandthOfDegreeOfLatitudeIsAbout111Meters() {
		assertThat(GeoUtils.haversineMeters(LAT, LNG, LAT + 0.001, LNG)).isCloseTo(111.2, within(0.5));
	}

	@Test
	void limaToCallaoIsAboutTenKm() {
		// Plaza de Armas de Lima -> Plaza Grau del Callao.
		double d = GeoUtils.haversineMeters(-12.0464, -77.0300, -12.0566, -77.1181);
		assertThat(d).isBetween(9_500.0, 10_000.0);
	}

	@Test
	void boundingBoxContainsTheCircle() {
		GeoUtils.BoundingBox box = GeoUtils.boundingBox(LAT, LNG, 150);
		assertThat(box.contains(LAT, LNG)).isTrue();
		assertThat(GeoUtils.haversineMeters(LAT, LNG, box.maxLat(), LNG)).isCloseTo(150, within(1.0));
		assertThat(GeoUtils.haversineMeters(LAT, LNG, LAT, box.maxLng())).isCloseTo(150, within(1.0));
		assertThat(box.contains(LAT + 0.01, LNG)).isFalse();
	}

	@Test
	void approxRoundsToThreeDecimals() {
		assertThat(GeoUtils.approx(-11.979_432)).isEqualTo(-11.979);
		assertThat(GeoUtils.approx(-77.003_51)).isEqualTo(-77.004);
	}

	@Test
	void geofuzzAppliesOffsetBetween100And300Meters() {
		for (int i = 0; i < 50; i++) {
			double[] fuzzed = GeoUtils.geofuzz(LAT, LNG);
			double distance = GeoUtils.haversineMeters(LAT, LNG, fuzzed[0], fuzzed[1]);
			assertThat(distance).isBetween(99.0, 301.0);
			assertThat(fuzzed[0]).isNotEqualTo(LAT);
			assertThat(fuzzed[1]).isNotEqualTo(LNG);
		}
	}

	@Test
	void geofuzzNullSafeHandlesNulls() {
		Double[] fuzzed = GeoUtils.geofuzz((Double) null, null);
		assertThat(fuzzed[0]).isNull();
		assertThat(fuzzed[1]).isNull();
	}
}
