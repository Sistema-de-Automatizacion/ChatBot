package com.chatbot.motosdelcaribe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MotosdelcaribeApplication {

	public static void main(String[] args) {
		SpringApplication.run(MotosdelcaribeApplication.class, args);
	}

}
