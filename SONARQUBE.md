# SonarQube Setup

Each service in this repository is an independent Maven project, so SonarQube is configured per service.

## What is configured

- `jacoco-maven-plugin` runs during `verify` and generates `target/site/jacoco/jacoco.xml`
- Sonar properties in each `pom.xml` point analysis to compiled classes, test classes, test reports, and the JaCoCo XML report
- `scripts/run-sonar.sh` runs analysis for one service or every service

## Prerequisites

- Docker Desktop or Docker Engine
- A valid SonarQube token
- Java 21 available locally

## Start SonarQube locally

```bash
cd /Users/shivamkumar/Desktop/loyaltyService
docker compose up -d sonarqube-db sonarqube
```

Then open `http://localhost:9000`.

Default login for a fresh local instance:

- username: `admin`
- password: `admin`

On first login, SonarQube will ask you to change the password.
After that, create a user token from:

- `My Account` -> `Security` -> `Generate Tokens`

## Scan one service

```bash
cd /Users/shivamkumar/Desktop/loyaltyService
SONAR_HOST_URL=http://localhost:9000 \
SONAR_TOKEN=your_token \
bash scripts/run-sonar.sh user-service
```

## Scan all services

```bash
cd /Users/shivamkumar/Desktop/loyaltyService
SONAR_HOST_URL=http://localhost:9000 \
SONAR_TOKEN=your_token \
bash scripts/run-sonar.sh all
```

## Optional project key prefix

By default project keys look like `loyaltyService:user-service`.
To change that:

```bash
cd /Users/shivamkumar/Desktop/loyaltyService
SONAR_HOST_URL=http://localhost:9000 \
SONAR_TOKEN=your_token \
SONAR_PROJECT_PREFIX=my-company-loyalty \
bash scripts/run-sonar.sh all
```
