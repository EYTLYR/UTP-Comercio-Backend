package eytlyr.com.utp_comercio_backend.calls;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import eytlyr.com.utp_comercio_backend.auth.CurrentUser;

@RestController
@RequestMapping("/api/v1/calls")
public class VoiceCallController {
    record Start(@NotNull UUID conversationId, @NotBlank @Size(max=131072) String offer) { }
    record Answer(@NotBlank @Size(max=131072) String answer) { }
    private final VoiceCallService service;
    private final CurrentUser me;
    public VoiceCallController(VoiceCallService service, CurrentUser me) { this.service=service; this.me=me; }
    @GetMapping("/config") public Map<String,Object> config() { return service.configuration(me.id()); }
    @GetMapping public List<VoiceCallService.Call> incoming() { return service.incoming(me.id()); }
    @PostMapping public VoiceCallService.Call start(@Valid @RequestBody Start body) { return service.start(me.id(), body.conversationId(), body.offer()); }
    @GetMapping("/{id}") public VoiceCallService.Call get(@PathVariable UUID id) { return service.get(me.id(), id); }
    @PostMapping("/{id}/answer") public VoiceCallService.Call answer(@PathVariable UUID id, @Valid @RequestBody Answer body) { return service.answer(me.id(), id, body.answer()); }
    @PostMapping("/{id}/end") public void end(@PathVariable UUID id) { service.end(me.id(), id); }
}
