package eytlyr.com.utp_comercio_backend.posts;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostResponseRepository extends JpaRepository<PostResponse, UUID> {

}
