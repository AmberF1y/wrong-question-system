package com.wrongquestion.backend.system.health;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {


    @Autowired
    private MockMvc mockMvc;


    @Test
    void healthShouldReturnOk() throws Exception {

        mockMvc.perform(
                get("/api/health")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

    }

}