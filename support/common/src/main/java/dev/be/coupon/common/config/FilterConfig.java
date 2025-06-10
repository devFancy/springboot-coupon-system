package dev.be.coupon.common.config;

import dev.be.coupon.common.support.filter.v1.HttpRequestAndResponseLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<HttpRequestAndResponseLoggingFilter> loggingFilter() {
        FilterRegistrationBean<HttpRequestAndResponseLoggingFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new HttpRequestAndResponseLoggingFilter());
        registrationBean.addUrlPatterns("/api/*");
        // 요청/응답의 전체 과정을 빠짐없이 기록하기 위해, 필터 체인에서 가장 높은 우선순위를 부여합니다.
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }
}
