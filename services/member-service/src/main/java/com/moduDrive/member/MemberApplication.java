package com.moduDrive.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
		"com.moduDrive.member",
		"com.moduDrive.common.core",
        "com.moduDrive.common.infrastructure.jpa"
})
public class MemberApplication {
	public static void main(String[] args) {
		SpringApplication.run(MemberApplication.class, args);
	}
}
