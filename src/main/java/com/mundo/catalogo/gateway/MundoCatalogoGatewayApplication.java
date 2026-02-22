package com.mundo.catalogo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MundoCatalogoGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MundoCatalogoGatewayApplication.class, args);
	}

}
