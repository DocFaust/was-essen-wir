package de.wasessenwir.user;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    List<UserResponse> list() {
        return users.list();
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request, UriComponentsBuilder uriBuilder) {
        UserResponse created = users.create(request);
        return ResponseEntity.created(uriBuilder.path("/api/users/{id}").build(created.id())).body(created);
    }

    @PutMapping("/{id}")
    UserResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PostMapping("/{id}/password")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    UserResponse resetPassword(@PathVariable UUID id, @RequestBody PasswordResetRequest request) {
        return users.resetPassword(id, request);
    }
}
