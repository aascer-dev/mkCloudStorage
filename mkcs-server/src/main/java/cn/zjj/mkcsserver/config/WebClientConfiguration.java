package cn.zjj.mkcsserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Configuration
@Slf4j
public class WebClientConfiguration {

    // 建议将代理配置写在 application.yml 中，避免硬编码
    @Value("${proxy.enabled:true}")
    private boolean proxyEnabled;

    @Value("${proxy.host:localhost}")
    private String proxyHost;

    @Value("${proxy.port:7892}")
    private int proxyPort;

    @Bean
    public WebClient webClient() {
        // 创建httpClient
        HttpClient httpClient = HttpClient.create();

        // 只有开启代理时才配置
        if (proxyEnabled) {
            httpClient = httpClient.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort));
        }
        log.info("创建 WebClient，代理启用: {}, 代理地址: {}:{}", proxyEnabled, proxyHost, proxyPort);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}