package com.example.demo;

import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Test
	void contextLoads() {
		long count = userRepository.count();
		System.out.println("--> Total Users in DB: " + count);
		Assertions.assertEquals(5, count, "Flyway should have seeded exactly 5 users");
	}
}
