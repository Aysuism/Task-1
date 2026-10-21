package com.example.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// port 0 = any free port, so this test does not clash with a running storage-service on 9090
@SpringBootTest(properties = "grpc.server.port=0")
class StorageServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
