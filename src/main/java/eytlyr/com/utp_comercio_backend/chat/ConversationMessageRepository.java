package eytlyr.com.utp_comercio_backend.chat;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

}
