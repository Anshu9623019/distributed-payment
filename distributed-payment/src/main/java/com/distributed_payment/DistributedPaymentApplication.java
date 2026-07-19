package com.distributed_payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling // required for OutboxPublisher's @Scheduled polling loop
public class DistributedPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedPaymentApplication.class, args);
    }
}
