package com.zjj.mkcs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@MapperScan("com.zjj.mkcs.server.mapper")
public class MkcsApplication {

	public static void main(String[] args) {
		SpringApplication.run(MkcsApplication.class, args);
	}

}
