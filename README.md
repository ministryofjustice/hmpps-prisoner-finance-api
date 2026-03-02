# hmpps-prisoner-finance-api

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-prisoner-finance-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-prisoner-finance-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-finance-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-finance-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

## Pre-requisites

To be able to run this repo locally you will need the following software installed

- Docker + Docker Compose
    - The easiest way to do this is to install Docker Desktop which comes bundled with both
- Java Development Kit (JDK) 21
    - The JDK version must match the gradle expectations exactly
- Gradle
    - Running the application will usually download and install this for you
- IntelliJ Idea
    - The ultimate version requires a license from MoJ but licenses are limited but you can use the Community Edition (CE) without issue

    
# Instructions

## Running the application locally

The application comes with a `dev` spring profile that includes default settings for running locally. This is not
necessary when deploying to kubernetes as these values are included in the helm configuration templates -
e.g. `values-dev.yaml`.

There is also a `docker-compose.yml` that can be used to run a local instance of the template in docker and also an
instance of HMPPS Auth (required if your service calls out to other services using a token).

### Building and running the docker image locally

The `Dockerfile` relies on the application being built first. Steps to build the docker image:
1. Build the jar files
```bash
./gradlew clean assemble
```
2. Copy the jar files to the base directory so that the docker build can find them
```bash
cp build/libs/*.jar .
```
3. Build the docker image with required arguments
```bash
docker build --build-arg GIT_REF=21345 --build-arg GIT_BRANCH=bob --build-arg BUILD_NUMBER=$(date '+%Y-%m-%d') .
```
4. Run the docker image, setting the auth url so that it starts up
```bash
docker run -e HMPPS_AUTH_URL="https://sign-in-dev.hmpps.service.justice.gov.uk/auth" <sha from step 3>
```

## Running the application locally

The application comes with a `local` spring profile that includes default settings for running locally.

There is also a `docker-compose.yml` that can be used to run a local instance in docker and also an
instance of HMPPS Auth.

```bash
make serve
```

will run the application and HMPPS Auth within a local docker instance.

To verify the app has started,
1. ensure the containers are visible (and running) in Docker, and
2. visit http://localhost:8080/health ensuring the result contains "status: UP"

### Running the application in Intellij

```bash
make serve-environment
```

will just start a docker instance of HMPPS Auth. The application should then be started with
a `dev` active profile in Intellij.

### Generating an auth token
- Use this command to request a local auth token:
  ```bash
  curl -X POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n hmpps-prisoner-finance-general-ledger-api:clientsecret | base64)"
  ```

- The response body will contain an access token something like this:

  ```json
  {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5...BAtWD653XpCzn8A",
    "token_type": "bearer",
    "expires_in": 3599,
    "scope": "read write",
    "sub": "hmpps-prisoner-finance-general-ledger"        
  }
  ```
- Use the value of `access_token` as a Bearer Token to authenticate when calling the local API endpoints.

## Generating API Clients & Models

We use OpenAPI Generator to automatically generate the Kotlin client and data models for the General Ledger API.

The configuration is in build.gradle.kts under apiSpecs. This creates two tasks:

`writeGeneralledgerJson`: Downloads the latest API specification from the Dev environment to openapi-specs/generalledger.json.

`buildGeneralledgerApiClient`: Generates the Kotlin data classes (Models) and WebClient interfaces (API) from the local JSON file.

### How to Update
If the General Ledger API changes:

Download the latest spec:

```sh
./gradlew writeGeneralledgerJson
```

Verify & Regenerate: Check the diff in openapi-specs/generalledger.json and run a build to ensure the code compiles.

```sh
./gradlew clean build
```
Commit: Commit the updated .json file. Do not commit the generated code in build/.
