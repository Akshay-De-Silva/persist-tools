DROP TABLE IF EXISTS MultipleAssociations;
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Dept;
DROP TABLE IF EXISTS Customer;
DROP TABLE IF EXISTS Profiles;

CREATE TABLE Profiles (
	id INT NOT NULL AUTO_INCREMENT,
	name VARCHAR(191) NOT NULL,
	isAdult BOOLEAN NOT NULL,
	salary FLOAT NOT NULL,
	age DECIMAL NOT NULL,
	PRIMARY KEY(id)
)  AUTO_INCREMENT = 10;

CREATE TABLE Customer (
	id INT NOT NULL,
	name VARCHAR(191) NOT NULL,
	PRIMARY KEY(id)
);

CREATE TABLE Dept (
	id INT NOT NULL,
	name VARCHAR(191) NOT NULL,
	PRIMARY KEY(id)
);

CREATE TABLE Users (
	id INT NOT NULL,
	name VARCHAR(191) NOT NULL,
	profileId INT UNIQUE,
	CONSTRAINT FK_USERS_PROFILES_0 FOREIGN KEY(profileId) REFERENCES Profiles(id),
	PRIMARY KEY(id)
);

CREATE TABLE MultipleAssociations (
	id INT NOT NULL,
	name VARCHAR(191) NOT NULL,
	userId INT UNIQUE,
	CONSTRAINT FK_MULTIPLEASSOCIATIONS_USERS_0 FOREIGN KEY(userId) REFERENCES Users(id),
	profileId INT UNIQUE,
	CONSTRAINT FK_MULTIPLEASSOCIATIONS_DEPT_0 FOREIGN KEY(profileId) REFERENCES Dept(id) ON DELETE SET DEFAULT,
	customerId INT UNIQUE,
	CONSTRAINT FK_MULTIPLEASSOCIATIONS_CUSTOMER_0 FOREIGN KEY(customerId) REFERENCES Customer(id) ON UPDATE SET DEFAULT,
	PRIMARY KEY(id)
);
