package org.projectk.controller;

import org.projectk.dto.ServiceOneResponse;
import org.projectk.dto.ServiceTwoResponse;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final ServiceOneClient oneClient;
    private final ServiceTwoClient twoClient;

    public DemoController(ServiceOneClient oneClient,
                          ServiceTwoClient twoClient) {
        this.oneClient = oneClient;
        this.twoClient = twoClient;
    }

    @GetMapping("/one/{id}")
    public ServiceOneResponse getOne(@PathVariable String id) {
        return oneClient.fetchData(id);
    }

    @GetMapping("/two/{id}")
    public ServiceTwoResponse getTwo(@PathVariable String id) {
        return twoClient.fetchData(id);
    }
}