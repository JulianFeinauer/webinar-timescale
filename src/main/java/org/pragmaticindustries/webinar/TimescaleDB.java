package org.pragmaticindustries.webinar;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * @author julian
 * Created by julian on 29.04.20
 */
public class TimescaleDB {

    public static void main(String[] args) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "password");
        Statement statement = connection.createStatement();

        // Create table
        try {
            statement.execute("CREATE TABLE flexible_series_2\n" +
                "(\n" +
                "    time         TIMESTAMPTZ NOT NULL,\n" +
                "    location     TEXT        NOT NULL,\n" +
                "    measurements JSONB       NOT NULL\n" +
                ");");
            // Make hypertable
            statement.execute("SELECT create_hypertable('flexible_series_2', 'time');");
        } catch (Exception e) {
            // do nothing...
        }

        final Instant start = LocalDate.of(2020, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        final Random random = new Random();
        for (int day = 0; day <= 10; day++) {
            for (int s = 0; s <= 86400; s++) {
                final long ts = start.plus(day, ChronoUnit.DAYS).plus(s, ChronoUnit.SECONDS).toEpochMilli()/1000;
                if (random.nextBoolean()) {
                    statement.addBatch(String.format("INSERT INTO flexible_series_2 (time, location, measurements) VALUES (to_timestamp(%d), 'office', '{\n  \"temperature\": %s\n}');", ts, 50.0 * random.nextGaussian()));
                } else {
                    statement.addBatch(String.format("INSERT INTO flexible_series_2 (time, location, measurements) VALUES (to_timestamp(%d), 'office', '{\n  \"humidity\": %s\n}');", ts, 80.0 * random.nextGaussian()));
                }
            }
            System.out.print(".");
            statement.executeBatch();
            statement.clearBatch();
        }
    }
}
