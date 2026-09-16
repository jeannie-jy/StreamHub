package com.streamhub.live;

import com.streamhub.common.api.TraceIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class FeignTraceConfig {
    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return requestTemplate -> {
            String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
            if (StringUtils.hasText(traceId)) {
                requestTemplate.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
            }
        };
    }
}
