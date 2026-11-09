-- RoomWise: timetable and booking schema
-- Run through RoomWise automatically, or execute in MySQL Workbench.

CREATE DATABASE IF NOT EXISTS roomwise_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE roomwise_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS timetable_slots;
DROP TABLE IF EXISTS rooms;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE rooms (
    id   INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE timetable_slots (
    id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id     INT          NOT NULL,
    day_of_week ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY',
                     'FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    course_code VARCHAR(40)  NOT NULL,
    course_name VARCHAR(180) NOT NULL,
    section     VARCHAR(180) NOT NULL,
    teacher     VARCHAR(150) NOT NULL,
    class_type  ENUM('THEORY','LAB') NOT NULL,
    CONSTRAINT chk_slot_times CHECK (end_time > start_time),
    CONSTRAINT fk_slot_room FOREIGN KEY (room_id) REFERENCES rooms(id)
        ON DELETE CASCADE
);

CREATE TABLE bookings (
    id           INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    teacher      VARCHAR(150) NOT NULL,
    room_id      INT          NOT NULL,
    booking_date DATE         NOT NULL,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    CONSTRAINT chk_booking_times CHECK (end_time > start_time),
    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES rooms(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_slots_day_room_time
    ON timetable_slots(day_of_week, room_id, start_time, end_time);
CREATE INDEX idx_bookings_date_room_time
    ON bookings(booking_date, room_id, start_time, end_time);
