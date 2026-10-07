package com.evops.config;

import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {
    @Bean
    public Realm localRealm() {
        SimpleAccountRealm realm = new SimpleAccountRealm();
        realm.addAccount("bootstrap", "bootstrap", "system");
        return realm;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        chain.addPathDefinition("/api/health", "anon");
        chain.addPathDefinition("/error", "anon");
        // 联调期先放行业务接口，登录体系接入后收紧
        chain.addPathDefinition("/api/turbines/**", "anon");
        chain.addPathDefinition("/api/inspection-orders/**", "anon");
        chain.addPathDefinition("/api/defects/**", "anon");
        chain.addPathDefinition("/api/defect-rules/**", "anon");
        chain.addPathDefinition("/api/annotations/**", "anon");
        chain.addPathDefinition("/api/routes/**", "anon");
        chain.addPathDefinition("/api/no-fly-zones/**", "anon");
        chain.addPathDefinition("/api/flight-tasks/**", "anon");
        chain.addPathDefinition("/api/route-changes/**", "anon");
        chain.addPathDefinition("/api/audit-events/**", "anon");
        chain.addPathDefinition("/api/weather-windows/**", "anon");
        chain.addPathDefinition("/api/maintenance-tasks/**", "anon");
        chain.addPathDefinition("/api/shifts/**", "anon");
        chain.addPathDefinition("/api/workers/**", "anon");
        chain.addPathDefinition("/api/equipment/**", "anon");
        chain.addPathDefinition("/api/spare-parts/**", "anon");
        chain.addPathDefinition("/api/part-rules/**", "anon");
        chain.addPathDefinition("/api/blades/**", "anon");
        chain.addPathDefinition("/api/icing/**", "anon");
        // 结冰预警页面与导出：联调期放行
        chain.addPathDefinition("/icing/**", "anon");
        chain.addPathDefinition("/**", "authc");
        return chain;
    }
}
