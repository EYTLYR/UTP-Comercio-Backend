package eytlyr.com.utp_comercio_backend;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * PostgreSQL temporal para pruebas sin Docker, con los binarios de una instalación local que
 * tenga PostGIS (por ejemplo el instalador de EDB + Stack Builder en Windows, o Homebrew/apt).
 * Crea un clúster nuevo en una carpeta temporal (initdb, usuario postgres sin contraseña),
 * lo arranca en un puerto libre y lo borra al cerrar.
 *
 * Se busca en NEARBY_PG_HOME (carpeta con bin/ y share/) o en las rutas habituales.
 */
public final class LocalPostgis implements AutoCloseable {

	private final Path home;

	private final Path dataDir;

	private final int port;

	private LocalPostgis(Path home, Path dataDir, int port) {
		this.home = home;
		this.dataDir = dataDir;
		this.port = port;
	}

	public String jdbcUrl() {
		return "jdbc:postgresql://localhost:" + port + "/postgres";
	}

	/** Instalación local de PostgreSQL con PostGIS, si hay. */
	public static Optional<Path> findHome() {
		List<Path> candidates = new ArrayList<>();
		String env = System.getenv("NEARBY_PG_HOME");
		if (env != null && !env.isBlank()) {
			candidates.add(Path.of(env));
		}
		for (String root : List.of("C:/Program Files/PostgreSQL", "/usr/lib/postgresql", "/opt/homebrew/opt",
				"/usr/local/opt", "/Applications/Postgres.app/Contents/Versions")) {
			Path r = Path.of(root);
			if (Files.isDirectory(r)) {
				try (Stream<Path> s = Files.list(r)) {
					// La versión más nueva primero.
					s.sorted(Comparator.reverseOrder()).forEach(candidates::add);
				}
				catch (IOException ignored) {
				}
			}
		}
		return candidates.stream().filter(LocalPostgis::hasPostgis).findFirst();
	}

	private static boolean hasPostgis(Path home) {
		if (!Files.isRegularFile(exe(home, "initdb"))) {
			return false;
		}
		String version = home.getFileName() == null ? "" : home.getFileName().toString();
		return Stream.of(home.resolve("share/extension"), home.resolve("share/postgresql/extension"),
				Path.of("/usr/share/postgresql", version, "extension"))
			.anyMatch(dir -> Files.isRegularFile(dir.resolve("postgis.control")));
	}

	private static Path exe(Path home, String name) {
		boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
		return home.resolve("bin").resolve(windows ? name + ".exe" : name);
	}

	public static LocalPostgis start(Path home) throws IOException {
		Path dataDir = Files.createTempDirectory("nearby-pg-");
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		run(exe(home, "initdb").toString(), "-D", dataDir.toString(), "-U", "postgres", "-A", "trust", "-E", "UTF8",
				"--locale=C", "--no-sync");
		run(exe(home, "pg_ctl").toString(), "-D", dataDir.toString(), "-l", dataDir.resolve("server.log").toString(),
				"-o", "-p " + port + " -c listen_addresses=localhost -c timezone=UTC -c fsync=off", "-w", "start");
		return new LocalPostgis(home, dataDir, port);
	}

	@Override
	public void close() {
		try {
			run(exe(home, "pg_ctl").toString(), "-D", dataDir.toString(), "-m", "immediate", "-w", "stop");
		}
		catch (IOException ignored) {
		}
		deleteQuietly(dataDir.toFile());
	}

	private static void run(String... cmd) throws IOException {
		// La salida va a un archivo y no a un pipe: pg_ctl start deja al servidor en segundo plano
		// heredando la salida, y leer el pipe hasta el final se quedaría esperando para siempre.
		Path out = Files.createTempFile("nearby-pg-cmd-", ".log");
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(out.toFile()).start();
			if (!p.waitFor(120, TimeUnit.SECONDS) || p.exitValue() != 0) {
				p.destroyForcibly();
				throw new IOException("Falló " + String.join(" ", cmd) + ":\n" + new String(Files.readAllBytes(out)));
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
		finally {
			try {
				Files.deleteIfExists(out);
			}
			catch (IOException stillOpen) {
				// En Windows el servidor puede seguir con el archivo abierto.
				out.toFile().deleteOnExit();
			}
		}
	}

	private static void deleteQuietly(File f) {
		File[] children = f.listFiles();
		if (children != null) {
			for (File c : children) {
				deleteQuietly(c);
			}
		}
		f.delete();
	}
}
