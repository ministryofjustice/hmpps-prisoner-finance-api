build-dev: update-environment
	docker compose build

build:
	docker build -t hmpps-prisoner-finance-api .

update-dependencies:
	./gradlew useLatestVersions

analyse-dependencies:
	./gradlew dependencyCheckAnalyze --info

serve: build-dev
	docker compose up -d --wait

serve-environment:
	docker compose up --scale hmpps-prisoner-finance-api=0 -d --wait


serve-clean-environment: stop-clean
	docker compose up --scale hmpps-prisoner-finance-api=0 -d --wait

update-environment:
	docker compose pull

stop:
	docker compose down

stop-clean:
	docker compose down --remove-orphans --volumes

lint:
	./gradlew ktlintCheck

format:
	./gradlew ktlintFormat