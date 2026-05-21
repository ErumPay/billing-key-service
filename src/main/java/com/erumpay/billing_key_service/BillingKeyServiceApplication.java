package com.erumpay.billing_key_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class BillingKeyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingKeyServiceApplication.class, args);
	}

}
