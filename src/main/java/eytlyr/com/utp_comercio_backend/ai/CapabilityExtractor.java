package eytlyr.com.utp_comercio_backend.ai;

import java.util.List;

/** IA de texto: detecta capacidades (recursos, habilidades, vulnerabilidades) en lo que escribe un vecino. */
public interface CapabilityExtractor {

	record ExtractedCapability(CapabilityTag tag, double confidence, String evidence) {
	}

	List<ExtractedCapability> extract(String text, SourceType source);
}
