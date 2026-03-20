.PHONY: db-start db-stop migrate jooq-generate dsl-generate build test run

db-start:
	docker compose up -d

db-stop:
	docker compose down

migrate:
	./gradlew flywayMigrate

jooq-generate:
	./gradlew generateJooq

dsl-generate:
	./gradlew generateDeclarativeJooqDsl

build:
	./gradlew build

test:
	./gradlew test

run:
	./gradlew bootRun
