package it.pagopa.ecommerce.payment.methods.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import it.pagopa.generated.ecommerce.gec.v1.ApiClient;
import it.pagopa.generated.ecommerce.gec.v1.api.CalculatorApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientsConfig implements WebFluxConfigurer {
    @Value("${afm.client.maxInMemory}")
    private int maxMemorySize;

    @Bean(name = "afmWebClient")
    public CalculatorApi afmWebClient(
                                      @Value("${afm.uri}") String afmWebClientUri,
                                      @Value(
                                          "${afm.readTimeout}"
                                      ) int afmWebClientReadTimeout,
                                      @Value(
                                          "${afm.connectionTimeout}"
                                      ) int afmWebClientConnectionTimeout
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, afmWebClientConnectionTimeout)
                .doOnConnected(
                        connection -> connection.addHandlerLast(
                                new ReadTimeoutHandler(
                                        afmWebClientReadTimeout,
                                        TimeUnit.MILLISECONDS
                                )
                        )
                );

        WebClient webClient = ApiClient.buildWebClientBuilder().exchangeStrategies(
                ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxMemorySize))
                        .build()
        ).clientConnector(
                new ReactorClientHttpConnector(httpClient)
        ).baseUrl(afmWebClientUri).build();

        return new CalculatorApi(new ApiClient(webClient));
    }

    // TODO: replace with v2 CalculatorApi
    @Bean(name = "afmWebClientV2")
    public CalculatorApi afmWebClientV2(
                                        @Value("${afm.v2.uri}") String afmWebClientUri,
                                        @Value(
                                            "${afm.readTimeout}"
                                        ) int afmWebClientReadTimeout,
                                        @Value(
                                            "${afm.connectionTimeout}"
                                        ) int afmWebClientConnectionTimeout
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, afmWebClientConnectionTimeout)
                .doOnConnected(
                        connection -> connection.addHandlerLast(
                                new ReadTimeoutHandler(
                                        afmWebClientReadTimeout,
                                        TimeUnit.MILLISECONDS
                                )
                        )
                );

        WebClient webClient = ApiClient.buildWebClientBuilder().exchangeStrategies(
                ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxMemorySize))
                        .build()
        ).clientConnector(
                new ReactorClientHttpConnector(httpClient)
        ).baseUrl(afmWebClientUri).build();

        return new CalculatorApi(new ApiClient(webClient));
    }
}
