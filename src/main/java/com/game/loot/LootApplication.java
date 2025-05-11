package com.game.loot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.game.loot.**"})
public class LootApplication {

	public static void main(String[] args) {
		SpringApplication.run(LootApplication.class, args);
	}

}
