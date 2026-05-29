package com.myagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.myagent.mapper")
public class MySpringAiAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MySpringAiAgentApplication.class, args);
    }
}
