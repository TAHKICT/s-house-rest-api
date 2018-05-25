package com.shouse.restapi.notifires;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import shouse.core.api.Notifier;
import shouse.core.node.response.Response;

public class RestWebApiNotifier implements Notifier{
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());


    private RestTemplate restTemplate;

    public RestWebApiNotifier(RestTemplate restTemplate){
        this.restTemplate = restTemplate;
    }

    @Override
    public void sendResponse(Response response) {
        LOGGER.info("Send response to web api: ".concat(response.toString()));
        restTemplate.postForEntity("http://localhost:8181/for-core-application/entry-point", response, Response.class);
    }

}
