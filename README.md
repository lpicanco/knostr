[![Kotlin](https://img.shields.io/badge/kotlin-1.8.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
![Build Status](https://img.shields.io/github/actions/workflow/status/lpicanco/knostr/jvm.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=lpicanco_knostr&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=lpicanco_knostr)
[![Sonar Coverage](https://img.shields.io/sonar/coverage/lpicanco_knostr?server=https%3A%2F%2Fsonarcloud.io)](https://sonarcloud.io/summary/new_code?id=lpicanco_knostr)
![GitHub License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat)

# Knostr

Nostr Relay Implementation
[wss://knostr.neutrine.com](wss://knostr.neutrine.com)

## Implemented NIPs
- [x] NIP-01
- [x] NIP-02
- [x] NIP-04
- [x] NIP-09
- [x] NIP-11
- [x] NIP-12
- [x] NIP-15
- [x] NIP-16 
- [x] NIP-20
- [x] NIP-28


## How to Run:

### Docker(self-build)
```bash
./gradlew jibDockerBuild
docker-compose up
```
