# boiler-service

[![CI](https://github.com/smart-home-automation-system/boiler-service/actions/workflows/CI.yml/badge.svg)](https://github.com/smart-home-automation-system/boiler-service/actions/workflows/CI.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)

![GitHub Release Date - Published_At](https://img.shields.io/github/release-date/smart-home-automation-system/boiler-service?style=plastic)
![GitHub Release](https://img.shields.io/github/v/release/smart-home-automation-system/boiler-service?style=plastic)

---

![GitHub top language](https://img.shields.io/github/languages/top/smart-home-automation-system/boiler-service?style=plastic)
![Java](https://img.shields.io/badge/java-17-yellow?style=plastic)
![SpringBoot](https://img.shields.io/badge/SpringBoot-4.0.1-blue?style=plastic)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)

![GitHub issues](https://img.shields.io/github/issues/smart-home-automation-system/boiler-service?style=plastic)
![GitHub contributors](https://img.shields.io/github/contributors/smart-home-automation-system/boiler-service?style=plastic)
![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/smart-home-automation-system/boiler-service?style=plastic)

![GitHub last commit](https://img.shields.io/github/last-commit/smart-home-automation-system/boiler-service?style=plastic)
![GitHub commit activity](https://img.shields.io/github/commit-activity/m/smart-home-automation-system/boiler-service?style=plastic)

# Description

The main purpose of this service is handling devices in the boiler room.
Service control furnace which is responsible for heating water and warming the rooms.
This service is querying the water-service for hot water status and heating-service for rooms status.

Furnace can be turned on if one of the pumps is working (hot water or heating).
Furnace can be turned off if all pumps are off.
Hot water pump has priority over heating pump.
While there is a necessity to heat up, a water heating pump should be turned off.
Heating pump may be turned on if there is a need to warm up the room. Current status is provided by heating-service.
