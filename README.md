# boiler-service

---

[![CI](https://github.com/smart-home-automation-system/boiler-service/actions/workflows/CI.yml/badge.svg)](https://github.com/smart-home-automation-system/boiler-service/actions/workflows/CI.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)

![GitHub Release Date - Published_At](https://img.shields.io/github/release-date/smart-home-automation-system/boiler-service?style=plastic)
![GitHub Release](https://img.shields.io/github/v/release/smart-home-automation-system/boiler-service?style=plastic)

---

![GitHub top language](https://img.shields.io/github/languages/top/smart-home-automation-system/boiler-service?style=plastic)
![Java](https://img.shields.io/badge/java-21-yellow?style=plastic)
![SpringBoot](https://img.shields.io/badge/SpringBoot-4.1.0-blue?style=plastic)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_boiler-service&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_boiler-service)

![GitHub issues](https://img.shields.io/github/issues/smart-home-automation-system/boiler-service?style=plastic)
![GitHub contributors](https://img.shields.io/github/contributors/smart-home-automation-system/boiler-service?style=plastic)
![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/smart-home-automation-system/boiler-service?style=plastic)

![GitHub last commit](https://img.shields.io/github/last-commit/smart-home-automation-system/boiler-service?style=plastic)
![GitHub commit activity](https://img.shields.io/github/commit-activity/m/smart-home-automation-system/boiler-service?style=plastic)

---

# Description

Handles the devices in the boiler room: the furnace and the two circulation pumps (hot
water and heating). The service does not decide *whether* the house needs heat — it asks
`water-service` for the hot water status and `heating-service` for the rooms status, and
translates both answers into relay states on a Shelly Pro 4 device over HTTP.

The decision loop runs on a scheduler with a one-minute delay between passes (`StatusCron`,
10 s initial delay). It is a reactive `@Scheduled` method using `fixedDelay`, so a slow pass
postpones the next one instead of overlapping with it and racing on the shared device state.
Every pass queries both services, then drives the devices in a fixed order — hot water
pump, heating pump, furnace:

- the furnace runs when at least one pump is working and is switched off once both are off;
- the hot water pump has priority over the heating pump: while hot water is being heated the
  heating pump stays off, and enabling it is refused until hot water is done;
- the heating pump follows `heating-service` whenever the hot water pump is idle, but
  disabling it is always allowed.

Device state is kept in memory (`BoilerConfig`) and re-read from the Shelly device only when
the cached entry is older than a minute, so a stuck loop does not turn into a burst of device
calls. Every HTTP client has a 5 s response timeout, and if `heating-service` or
`water-service` cannot be reached, the client falls back to `active=false` — an unreachable
neighbour makes the boiler idle rather than blocked.

## Run locally

```bash
mvn verify
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

| | Application | Actuator |
|---|---|---|
| `local` profile | 6007 | 8007 |
| in the cluster (`home` profile) | 6200 | 8200 |

No database and no message broker — the only outbound traffic is HTTP: the Shelly Pro 4 in
the boiler room and the two sibling services. Their addresses come from the `shelly.actor`
and `internal.service.*` properties; the `local` profile points `heating-service` and
`water-service` at `localhost:6002` and `localhost:6006`.

## API

All paths are served under the `/home/boiler` base path (`spring.webflux.base-path`). The
service is currently reachable inside the cluster only — the Kubernetes ingress has no rule
for `/home/boiler`, and requests under `/home` land on `api-gateway-service`, which has no
static route to this service (it relied on the Eureka discovery locator, dropped together
with the Eureka client).

| Method | Path | Description |
|---|---|---|
| `GET` | `/home/boiler/status` | Current state of the furnace and of both pumps (`hot_water`, `heating`) — each with its working flag and the timestamped message describing the last change; a device the loop has not touched yet comes back as `{"working": false}`, without the message |

Actuator endpoints, including the `readiness` and `liveness` health groups used by the
Kubernetes probes, live on the management port, not on the application one.
