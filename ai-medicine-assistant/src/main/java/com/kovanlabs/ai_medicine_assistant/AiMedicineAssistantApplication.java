package com.kovanlabs.ai_medicine_assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;

@SpringBootApplication(exclude = {OpenAiAutoConfiguration.class})
public class AiMedicineAssistantApplication {

	public static void main(String[] args)
	{
		SpringApplication.run(AiMedicineAssistantApplication.class, args);
	}
}
