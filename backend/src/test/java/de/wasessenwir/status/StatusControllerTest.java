package de.wasessenwir.status;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.json.JsonCompareMode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @Test
    void statusReturnsOnlyPublicFields() throws Exception {
        when(statusService.currentStatus()).thenReturn(new StatusResponse("UP", "was-essen-wir"));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":"UP","application":"was-essen-wir"}
                        """, JsonCompareMode.STRICT));
    }
}
