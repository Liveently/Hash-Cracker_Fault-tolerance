package ru.kosolap.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Service
public class WorkerService {

    private final RestTemplate restTemplate = new RestTemplate();

    // Получение списка IP-адресов всех контейнеров "worker"
    public List<String> getWorkers() {
        List<String> addresses = new ArrayList<>();
        try {
            InetAddress[] machines = InetAddress.getAllByName("worker");
            for (InetAddress address : machines) {
                addresses.add(address.getHostAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return addresses;
    }

    public String checkHealth(String ip) {
        String workerPort = System.getenv("WORKER_PORT");  
        String workerUrl = "http://" + ip + ":" + workerPort + "/health";  

        int timeout = Integer.parseInt(System.getenv().getOrDefault("HEALTH_CHECK_TIMEOUT", "5000"));  // Получаем таймаут из окружения

        RestTemplate restTemplate = createRestTemplateWithTimeout(timeout);

        try {
            String response = restTemplate.getForObject(workerUrl, String.class);
            if ("Worker is healthy".equals(response)) {
                return "Healthy";
            } else {
                return "Unhealthy";
            }
        } catch (Exception e) {
            return "Not Responding";
        }
    }

    private RestTemplate createRestTemplateWithTimeout(int timeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setResponseTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        HttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }
}