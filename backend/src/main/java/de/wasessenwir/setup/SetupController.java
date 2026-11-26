package de.wasessenwir.setup;

import de.wasessenwir.security.ClientIpResolver;
import de.wasessenwir.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setup;
    private final ClientIpResolver clientIpResolver;

    public SetupController(SetupService setup, ClientIpResolver clientIpResolver) {
        this.setup = setup;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/status")
    SetupStatusResponse status() {
        return new SetupStatusResponse(setup.isRequired());
    }

    @PostMapping
    ResponseEntity<UserResponse> complete(@RequestBody SetupRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(setup.complete(request, clientIpResolver.resolve(httpRequest)));
    }
}
