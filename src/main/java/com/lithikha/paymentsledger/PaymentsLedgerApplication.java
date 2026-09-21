package com.lithikha.paymentsledger;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentsLedgerApplication {

	public static void main(String[] args) {
		// The Postgres JDBC driver sends the JVM's default zone at connect time. Windows JVMs report the
		// legacy id "Asia/Calcutta", which newer Postgres servers reject, so pin the JVM to UTC first.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(PaymentsLedgerApplication.class, args);
	}

}
