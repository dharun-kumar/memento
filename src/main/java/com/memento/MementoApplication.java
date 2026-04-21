package com.memento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching // activates Spring's caching proxy so @Cacheable/@CacheEvict annotations work
public class MementoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MementoApplication.class, args);
	}

}
