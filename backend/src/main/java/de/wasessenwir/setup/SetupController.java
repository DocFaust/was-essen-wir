package de.wasessenwir.setup;

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

    public SetupController(SetupService setup) {
        this.setup = setup;
    }

    @GetMapping("/status")
    SetupStatusResponse status(HttpServletRequest request) {
        request.getSession(true);
        return new SetupStatusResponse(setup.isRequired());
    }

    @PostMapping
    ResponseEntity<UserResponse> complete(@RequestBody SetupRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(setup.complete(request, httpRequest.getRemoteAddr()));
    }
}
