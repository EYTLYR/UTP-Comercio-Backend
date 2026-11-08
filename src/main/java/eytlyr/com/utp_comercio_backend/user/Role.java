package eytlyr.com.utp_comercio_backend.user;

/**
 * Rol del usuario.
 * <ul>
 * <li>NEIGHBOR: vecino.</li>
 * <li>BRIGADIST: brigadista aprobado por su Municipalidad.</li>
 * <li>DELEGATE: delegado de una zona.</li>
 * <li>MUNICIPALITY: equipo de una Municipalidad aprobada; administra las zonas de su distrito.</li>
 * <li>ADMIN: administrador de la plataforma Nearby; aprueba Municipalidades.</li>
 * </ul>
 */
public enum Role {

	NEIGHBOR, BRIGADIST, DELEGATE, MUNICIPALITY, ADMIN

}
