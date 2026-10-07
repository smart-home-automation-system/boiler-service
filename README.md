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
![SpringBoot](https://img.shields.io/badge/SpringBoot-4.1.1-blue?style=plastic)
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

The Shelly itself is watched too. When its calls have been failing for five minutes — no
connection, a timeout, an error status or an answer that is not one, in every control pass —
(`boiler.shelly-monitor.offline-after`) the service publishes an alert, repeats it every hour
(`reminder-interval`) for as long as that lasts, and publishes one info when the device is
proven to work again — see [Messaging](#messaging).

## Run locally

```bash
mvn verify
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

| | Application | Actuator |
|---|---|---|
| `local` profile | 6007 | 8007 |
| in the cluster (`home` profile) | 6200 | 8200 |

> **A local run drives the real device.** The `local` profile moves the ports and the two
> sibling services, not the Shelly: ten seconds after the start the control pass switches the
> relays in the boiler room. With the siblings not running locally both read as "not active",
> so the pass turns the pumps and the furnace off — against the instance in the cluster, which
> turns them back on within a minute. Override `shelly.actor.pro.boiler.host` unless that is
> what you want.

No database. Outbound traffic is HTTP to the Shelly Pro 4 in the boiler room and to the two
sibling services, plus RabbitMQ for the notifications. The addresses come from the
`shelly.actor`, `internal.service.*` and `spring.rabbitmq.*` properties; the `local` profile
points `heating-service`, `water-service` and the broker at `localhost` (6002, 6006, 5672).
The broker password has no default: set `rabbitmq-password` (any value will do when nothing
is to be published — the connection is opened by the first notification).

## API

All paths are served under the `/home/boiler` base path (`spring.webflux.base-path`). From
outside the cluster the service is reached through `api-gateway-service`, which routes
`/home/boiler/**` to it.

| Method | Path | Description |
|---|---|---|
| `GET` | `/home/boiler/status` | Current state of the furnace and of both pumps (`hot_water`, `heating`) — each with its working flag and the timestamped message describing the last change; a device the loop has not touched yet comes back as `{"working": false}`, without the message |

Actuator endpoints, including the `readiness` and `liveness` health groups used by the
Kubernetes probes, live on the management port, not on the application one.

# Messaging

Publishes to RabbitMQ, virtual host `/notification`, headers exchange `notification` — the
exchange, its queues and bindings are pre-declared by the RabbitMQ infrastructure, and
`notification-service` posts what arrives there on Discord. The service consumes nothing.

| When | Headers | Payload |
|---|---|---|
| calls to the Shelly have been failing for `offline-after` (5 min), in every pass | `category=alert`, `level=error` | plain text: since when its calls fail, and that the furnace and the pumps are not being controlled |
| they still fail, every `reminder-interval` (1 h) | `category=alert`, `level=warn` | plain text, the same with the time that has passed |
| a reported device gets through a pass in which what had failed works again — or it answers and nothing has failed for `offline-after` | `category=info`, `level=info` | plain text: from when to when its calls failed |

Every message also carries `env` (`prod`, `dev` in the `local` profile), the other header the
exchange routes by. A notification counts as sent only when the broker confirmed it **and**
did not return it as unroutable; otherwise it is logged at ERROR and tried again with the next
pass, a minute later. The state of the monitor lives in memory: after a restart in the middle
of an outage the alert is sent a second time.
