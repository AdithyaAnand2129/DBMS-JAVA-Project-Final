-- RoomWise: timetable and booking schema
-- Run through RoomWise automatically, or execute in MySQL Workbench.

CREATE DATABASE IF NOT EXISTS roomwise_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE roomwise_db;

CREATE TABLE IF NOT EXISTS rooms (
    id   INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room VARCHAR(40) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS timetable_slots (
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
    INDEX idx_slots_day_room_time(day_of_week, room_id, start_time, end_time),
    CONSTRAINT chk_slot_times CHECK (end_time > start_time),
    CONSTRAINT fk_slot_room_restrict FOREIGN KEY (room_id) REFERENCES rooms(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bookings (
    id           INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    teacher      VARCHAR(150) NOT NULL,
    room_id      INT          NOT NULL,
    booking_date DATE         NOT NULL,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    INDEX idx_bookings_date_room_time(booking_date, room_id, start_time, end_time),
    CONSTRAINT chk_booking_times CHECK (end_time > start_time),
    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES rooms(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;


-- Internal mutex for atomic room and teacher schedule checks.
CREATE TABLE IF NOT EXISTS schedule_guard (
    id TINYINT NOT NULL PRIMARY KEY,
    CONSTRAINT chk_single_guard CHECK (id = 1)
) ENGINE=InnoDB;
INSERT IGNORE INTO schedule_guard(id) VALUES (1);
