package com.bar.foo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class MySampleUnitTest {

	private static final Logger log = LoggerFactory.getLogger(MySampleUnitTest.class);

	@Test
	void sampleTest() {
		log.info("Running sample test");
	}

}
