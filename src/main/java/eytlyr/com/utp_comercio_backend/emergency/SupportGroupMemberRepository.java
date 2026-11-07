package eytlyr.com.utp_comercio_backend.emergency;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportGroupMemberRepository extends JpaRepository<SupportGroupMember, UUID> {

	boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

	Optional<SupportGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

	@Query("select m.group.id from SupportGroupMember m where m.user.id = :userId")
	Set<UUID> groupIdsOf(@Param("userId") UUID userId);

}
