package de.wasessenwir.status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

    static final String UP = "UP";

    private final String applicationName;

    public StatusService(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    public StatusResponse currentStatus() {
        return new StatusResponse(UP, applicationName);
    }
}
