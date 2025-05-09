package com.tooling.toolforge.config;

import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
        basePackages = "com.tooling.toolforge",  // adjust package
        mongoTemplateRef = "usersDb" // name of the bean
)
public class MongoConfig {


    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${mongodb.users}")
    private String usersDb;

    @Bean(name = "usersDb")
    public MongoTemplate usersDb() {
        return new MongoTemplate(MongoClients.create(mongoUri), usersDb);
    }
}
