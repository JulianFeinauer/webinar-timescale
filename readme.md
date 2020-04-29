# TimescaleDB

* Go to `https://docs.timescale.com/v1.2/getting-started/installation/docker/installation-docker`
* Execute `docker run -d --name timescaledb -p 5432:5432 -e POSTGRES_PASSWORD=password timescale/timescaledb:latest-pg11`
* Open IDE

# Then, fun
```{sql}
-- Create Table
CREATE TABLE conditions
(
    time        TIMESTAMPTZ      NOT NULL,
    location    TEXT             NOT NULL,
    temperature DOUBLE PRECISION NULL,
    humidity    DOUBLE PRECISION NULL
);

-- Make Hypertable
SELECT create_hypertable('conditions', 'time');

-- Add some data
INSERT INTO conditions(time, location, temperature, humidity)
VALUES (NOW(), 'office', 70.0, 50.0);
INSERT INTO conditions(time, location, temperature, humidity)
VALUES (NOW(), 'office 2', 30.0, 50.0);
INSERT INTO conditions(time, location, temperature, humidity)
VALUES (NOW(), 'office 3', 60.0, 50.0);

SELECT *
FROM conditions
ORDER BY time DESC
LIMIT 100;

-- Subsampling Query
SELECT time_bucket('15 minutes', time) AS fifteen_min,
       location,
       COUNT(*),
       MAX(temperature)                AS max_temp,
       MAX(humidity)                   AS max_hum
FROM conditions
WHERE time > NOW() - interval '3 hours'
GROUP BY fifteen_min, location
ORDER BY fifteen_min DESC, max_temp DESC;


-- Now, the power of Postgres!
CREATE TABLE locations
(
    location TEXT NOT NULL,
    country  TEXT NOT NULL
);

INSERT INTO locations (location, country)
VALUES ('office', 'germany');
INSERT INTO locations (location, country)
VALUES ('office 2', 'norway');
INSERT INTO locations (location, country)
VALUES ('office 3', 'germany');

-- Now...
SELECT time_bucket('15 minutes', time) AS fifteen_min,
       locations.country,
       COUNT(*),
       MAX(temperature)                AS max_temp,
       MAX(humidity)                   AS max_hum
FROM conditions,
     locations
WHERE time > NOW() - interval '3 hours'
  AND locations.location = conditions.location
GROUP BY fifteen_min, locations.country
ORDER BY fifteen_min DESC, max_temp DESC;

-- PART 2 JSONB
CREATE TABLE flexible_series
(
    time         TIMESTAMPTZ NOT NULL,
    location     TEXT        NOT NULL,
    measurements JSONB       NOT NULL
);

SELECT create_hypertable('flexible_series', 'time');

INSERT INTO flexible_series (time, location, measurements)
VALUES (NOW(), 'office', '{}');
INSERT INTO flexible_series (time, location, measurements)
VALUES (NOW(), 'office', '{
  "temperature": 47
}');
INSERT INTO flexible_series (time, location, measurements)
VALUES (NOW(), 'office', '{
  "humidity": 80
}');
INSERT INTO flexible_series (time, location, measurements)
VALUES (NOW(), 'office', '{
  "humidity": 80,
  "temperature": 37.5
}');

SELECT *
FROM flexible_series;

SELECT measurements -> 'temperature'
FROM flexible_series;

SELECT measurements -> 'temperature'
FROM flexible_series
WHERE measurements ? 'temperature';

-- Which keys do I have
SELECT DISTINCT jsonb_object_keys(measurements)
FROM flexible_series;

-- Keys and Amounts
SELECT jsonb_object_keys(measurements) AS key, COUNT(*)
FROM flexible_series
GROUP BY key

-- Back to TimescaleDB
SELECT time_bucket('1 minute', time) AS bucket,
       AVG((measurements ->> 'temperature')::numeric)
FROM flexible_series
WHERE measurements ? 'temperature'
GROUP BY bucket

-- What is reported when??
SELECT time_bucket('1 second', time) AS bucket, key, COUNT(cnt)
FROM (
         SELECT time, jsonb_object_keys(measurements) AS key, COUNT(*) AS cnt
         FROM flexible_series
         GROUP BY time, key) AS tmp
GROUP BY bucket, key


-- And what more?
-- Indexing!!!!!
EXPLAIN ANALYZE SELECT time_bucket('1 minute', time) AS bucket,
       AVG((measurements ->> 'temperature')::numeric)
FROM flexible_series
WHERE measurements ? 'temperature'
GROUP BY bucket;

CREATE INDEX idx1 ON flexible_series USING gin (measurements);

EXPLAIN ANALYZE SELECT time_bucket('1 minute', time) AS bucket,
       AVG((measurements ->> 'temperature')::numeric)
FROM flexible_series
WHERE measurements ? 'temperature'
GROUP BY bucket;
```
Grafana
```
docker run -d -p 3000:3000 --link timescaledb grafana/grafana:6.7.3
```
```
SELECT
  $__timeGroupAlias("time",$__interval),
  avg((measurements->>'temperature')::NUMERIC) AS "measurements"
FROM flexible_series_2
WHERE
  $__timeFilter("time")
GROUP BY 1
ORDER BY 1
```
Really advanced stuff
```

ALTER TABLE tbl ADD col NUMERIC;

CREATE TRIGGER extract_values_from_jsonb
BEFORE INSERT ON tbl
FOR EACH ROW
EXECUTE PROCEDURE extract_from_jsonb();

CREATE FUNCTION extract_from_jsonb()
RETURNS trigger AS '
BEGIN
  NEW.col := ((NEW.data->>''json_key'')::NUMERIC);
  RETURN NEW;
END' LANGUAGE 'plpgsql';

-- Insert all changes for existing values
UPDATE tbl SET col = ((NEW.data->>''json_key'')::NUMERIC);
```